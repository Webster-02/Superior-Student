(function(){
  function decode(value) {
    try {
      const binary = atob(value || '');
      const encoded = Array.prototype.map.call(binary, function(ch) {
        return '%' + ch.charCodeAt(0).toString(16).padStart(2, '0');
      }).join('');
      return JSON.parse(decodeURIComponent(encoded));
    } catch (_) {
      return '';
    }
  }
  function clean(value) { return String(value || '').replace(/\s+/g, ' ').trim(); }
  function normalize(value) { return clean(value).toLowerCase().replace(/[^a-z0-9]+/g, ''); }
  function simplifyCourse(value) {
    return normalize(clean(value)
      .replace(/\b(?:FALL|SPRING|SUMMER|WINTER)\s+20\d{2}\b/ig, ' ')
      .replace(/\b20\d{2}\s*-\s*20\d{2}\s*-\s*[A-Z0-9-]+\b/ig, ' ')
      .replace(/\b[A-Z]{2,8}[- ]?\d{4,}[A-Z0-9-]*\b/ig, ' ')
      .replace(/\battendance\b/ig, ' '));
  }
  const targetCourse = decode('%COURSE_B64%');
  const targetCode = decode('%CODE_B64%');
  const targetCodeNorm = normalize(targetCode);
  const targetCourseNorm = simplifyCourse(targetCourse);

  function matchesTarget(node) {
    const text = clean(node && (node.innerText || node.textContent));
    if (!text || text.length > 1600) return false;
    const normalized = normalize(text);
    if (targetCodeNorm.length >= 5 && normalized.indexOf(targetCodeNorm) !== -1) return true;
    const simple = simplifyCourse(text);
    return targetCourseNorm.length >= 5 &&
      (simple.indexOf(targetCourseNorm) !== -1 || targetCourseNorm.indexOf(simple) !== -1);
  }

  function detailUrlFrom(value) {
    const raw = String(value || '');
    const match = raw.match(/(?:https?:\/\/[^'"\s)]+)?\/student\/attendancedetail(?:\/id)?\/[A-Za-z0-9_-]+(?:\?[^'"\s)]*)?/i);
    if (!match) return '';
    try {
      const url = new URL(match[0], location.href);
      if (url.hostname.toLowerCase() !== location.hostname.toLowerCase()) return '';
      if (!/\/student\/attendancedetail(?:\/|$)/i.test(url.pathname)) return '';
      return url.href;
    } catch (_) {
      return '';
    }
  }

  const selectors = 'tr,li,.o_data_row,.o_kanban_record,.list-group-item,.row,.card,[role="row"],button,[role="button"]';
  const rows = Array.from(document.querySelectorAll(selectors))
    .filter(matchesTarget)
    .sort(function(a,b) {
      const aLength = clean(a.innerText || a.textContent).length;
      const bLength = clean(b.innerText || b.textContent).length;
      return aLength - bLength;
    });

  for (const row of rows) {
    const nodes = [row].concat(Array.from(row.querySelectorAll('a[href],[data-href],[data-url],[onclick]')));
    for (const node of nodes) {
      const raw = [
        node.getAttribute && node.getAttribute('href'),
        node.getAttribute && node.getAttribute('data-href'),
        node.getAttribute && node.getAttribute('data-url'),
        node.getAttribute && node.getAttribute('onclick')
      ].filter(Boolean).join(' ');
      const url = detailUrlFrom(raw);
      if (url) return JSON.stringify({found:true, url:url, clicked:false});
    }
  }

  // If the ERP has no exposed URL, activate the existing event handler for
  // the smallest row matching this student's subject, rather than guessing an ID.
  for (const row of rows) {
    const action = row.matches('a,button,[role="button"],[onclick],[data-href],[data-url]') ? row :
      row.querySelector('a,button,[role="button"],[onclick],[data-href],[data-url]');
    if (action) {
      action.click();
      return JSON.stringify({found:true, url:'', clicked:true});
    }
    if (row.getAttribute('onclick')) {
      row.click();
      return JSON.stringify({found:true, url:'', clicked:true});
    }
  }

  return JSON.stringify({found:false, url:'', clicked:false});
})();