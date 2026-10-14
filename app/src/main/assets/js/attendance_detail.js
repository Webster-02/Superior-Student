(function(){
  function clean(value){return String(value||'').replace(/\s+/g,' ').trim();}
  function textOf(el){return el ? clean(el.innerText || el.textContent || '') : '';}
  function safe(value){return clean(value).replace(/\bERP\b/gi,'').replace(/\bOdoo\b/gi,'').replace(/\s+/g,' ').trim();}

  const path=(location.pathname||'').toLowerCase();
  const rootSelectors=['#page_content_inner','#page_content','.md-card-list-wrapper',
    '.md-card-list','.uk-switcher','.o_portal_wrap','main,[role="main"]',
    '.container-fluid','.container'];
  let root=null;
  for(const selector of rootSelectors){
    const candidate=document.querySelector(selector);
    if(candidate){root=candidate;break;}
  }
  const candidates=Array.from(document.querySelectorAll(rootSelectors.join(',')));
  for(const candidate of candidates){
    const content=textOf(candidate);
    if(/Course\s*:|Attendance Percentage|Number of classes Conducted/i.test(content)){
      root=candidate;
      break;
    }
  }
  if(!root) root=document.body;
  const clone=root.cloneNode(true);
  clone.querySelectorAll('header,nav,footer,aside,script,style,noscript').forEach(function(el){el.remove();});
  clone.querySelectorAll('form').forEach(function(el){el.remove();});

  const tables=[];
  let attendanceDetail = null;
  const isAttendanceDetail = /\/student\/attendancedetail(?:\/|$)/i.test(path);
  if (isAttendanceDetail) {
    const detailText = safe((root && (root.innerText || root.textContent)) ||
      (document.body && (document.body.innerText || document.body.textContent)) || '');

    function detailField(pattern) {
      const match = detailText.match(pattern);
      return match ? safe(match[1]) : '';
    }

    const detailCourse = detailField(/\bCourse\s*:\s*(.*?)(?=\s+Course Code\s*:|\s+Number of classes Conducted\s*:|\s+Number of classes Attended\s*:|\s+Attendance Percentage\s*:|$)/i);
    const detailCode = detailField(/\bCourse Code\s*:\s*(.*?)(?=\s+Number of classes Conducted\s*:|\s+Number of classes Attended\s*:|\s+Attendance Percentage\s*:|$)/i);
    const conductedMatch = detailText.match(/Number of classes Conducted\s*:\s*(\d+)/i);
    const attendedMatch = detailText.match(/Number of classes Attended\s*:\s*(\d+)/i);
    const percentageMatch = detailText.match(/Attendance Percentage\s*:\s*(\d{1,3}(?:\.\d+)?)\s*%?/i);

    const sessionTable = tables.find(function(table) {
      return table.headers.some(function(h) { return /date/i.test(h); }) &&
        table.headers.some(function(h) { return /status/i.test(h); });
    });
    let sessions = [];
    if (sessionTable) {
      const dateIndex = sessionTable.headers.findIndex(function(h) { return /date/i.test(h); });
      const statusIndex = sessionTable.headers.findIndex(function(h) { return /status/i.test(h); });
      sessions = sessionTable.rows.map(function(row) {
        return {
          date: clean(row[dateIndex] || ''),
          status: clean(row[statusIndex] || '').toUpperCase()
        };
      }).filter(function(row) { return row.date && row.status; });
    }

    // ERP builds may render class history with generic rows and no semantic THEAD.
    // Fall back to live DOM rows and identify date/status by the values themselves.
    if (!sessions.length) {
      const datePattern = /^(?:\d{4}-\d{2}-\d{2}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4})$/;
      const statusPattern = /^(?:P|A|Present|Absent|Late|Leave|Excused|Medical|Holiday)$/i;
      Array.from(clone.querySelectorAll('table tr,[role="row"],.o_data_row,.list-group-item')).forEach(function(row) {
        let cells = Array.from(row.children || []).filter(function(cell) {
          const tag = (cell.tagName || '').toUpperCase();
          const role = (cell.getAttribute && cell.getAttribute('role')) || '';
          return tag === 'TD' || tag === 'TH' || role === 'cell' || role === 'gridcell';
        }).map(function(cell) { return clean(textOf(cell)); });

        if (cells.length < 2) {
          cells = Array.from(row.querySelectorAll('td,th,[role="cell"],[role="gridcell"]'))
            .map(function(cell) { return clean(textOf(cell)); });
        }
        if (cells.length < 2 || cells.some(function(v) { return /^(date|status)$/i.test(v); })) return;

        const date = cells.find(function(value) { return datePattern.test(value); }) || '';
        const status = cells.find(function(value) { return statusPattern.test(value); }) || '';
        if (date && status && !sessions.some(function(item) { return item.date === date && item.status === status.toUpperCase(); })) {
          sessions.push({date: date, status: status.toUpperCase()});
        }
      });
    }

    const conducted = conductedMatch ? parseInt(conductedMatch[1], 10) : null;
    const attended = attendedMatch ? parseInt(attendedMatch[1], 10) : null;
    const percentage = percentageMatch ? parseFloat(percentageMatch[1]) : null;
    if (detailCourse || conducted !== null || attended !== null || percentage !== null) {
      attendanceDetail = {
        course: detailCourse,
        code: detailCode,
        conducted: conducted,
        attended: attended,
        percentage: percentage,
        sessions: sessions.slice(0, 150)
      };
    }
  }

  
  return JSON.stringify({
    cards:[],
    tables:[],
    lines:[],
    records:[],
    overallAttendance:null,
    attendanceDetail:attendanceDetail,
    attendanceDetailLinks:[],
    invoices:[],
    academicResults:[]
  });
})();