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
  function courseCodeTokens(value) {
    const text = String(value || '');
    const regex = /\b([A-Z]{2,8})[- ]?(\d{3,})\b/gi;
    const tokens = [];
    let match;
    while ((match = regex.exec(text)) !== null) tokens.push((match[1] + match[2]).toUpperCase());
    return Array.from(new Set(tokens));
  }
  function simplifyCourse(value) {
    return normalize(clean(value)
      .replace(/\b(?:FALL|SPRING|SUMMER|WINTER)\s+20\d{2}\b/ig, ' ')
      .replace(/\b20\d{2}\s*-\s*20\d{2}\s*-\s*[A-Z0-9-]+\b/ig, ' ')
      .replace(/\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b/ig, ' ')
      .replace(/\battendance\b/ig, ' '));
  }
  const targetCourse = decode('%COURSE_B64%');
  const targetCode = decode('%CODE_B64%');
  const targetCodeTokens = courseCodeTokens(targetCode);
  const targetCourseNorm = simplifyCourse(targetCourse);
  const targetCodeCanonical = targetCodeTokens[0] || '';
  const hasTargetCode = targetCodeCanonical.length > 0;

  function matchesTarget(node) {
    const text = clean(node && (node.innerText || node.textContent));
    if (!text || text.length > 1400) return false;
    if (hasTargetCode) return courseCodeTokens(text).includes(targetCodeCanonical);
    const normalizedText = simplifyCourse(text);
    return targetCourseNorm.length >= 8 && normalizedText.length >= 8 &&
      normalizedText.includes(targetCourseNorm);
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

  const selectors = 'tr,li,.o_data_row,.o_kanban_record,.list-group-item,[role="row"],.row,.card,button,[role="button"]';
  const rows = Array.from(document.querySelectorAll(selectors))
    .filter(matchesTarget)
    .sort(function(a,b) {
      const aText = clean(a.innerText || a.textContent);
      const bText = clean(b.innerText || b.textContent);
      const aCodes = courseCodeTokens(aText).length;
      const bCodes = courseCodeTokens(bText).length;
      return (aCodes - bCodes) || (aText.length - bText.length);
    });

  for (const row of rows) {
    const raw = [
      row.getAttribute && row.getAttribute('href'),
      row.getAttribute && row.getAttribute('data-href'),
      row.getAttribute && row.getAttribute('data-url'),
      row.getAttribute && row.getAttribute('onclick'),
      ...Array.from(row.querySelectorAll('a[href],[data-href],[data-url],[onclick]')).map(function(node) {
        return [
          node.getAttribute('href') || '',
          node.getAttribute('data-href') || '',
          node.getAttribute('data-url') || '',
          node.getAttribute('onclick') || ''
        ].filter(Boolean).join(' ');
      })
    ].filter(Boolean).join(' ');
    const url = detailUrlFrom(raw);
    if (url) return JSON.stringify({found:true, url:url, clicked:false});
  }

  // If the matched ERP row has no explicit URL, click that exact row so
  // delegated ERP handlers can open its own record. Never click a child action
  // from a fuzzy ancestor, which can open another subject's detail by mistake.
  if (rows.length > 0) {
    rows[0].click();
    return JSON.stringify({found:true, url:'', clicked:true});
  }

  return JSON.stringify({found:false, url:'', clicked:false});
})();