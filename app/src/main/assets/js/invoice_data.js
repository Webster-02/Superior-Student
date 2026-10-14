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
  const pageRootSelectors=['#page_content_inner','#page_content','.md-card-content','.md-card',
    'main,[role="main"]','.container-fluid','.container'];
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

  const invoices = [];
  if (/\/student\/invoices(?:\/|$)/i.test(path)) {
    Array.from(clone.querySelectorAll('table')).forEach(function(table) {
      const headerRow = table.querySelector('thead tr') || table.querySelector('tr');
      if (!headerRow) return;
      const headers = Array.from(headerRow.children || [])
        .filter(function(cell) {
          const tag = String(cell.tagName || '').toUpperCase();
          return tag === 'TH' || tag === 'TD';
        })
        .map(function(cell) { return safe(textOf(cell)).toLowerCase(); });
      const normalized = headers.map(function(value) { return value.replace(/[^a-z0-9]+/g, ''); });
      const column = function(patterns) {
        return normalized.findIndex(function(name) {
          return patterns.some(function(pattern) { return pattern.test(name); });
        });
      };
      const numberIndex = column([/^invoiceno$/, /^invoicenumber$/]);
      const statusIndex = column([/^status$/, /^state$/]);
      const amountIndex = column([/^totalamount$/, /^amount$/]);
      const payableIndex = column([/^payable$/, /^amountpayable$/, /^remainingamount$/]);
      if (numberIndex < 0 || statusIndex < 0 || amountIndex < 0) return;

      const dateIndex = column([/^invoicedate$/, /^date$/]);
      const dueIndex = column([/^duedate$/, /^paymentduedate$/]);
      const termIndex = column([/^term$/, /^session$/]);
      const semesterIndex = column([/^semester$/, /^semesterno$/, /^semestername$/]);
      const receiptIndex = column([/^receiptfor$/, /^description$/, /^particulars$/]);
      const barcodeIndex = column([/^barcode$/, /^referenceno$/, /^reference$/]);
      const bodyRows = Array.from(table.querySelectorAll('tbody tr'));
      const rows = bodyRows.length ? bodyRows :
        Array.from(table.querySelectorAll('tr')).filter(function(row) { return row !== headerRow; });

      rows.forEach(function(row) {
        const cells = Array.from(row.children || []).filter(function(cell) {
          return String(cell.tagName || '').toUpperCase() === 'TD';
        });
        if (!cells.length) return;
        const cellText = function(index) {
          return index >= 0 && index < cells.length ? safe(textOf(cells[index])) : '';
        };
        const number = cellText(numberIndex);
        const status = cellText(statusIndex).toLowerCase();
        if (!number || !/^(paid|unpaid|partial|partially paid|pending)$/i.test(status)) return;

        let downloadUrl = '';
        const anchor = row.querySelector('a[href*="/student/invoices/download/"]');
        if (anchor) {
          try {
            const parsed = new URL(anchor.getAttribute('href') || '', location.href);
            if (parsed.hostname.toLowerCase() === location.hostname.toLowerCase() &&
                /^\/student\/invoices\/download\/[0-9]+\/?$/i.test(parsed.pathname)) {
              downloadUrl = parsed.href;
            }
          } catch (_) {}
        }

        invoices.push({
          number: number,
          invoiceDate: cellText(dateIndex),
          dueDate: cellText(dueIndex),
          term: cellText(termIndex),
          semester: cellText(semesterIndex),
          receiptFor: cellText(receiptIndex),
          barcode: cellText(barcodeIndex),
          totalAmount: cellText(amountIndex),
          payable: cellText(payableIndex),
          status: status,
          downloadUrl: downloadUrl
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
    invoices:unique(invoices).slice(0,150),
    academicResults:[]
  });
})();