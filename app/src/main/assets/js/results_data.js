(function(){
  function clean(value){return String(value||'').replace(/\s+/g,' ').trim();}
  function textOf(el){return el ? clean(el.innerText || el.textContent || '') : '';}
  function safe(value){return clean(value).replace(/\bERP\b/gi,'').replace(/\bOdoo\b/gi,'').replace(/\s+/g,' ').trim();}
  function unique(list){
    const seen=new Set();
    return list.filter(function(item){
      const key=JSON.stringify(item);
      if(seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  const path=(location.pathname||'').toLowerCase();
  const pageRootSelectors=['.o_list_view','.o_kanban_view','.o_form_view','.o_action_manager',
    '.o_portal_wrap','main,[role="main"]','.container-fluid','.container'];
  let root=null;
  for(const selector of pageRootSelectors){
    const candidate=document.querySelector(selector);
    if(candidate){root=candidate;break;}
  }
  const candidates=Array.from(document.querySelectorAll(pageRootSelectors.join(',')));
  for(const candidate of candidates){
    if(candidate.querySelector('table')){root=candidate;break;}
  }
  if(!root) root=document.body;
  const clone=root.cloneNode(true);
  clone.querySelectorAll('header,nav,footer,aside,script,style,noscript').forEach(function(el){el.remove();});
  clone.querySelectorAll('form').forEach(function(el){el.remove();});

  const academicResults = [];
  if (/\/student\/results(?:\/|$)/i.test(path)) {
    clone.querySelectorAll('table').forEach(function(table) {
      const rows = Array.from(table.querySelectorAll('tbody tr'));
      if (!rows.some(function(row) { return row.classList.contains('table-parent-row'); })) return;

      const summaryHeaders = Array.from(table.querySelectorAll('thead tr'))
        .slice(-1)
        .flatMap(function(row) {
          return Array.from(row.children || [])
            .filter(function(cell) { return /^(TH|TD)$/i.test(cell.tagName || ''); })
            .map(function(cell) { return safe(textOf(cell)); });
        });
      function normHeader(value) {
        return safe(value).toLowerCase().replace(/[^a-z0-9]+/g, '');
      }
      function headerIndex(headers, patterns) {
        return headers.findIndex(function(header) {
          const normalized = normHeader(header);
          return patterns.some(function(pattern) { return pattern.test(normalized); });
        });
      }
      function rowTextValues(row) {
        return Array.from(row.children || [])
          .filter(function(cell) { return /^(TH|TD)$/i.test(cell.tagName || ''); })
          .map(function(cell) { return safe(textOf(cell)); });
      }

      let current = null;
      let courseHeaders = [];
      rows.forEach(function(row) {
        const values = rowTextValues(row);
        if (row.classList.contains('table-parent-row')) {
          if (!values.length) { current = null; return; }
          const termIndex = headerIndex(summaryHeaders, [/^term$/, /^semester$/, /^session$/]);
          const gradePointsIndex = headerIndex(summaryHeaders, [/^gradepoints$/, /^gradepts$/]);
          const totalCreditsIndex = headerIndex(summaryHeaders, [/^totalcredits$/, /^credits$/]);
          const earnedCreditsIndex = headerIndex(summaryHeaders, [/^earnedcredits$/]);
          const gpaIndex = headerIndex(summaryHeaders, [/^gpa$/]);
          const cgpaIndex = headerIndex(summaryHeaders, [/^cgpa$/]);
          function summaryValue(index, fallback) {
            const at = index >= 0 ? index : fallback;
            return at >= 0 && at < values.length ? values[at] : '';
          }

          const termName = summaryValue(termIndex, 0);
          if (!termName) { current = null; return; }
          current = {
            term: termName,
            gradePoints: summaryValue(gradePointsIndex, 1),
            totalCredits: summaryValue(totalCreditsIndex, 2),
            earnedCredits: summaryValue(earnedCreditsIndex, 3),
            gpa: summaryValue(gpaIndex, 4),
            cgpa: summaryValue(cgpaIndex, 5),
            courses: []
          };
          academicResults.push(current);
          courseHeaders = [];
          return;
        }

        if (!current || !row.classList.contains('table-child-row')) return;
        const thCells = Array.from(row.children || []).filter(function(cell) {
          return /^(TH)$/i.test(cell.tagName || '');
        });
        const tdCells = Array.from(row.children || []).filter(function(cell) {
          return /^(TD)$/i.test(cell.tagName || '');
        });
        if (thCells.length && !tdCells.length) {
          courseHeaders = thCells.map(function(cell) { return safe(textOf(cell)); });
          return;
        }
        if (!tdCells.length) return;

        const headers = courseHeaders.length
          ? courseHeaders
          : ['Course', 'Credits', 'Marks Obtained', 'Grade Pts', 'Mid Grade', 'Final Grade'];
        function courseValue(patterns, fallback) {
          const at = headerIndex(headers, patterns);
          const index = at >= 0 ? at : fallback;
          return index >= 0 && index < values.length ? values[index] : '';
        }
        const name = courseValue([/^course$/, /^subject$/, /^coursename$/, /^subjectname$/], 0);
        if (!name || /^(course|subject)$/i.test(name)) return;

        current.courses.push({
          course: name,
          credits: courseValue([/^credits?$/, /^credithours$/], 1),
          marksObtained: courseValue([/^marksobtained$/, /^marks$/, /^score$/], 2),
          gradePoints: courseValue([/^gradepoints$/, /^gradepts$/, /^gradepoint$/], 3),
          midGrade: courseValue([/^midgrade$/], 4),
          finalGrade: courseValue([/^finalgrade$/, /^grade$/], 5)
        });
      });
    });
  }

  
  return JSON.stringify({
    cards:[],
    tables:[],
    lines:[],
    records:[],
    overallAttendance:null,
    attendanceDetail:null,
    attendanceDetailLinks:[],
    invoices:[],
    academicResults:unique(academicResults).slice(0,30)
  });
})();