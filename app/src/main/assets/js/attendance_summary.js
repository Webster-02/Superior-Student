(function () {
  function clean(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
  }

  function textOf(element) {
    return element ? clean(element.innerText || element.textContent || '') : '';
  }

  function percentFromCard(card) {
    const label = card.querySelector('.md-card-content .uk-text-small');
    const labelText = textOf(label);
    const matches = Array.from(labelText.matchAll(/(\d{1,3}(?:\.\d+)?)\s*%/g));
    if (matches.length === 1) {
      const value = Number(matches[0][1]);
      if (Number.isFinite(value) && value >= 0 && value <= 100) return value;
    }

    // Some portal variants expose the same official percent only on the
    // progress-bar width. This is card-local, never read from a shared row.
    if (matches.length === 0) {
      const bar = card.querySelector('.md-card-content .uk-progress-bar');
      const style = bar ? (bar.getAttribute('style') || '') : '';
      const widthMatches = Array.from(style.matchAll(/width\s*:\s*(\d{1,3}(?:\.\d+)?)\s*%/gi));
      if (widthMatches.length === 1) {
        const value = Number(widthMatches[0][1]);
        if (Number.isFinite(value) && value >= 0 && value <= 100) return value;
      }
    }
    return null;
  }

  const pageRoot = document.querySelector('#hierarchical-show') ||
    document.querySelector('.hierarchical_show') ||
    document.querySelector('#page_content_inner') ||
    document.body;

  // Superior ERP renders each subject inside its own .md-card.md-card-hover.
  // Read title, course code, href and percentage from that same card so
  // a neighbouring subject can never donate its percentage.
  let subjectCards = Array.from(pageRoot.querySelectorAll('.md-card.md-card-hover'))
    .filter(function(card) {
      return !!card.querySelector('a[href*="/student/attendancedetail"]');
    });

  if (!subjectCards.length) {
    subjectCards = Array.from(pageRoot.querySelectorAll('a[href*="/student/attendancedetail"]'))
      .map(function(anchor) { return anchor.closest('.md-card') || anchor.parentElement; })
      .filter(Boolean);
  }

  const seen = new Set();
  const attendanceDetailLinks = [];

  subjectCards.forEach(function(card) {
    const anchor = card.querySelector('a[href*="/student/attendancedetail"]');
    if (!anchor) return;

    let url;
    try {
      url = new URL(anchor.getAttribute('href') || '', location.href);
    } catch (_) {
      return;
    }
    if (url.hostname.toLowerCase() !== location.hostname.toLowerCase()) return;
    if (!/^\/student\/attendancedetail(?:\/id)?\/[^/]+\/?$/i.test(url.pathname)) return;
    if (seen.has(url.href)) return;
    seen.add(url.href);

    const course = textOf(card.querySelector('.md-list-heading'));
    const code = textOf(card.querySelector('.sub-heading'));
    if (!course && !code) return;

    attendanceDetailLinks.push({
      course: course,
      code: code,
      url: url.href,
      percentage: percentFromCard(card)
    });
  });

  return JSON.stringify({
    cards: [],
    tables: [],
    lines: [],
    records: [],
    overallAttendance: null,
    attendanceDetail: null,
    attendanceDetailLinks: attendanceDetailLinks,
    invoices: [],
    academicResults: []
  });
})();