(function () {
  function clean(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
  }
  function textOf(element) {
    return element ? clean(element.innerText || element.textContent || '') : '';
  }
  function unique(items) {
    const seen = new Set();
    return items.filter(function (item) {
      const key = String(item.course || '').toLowerCase() + '|' + String(item.code || '').toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  const path = String(location.pathname || '').toLowerCase();
  const enrolledPath = /^\/student\/(?:enrolled|enroll)\/courses(?:\/|$)/i.test(path);
  const enrolledCourses = [];

  if (enrolledPath) {
    const root = document.querySelector('#hierarchical-show') ||
      document.querySelector('.hierarchical_show') ||
      document.querySelector('#page_content_inner') ||
      document.body;

    const cards = Array.from(root.querySelectorAll('.md-card.md-card-hover'));
    cards.forEach(function (card) {
      const anchor = card.querySelector('a[title="Open class"], a[href*="/student/results/id/"]');
      if (!anchor) return;

      const course = textOf(anchor.querySelector('.md-list-heading') ||
        card.querySelector('.md-list-heading'));
      const subheadings = Array.from(card.querySelectorAll('.sub-heading'));
      const code = subheadings.map(textOf).find(function (value) {
        return /\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b/i.test(value) &&
          !/credits|course\s*type/i.test(value);
      }) || '';
      if (!course || !code) return;

      const creditLine = subheadings.map(textOf).find(function (value) {
        return /\bCredits\s*:/i.test(value);
      }) || '';
      const courseTypeLine = subheadings.map(textOf).find(function (value) {
        return /\bCourse\s*Type\s*:/i.test(value);
      }) || '';
      const creditMatch = creditLine.match(/\bCredits\s*:\s*([0-9]+(?:\.[0-9]+)?)/i);
      const courseTypeMatch = courseTypeLine.match(/\bCourse\s*Type\s*:\s*(.+)$/i);

      const term = Array.from(card.querySelectorAll('.uk-badge')).map(textOf).find(function (value) {
        return /^(?:SPRING|SUMMER|FALL|WINTER)\s+\d{4}$/i.test(value);
      }) || '';
      const department = textOf(card.querySelector('.uk-badge-danger'));
      const facultyHead = card.querySelector('.md-card-head[title]');
      const faculty = facultyHead ? clean(facultyHead.getAttribute('title') || '') : '';

      let detailUrl = '';
      try {
        const parsed = new URL(anchor.getAttribute('href') || '', location.href);
        if (parsed.hostname.toLowerCase() === location.hostname.toLowerCase() &&
            /^\/student\/results\/id\/[0-9]+\/?$/i.test(parsed.pathname)) {
          detailUrl = parsed.href;
        }
      } catch (_) { /* Keep the course visible even if its detail link is unavailable. */ }

      enrolledCourses.push({
        course: course,
        code: code,
        credits: creditMatch ? creditMatch[1] : '',
        courseType: courseTypeMatch ? clean(courseTypeMatch[1]) : '',
        term: term,
        faculty: faculty,
        department: department,
        detailUrl: detailUrl
      });
    });
  }

  return JSON.stringify({
    cards: [],
    tables: [],
    lines: [],
    records: [],
    overallAttendance: null,
    attendanceDetail: null,
    attendanceDetailLinks: [],
    invoices: [],
    academicResults: [],
    enrolledCourses: unique(enrolledCourses).slice(0, 120)
  });
})();