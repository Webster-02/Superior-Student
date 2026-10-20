(function () {
  var batchId = JSON.parse(atob("%BATCH_ID_B64%"));
  var jobs = JSON.parse(atob("%JOBS_B64%"));
  window.__pachattarE1Batches = window.__pachattarE1Batches || {};
  var batches = window.__pachattarE1Batches;
  var batch = {
    id: batchId,
    startedAt: Date.now(),
    pending: jobs.length,
    results: {},
    frames: [],
    cancelled: false
  };
  batches[batchId] = batch;

  function finish(job, frame, startedAt, status, payload, fallbackPayload, detail) {
    if (batch.cancelled || batch.results[job.key]) return;
    batch.results[job.key] = {
      status: status,
      payload: payload || "",
      fallbackPayload: fallbackPayload || "",
      detail: detail || "",
      durationMs: Math.max(0, Date.now() - startedAt)
    };
    batch.pending = Math.max(0, batch.pending - 1);
    if (frame) {
      try { frame.remove(); } catch (_) {}
      var index = batch.frames.indexOf(frame);
      if (index >= 0) batch.frames.splice(index, 1);
    }
  }

  jobs.forEach(function (job) {
    var startedAt = Date.now();
    var frame = document.createElement("iframe");
    var ended = false;
    var timeoutId;
    function finishOnce(status, payload, fallbackPayload, detail) {
      if (ended) return;
      ended = true;
      clearTimeout(timeoutId);
      finish(job, frame, startedAt, status, payload, fallbackPayload, detail);
    }

    frame.setAttribute("aria-hidden", "true");
    frame.setAttribute("tabindex", "-1");
    frame.style.cssText = "position:absolute;width:1px;height:1px;left:-10000px;top:-10000px;border:0;opacity:0;pointer-events:none";
    frame.onload = function () {
      if (ended || batch.cancelled) return;
      try {
        var childUrl = String(frame.contentWindow.location.href || "").toLowerCase();
        var childPath = String(frame.contentWindow.location.pathname || "").toLowerCase();
        if (childPath.indexOf("/web/login") !== -1 || childPath === "/login" || childPath.indexOf("/login/") !== -1) {
          finishOnce("auth", "", "", "ERP redirected the authenticated request to login");
          return;
        }
        var payload = "";
        var fallbackPayload = "";
        var primaryError = "";
        try {
          var primaryValue = frame.contentWindow.eval(job.script);
          payload = typeof primaryValue === "string" ? primaryValue : JSON.stringify(primaryValue || "");
        } catch (error) {
          primaryError = String(error && error.message ? error.message : error);
        }
        if (job.fallback) {
          try {
            var fallbackValue = frame.contentWindow.eval(job.fallback);
            fallbackPayload = typeof fallbackValue === "string"
              ? fallbackValue
              : JSON.stringify(fallbackValue || "");
          } catch (fallbackError) {
            if (!primaryError) primaryError = String(fallbackError && fallbackError.message ? fallbackError.message : fallbackError);
          }
        }
        if (!payload && !fallbackPayload) {
          finishOnce("error", "", "", primaryError || ("Empty extraction from " + childUrl));
          return;
        }
        finishOnce("done", payload, fallbackPayload, primaryError);
      } catch (error) {
        finishOnce("error", "", "", String(error && error.message ? error.message : error));
      }
    };
    frame.onerror = function () {
      finishOnce("error", "", "", "Frame load failed");
    };
    timeoutId = setTimeout(function () {
      finishOnce("timeout", "", "", "ERP fetch timed out");
    }, job.timeoutMs || 22000);
    batch.frames.push(frame);
    frame.src = job.url;
    document.body.appendChild(frame);
  });

  return "STARTED";
})();
