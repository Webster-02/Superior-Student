(function(){
  function clean(value){return (value||'').replace(/\s+/g,' ').trim();}
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
  const pageRootSelectors = path.indexOf('/student/class/schedule') !== -1
    ? ['.fc','.o_calendar_view','.o_calendar_renderer','main,[role="main"]','.o_portal_wrap','.container-fluid','.container']
    : path.indexOf('/student/profile') !== -1
      ? ['.o_form_view','.o_form_sheet_bg','.o_portal_wrap','.o_portal_my_home','main,[role="main"]','.oe_structure','.container-fluid','.container']
      : path.indexOf('/student/results') !== -1
        ? ['.o_list_view','.o_kanban_view','.o_form_view','.o_action_manager','.o_portal_wrap','main,[role="main"]','.container-fluid','.container']
        : ['.o_portal_wrap','main,[role="main"]','.oe_structure','.container-fluid','.container'];
  let root=null;
  for(const selector of pageRootSelectors){
    const candidate=document.querySelector(selector);
    if(candidate){ root=candidate; break; }
  }
  const candidates=Array.from(document.querySelectorAll(pageRootSelectors.join(',')));
  for(const candidate of candidates){
    if(candidate.querySelector('table')){root=candidate;break;}
  }
  if(!root) root=document.body;

  const clone=root.cloneNode(true);
  clone.querySelectorAll('header,nav,footer,aside,script,style,noscript,form').forEach(function(el){el.remove();});

  const tables=[];
  clone.querySelectorAll('table').forEach(function(table){
    const rows=Array.from(table.querySelectorAll('tr')).map(function(tr){
      return Array.from(tr.querySelectorAll('th,td')).map(function(cell){return safe(textOf(cell));}).filter(function(v){return v!=='';});
    }).filter(function(row){return row.length>0;});
    if(!rows.length) return;

    let headers=[];
    const headerCells=table.querySelectorAll('thead th');
    if(headerCells.length){
      headers=Array.from(headerCells).map(function(cell){return safe(textOf(cell));});
    }else if(table.querySelector('tr th')){
      headers=Array.from(table.querySelectorAll('tr:first-child th')).map(function(cell){return safe(textOf(cell));});
    }

    let dataRows=rows;
    if(headers.length && dataRows.length && dataRows[0].join('|')===headers.join('|')) dataRows=dataRows.slice(1);

    const caption=table.querySelector('caption');
    tables.push({
      title:safe(caption ? textOf(caption) : ''),
      headers:headers,
      rows:dataRows.slice(0,100)
    });
  });

  const cards=[];
  clone.querySelectorAll('.stat-card,.summary-card,.info-box').forEach(function(card){
    if(card.querySelector('table')) return;
    const cardLines=(card.innerText||card.textContent||'').split(/\n+/).map(clean).filter(Boolean);
    if(cardLines.length>=2) cards.push({label:safe(cardLines[0]),value:safe(cardLines.slice(1).join(' '))});
  });

  const lines=[];
  clone.querySelectorAll('h1,h2,h3,h4,p,li,.alert').forEach(function(el){
    if(el.closest('table')) return;
    const value=safe(textOf(el));
    if(value && value.length<=180) lines.push(value);
  });

  clone.querySelectorAll('[class*="calendar"],[class*="schedule"],[class*="event"],[class*="course"],[class*="attendance"],[id*="calendar"],[id*="schedule"],[id*="event"],[id*="course"],[id*="attendance"]').forEach(function(el){
    if(el.closest('table')) return;
    const value=safe(textOf(el));
    if(value && value.length<=250) lines.push(value);
  });

  const records=[];
  tables.forEach(function(table){table.rows.forEach(function(row){records.push({headers:table.headers,values:row});});});
  const selectors=['[class*="attendance"] [class*="row"]','[class*="attendance"] [class*="item"]','[class*="course"]','[class*="event"]','[class*="schedule"] [class*="item"]','[class*="calendar"] [class*="event"]'];
  selectors.forEach(function(selector){
    clone.querySelectorAll(selector).forEach(function(el){
      const value=safe(textOf(el));
      const label=safe(el.getAttribute('aria-label')||el.getAttribute('title')||'');
      const combined=safe([label,value].filter(Boolean).join(' | '));
      if(combined&&combined.length<=350)records.push({headers:[],values:[combined]});
    });
  });

  clone.querySelectorAll('[class*="progress"],[class*="percentage"],[class*="percent"],[aria-valuenow]').forEach(function(el){
    const value=safe([
      el.getAttribute('aria-label')||'',
      el.getAttribute('title')||'',
      el.getAttribute('aria-valuenow') ? el.getAttribute('aria-valuenow')+'%' : '',
      textOf(el)
    ].filter(Boolean).join(' '));
    if(value&&/%/.test(value))records.push({headers:[],values:[value]});
  });

  const percentPattern=/([0-9]{1,3}(?:\.[0-9]+)?)\s*%/;
  const codePattern=/\b[A-Z]{2,6}\d{5,}[A-Z0-9-]*\b/i;
  const subjectPattern=/functional english|quantitative reasoning|civics and community engagement|management of refractive errors|visual optics and image processing|redefining success/i;

  // Profile pages often use label/value rows instead of tables. Capture
  // those pairs explicitly so the native presentation does not lose fields.
  if(/\/student\/profile/i.test(location.pathname)){
    const profileSelectors=[
      'dt','dd','label','.o_form_label','.o_field_widget','.form-group','.form-row','.profile-field','.profile-item',
      '.info-row','.info-item','.student-details .row','.o_form_sheet_bg .o_group',
      '[class*="profile"] [class*="row"]',
      '[class*="profile"] [class*="item"]',
      '[class*="profile"] [class*="field"]',
      '[class*="student"] [class*="row"]'
    ];
    profileSelectors.forEach(function(selector){
      clone.querySelectorAll(selector).forEach(function(el){
        if(el.closest('table')) return;
        const parts=Array.from(el.children || []).map(function(child){return safe(textOf(child));}).filter(Boolean);
        const raw=safe(textOf(el));
        if(parts.length>=2 && parts.length<=6){
          records.push({
            headers:['Field','Value'],
            values:[parts[0],parts.slice(1).join(' • ')]
          });
        }else if(raw && raw.length<=240){
          const split=raw.split(/\n+/).map(clean).filter(Boolean);
          if(split.length>=2 && split.length<=6){
            records.push({
              headers:['Field','Value'],
              values:[split[0],split.slice(1).join(' • ')]
            });
          }
        }
      });
    });
    // Odoo form views expose labels and field widgets in several nested patterns.
    // Capture label -> rendered value pairs from the same row/column so the app
    // can present the real ERP fields without depending on one CSS class.
    clone.querySelectorAll('label[for]').forEach(function(label){
      const fieldId=label.getAttribute('for');
      if(!fieldId) return;
      const field=Array.from(clone.querySelectorAll('[id]')).find(function(node){return node.id===fieldId;});
      const value=safe(field ? (field.value || field.getAttribute('value') || textOf(field)) : '');
      const labelText=safe(textOf(label));
      if(labelText && value && value.length<=180){
        records.push({headers:['Field','Value'],values:[labelText,value]});
      }
    });

    clone.querySelectorAll('input[name],textarea[name],select[name]').forEach(function(field){
      if(field.type==='hidden' || field.type==='password' || field.type==='search') return;
      const value=safe(field.value || field.getAttribute('value') || textOf(field));
      if(!value) return;
      let label='';
      const id=field.getAttribute('id');
      if(id){
        const labelNode=clone.querySelector('label[for="'+id.replace(/"/g,'')+'"]');
        label=safe(textOf(labelNode));
      }
      if(!label){
        const parent=field.closest('.o_field_widget,.form-group,.form-row,.row,.o_form_field,.o_form_label');
        const labelNode=parent ? parent.querySelector('label,.o_form_label,.form-label') : null;
        label=safe(textOf(labelNode));
      }
      if(!label) label=safe(field.getAttribute('placeholder')||field.getAttribute('aria-label')||field.getAttribute('name')||'');
      if(label && value.length<=180 && !/password|search|login/i.test(label)){
        records.push({headers:['Field','Value'],values:[label,value]});
      }
    });

    clone.querySelectorAll('.o_form_sheet,.o_form_sheet_bg,.o_form_view,.o_form_nosheet,.o_group').forEach(function(container){
      const labels=Array.from(container.querySelectorAll('.o_form_label,label'));
      labels.forEach(function(labelNode){
        const label=safe(textOf(labelNode));
        if(!label || label.length>100) return;
        const parent=labelNode.parentElement;
        const valueNode=parent ? parent.querySelector('.o_field_widget,.o_field_char,.o_field_text,.o_field_integer,.o_field_float,.o_field_monetary,.o_field_many2one') : null;
        const value=safe(textOf(valueNode));
        if(value && value.length<=180 && label !== value){
          records.push({headers:['Field','Value'],values:[label,value]});
        }
      });
    });
  }

  // Results can be rendered as cards/list rows depending on the ERP
  // version. Capture semantic result/grade rows in addition to tables.
  if(/\/student\/results/i.test(location.pathname)){
    const resultSelectors=[
      '[class*="result"] [class*="row"]',
      '[class*="result"] [class*="item"]',
      '[class*="result"] [class*="card"]',
      '[class*="grade"] [class*="row"]',
      '[class*="grade"] [class*="item"]',
      '[class*="marks"] [class*="row"]',
      '[class*="marks"] [class*="item"]',
      '.o_list_view tbody tr',
      '.o_portal_my_doc_table tbody tr',
      '[class*="course"] [class*="row"]'
    ];
    resultSelectors.forEach(function(selector){
      clone.querySelectorAll(selector).forEach(function(el){
        if(el.closest('table')) return;
        const value=safe(textOf(el));
        if(value && value.length>=3 && value.length<=350){
          records.push({headers:['Result'],values:[value]});
        }
      });
    });
    // Capture semester/term selectors and common result grids.
    clone.querySelectorAll('select').forEach(function(select){
      const options=Array.from(select.options||[]);
      if(options.length<2) return;
      const labels=options.map(function(o){return safe(o.textContent||o.innerText||'');}).filter(Boolean);
      if(labels.some(function(v){return /semester|term|fall|spring|summer|202[0-9]/i.test(v);})){
        records.push({headers:['Semester options'],values:labels});
      }
    });

    clone.querySelectorAll('[class*="result"] [class*="course"],[class*="result"] [class*="subject"],[class*="grade"] [class*="course"],[class*="marks"] [class*="course"]').forEach(function(el){
      const value=safe(textOf(el));
      if(value && value.length>=3 && value.length<=260){
        records.push({headers:['Result'],values:[value]});
      }
    });
  }


  let overallAttendance=null;
  const pageText=safe(clone.innerText||clone.textContent||'');
  const overallMatches=[
    /(?:overall|total)\s+attendance(?:\s+percentage)?\s*[:\-]?\s*([0-9]{1,3}(?:\.[0-9]+)?)\s*%/i,
    /(?:attendance\s+percentage|overall\s+percentage)\s*[:\-]?\s*([0-9]{1,3}(?:\.[0-9]+)?)\s*%/i
  ];
  for(const pattern of overallMatches){
    const m=pageText.match(pattern);
    if(m){overallAttendance=parseFloat(m[1]);break;}
  }

  const structuredRecords=[];
  const structuredCandidates=Array.from(clone.querySelectorAll('div,li,td,tr,section,article,.card,.row,.item'));
  structuredCandidates.forEach(function(el){
    const raw=safe(textOf(el));
    if(!raw || raw.length>320) return;
    const percent=raw.match(percentPattern);
    if(!percent) return;
    if(!codePattern.test(raw) && !subjectPattern.test(raw)) return;

    let subject=raw.replace(percent[0],'').trim();
    const codeMatch=subject.match(codePattern);
    const code=codeMatch ? codeMatch[0] : '';
    if(code) subject=subject.replace(code,'').trim();
    subject=subject.replace(/^[-•:|]+|[-•:|]+$/g,'').trim();
    if(subject.length<3) return;

    structuredRecords.push({
      headers:['Subject','Course Code','Attendance Percentage'],
      values:[subject,code,percent[1]+'%']
    });
  });

  structuredRecords.forEach(function(record){records.push(record);});

  const scheduleStructured=[];
  const scheduleSeen={};

  function addScheduleRecord(time, day, title, code, room){
    time=safe(time); day=safe(day); title=safe(title); code=safe(code); room=safe(room);
    if(title.length<3 || (!time && !day)) return;
    const key=time+'|'+day+'|'+title+'|'+code+'|'+room;
    if(scheduleSeen[key]) return;
    scheduleSeen[key]=true;
    scheduleStructured.push({
      headers:['Day','Time','Class','Course Code','Room'],
      values:[day,time,title,code,room]
    });
  }

  function attrText(el){
    if(!el) return '';
    const attrs=[
      'aria-label','title','data-time','data-start','data-end','data-date',
      'data-start-time','data-end-time','data-event','data-event-data',
      'data-datetime','data-date-time','data-starttime','data-endtime'
    ];
    return attrs.map(function(name){
      return el.getAttribute ? (el.getAttribute(name)||'') : '';
    }).filter(Boolean).join(' | ');
  }

  function findTime(value){
    const m=safe(value).match(/\b(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?\b/);
    return m ? m[0] : '';
  }

  function findDay(value){
    const m=safe(value).match(/\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b/i);
    return m ? m[0] : '';
  }

  function timeFromDate(value){
    const raw=safe(value);
    const match=raw.match(/T(\d{2}):(\d{2})/);
    if(match) return match[1]+':'+match[2];
    return '';
  }

  function dayFromDate(value){
    const raw=safe(value);
    const iso=raw.match(/(\d{4})-(\d{2})-(\d{2})/);
    if(!iso) return '';
    const date=new Date(iso[1]+'-'+iso[2]+'-'+iso[3]+'T12:00:00');
    if(Number.isNaN(date.getTime())) return '';
    return ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'][date.getDay()];
  }

  function titleFromElement(el){
    if(!el) return '';
    const selectors=[
      '.fc-event-title','.fc-title','.fc-event-main',
      '[class*="event-title"]','[class*="course-name"]',
      '[class*="course_title"]','[class*="subject-name"]',
      '[class*="subject_name"]'
    ];
    for(const selector of selectors){
      const node=el.querySelector ? el.querySelector(selector) : null;
      const value=safe(textOf(node));
      if(value.length>=3) return value;
    }
    return safe(el.getAttribute && (el.getAttribute('data-title')||el.getAttribute('data-name')||''));
  }

  function cleanScheduleTitle(value, code, time, day){
    let title=safe(value);
    if(!title) return '';
    title=title.replace(code,'').trim();
    if(time) title=title.replace(time,'').trim();
    if(day) title=title.replace(new RegExp('\\b'+day+'\\b','ig'),'').trim();
    title=title.replace(/\b(?:Lecture|Lab|Practical|Theory)\b/ig,' ').trim();
    title=title.replace(/\b[A-Z]{1,3}-\d{1,3}\b/ig,' ').trim();
    title=title.replace(/(?:^|[|•])\s*(?:Room\s*)?[A-Z]{1,3}\d{1,3}\s*(?:\|)?/ig,' ').trim();
    title=title.replace(/\s+/g,' ').replace(/^[-•:|]+|[-•:|]+$/g,'').trim();
    return title;
  }

  function collectTimeLabels(){
    const labels=[];
    const nodes=Array.from(document.querySelectorAll('.fc-timegrid-slot-label,.fc-timegrid-slot-label-cushion,.fc-timegrid-axis-cushion,[class*="timegrid-slot-label"],div,span,td,th'));
    nodes.forEach(function(el){
      const value=safe(textOf(el));
      if(!/^\d{1,2}:\d{2}$/.test(value)) return;
      const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      if(!rect || rect.width<=0 || rect.height<=0) return;
      const minutes=parseInt(value.slice(0,2),10)*60+parseInt(value.slice(3),10);
      labels.push({time:value,minutes:minutes,top:rect.top,left:rect.left});
    });
    labels.sort(function(a,b){return a.top-b.top;});
    const unique=[];
    labels.forEach(function(item){
      const duplicate=unique.some(function(existing){
        return existing.time===item.time && Math.abs(existing.top-item.top)<3;
      });
      if(!duplicate) unique.push(item);
    });
    return unique;
  }

  function collectDayLabels(){
    const labels=[];
    document.querySelectorAll('[data-date]').forEach(function(el){
      const date=safe(el.getAttribute('data-date')||'');
      const day=dayFromDate(date);
      if(!day) return;
      const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      if(!rect || rect.width<=0 || rect.height<=0) return;
      labels.push({
        day:day,
        date:date,
        center:rect.left+(rect.width/2),
        top:rect.top
      });
    });

    document.querySelectorAll('.fc-col-header-cell-cushion,.fc-col-header-cell,.fc-day-header,[class*="day-header"]').forEach(function(el){
      const value=safe(textOf(el));
      const day=findDay(value);
      if(!day) return;
      const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      if(!rect || rect.width<=0 || rect.height<=0) return;
      labels.push({day:day,date:'',center:rect.left+(rect.width/2),top:rect.top});
    });
    return labels;
  }

  const timeLabels=collectTimeLabels();
  const dayLabels=collectDayLabels();

  function inferTimeFromPosition(el){
    if(!el || !timeLabels.length || !el.getBoundingClientRect) return '';
    const rect=el.getBoundingClientRect();
    if(rect.width<=0 || rect.height<=0) return '';

    // FullCalendar time-grid events are positioned vertically by their
    // start time. Use the event's TOP, not its center, to avoid shifting
    // a 08:00 class into the next slot.
    const eventTop=rect.top;
    let best=null;
    let bestDistance=Infinity;
    timeLabels.forEach(function(label){
      const d=Math.abs(eventTop-label.top);
      if(d<bestDistance){bestDistance=d;best=label;}
    });

    if(!best) return '';

    // If labels are evenly spaced, interpolate between adjacent labels
    // so 08:30/09:00/etc are recovered even when the event does not sit
    // exactly on the label pixel.
    const ordered=timeLabels.slice().sort(function(a,b){return a.top-b.top;});
    for(let i=0;i<ordered.length-1;i++){
      const a=ordered[i], b=ordered[i+1];
      if(eventTop>=a.top && eventTop<=b.top && b.top>a.top){
        const ratio=(eventTop-a.top)/(b.top-a.top);
        const minutes=Math.round(a.minutes+(b.minutes-a.minutes)*ratio);
        const snapped=Math.max(0,Math.min(1439,minutes));
        const hh=String(Math.floor(snapped/60)).padStart(2,'0');
        const mm=String(snapped%60).padStart(2,'0');
        return hh+':'+mm;
      }
    }

    return bestDistance<=140 ? best.time : '';
  }

  function inferDayFromPosition(el){
    if(!el || !el.getBoundingClientRect) return '';

    let parent=el;
    for(let depth=0;depth<8 && parent;depth++,parent=parent.parentElement){
      const date=safe(parent.getAttribute && (parent.getAttribute('data-date')||''));
      const day=dayFromDate(date);
      if(day) return day;
    }

    if(!dayLabels.length) return '';
    const rect=el.getBoundingClientRect();
    if(rect.width<=0 || rect.height<=0) return '';
    const center=rect.left+(rect.width/2);
    let nearest=null;
    let distance=Infinity;
    dayLabels.forEach(function(label){
      const d=Math.abs(center-label.center);
      if(d<distance){distance=d;nearest=label;}
    });
    return nearest && distance<=220 ? nearest.day : '';
  }

  const eventSelectors=[
    '.fc-timegrid-event','.fc-event','.fc-daygrid-event','.fc-event-main',
    '.o_calendar_event','.calendar_event','[class*="calendar-event"]',
    '[class*="schedule-event"]','[class*="timetable-event"]',
    '[data-start]','[data-event]','[data-event-data]',
    '[data-time]','[data-datetime]'
  ];
  const eventNodes=[];
  eventSelectors.forEach(function(selector){
    document.querySelectorAll(selector).forEach(function(el){
      if(eventNodes.indexOf(el)<0) eventNodes.push(el);
    });
  });

  eventNodes.forEach(function(el){
    const raw=safe(textOf(el));
    const attrs=safe(attrText(el));
    const titleCandidate=titleFromElement(el);
    if((!raw && !attrs && !titleCandidate) || raw.length>900) return;

    const combined=safe([raw,attrs,titleCandidate].filter(Boolean).join(' | '));
    const codeMatch=combined.match(codePattern);
    const genericTitle = titleCandidate && titleCandidate.length >= 3 && titleCandidate.length <= 140;
    const scheduleNode = /fc-event|calendar-event|schedule-event|timetable-event|o_calendar_event|calendar_event/i.test(
      String(el.className||'')
    );
    if(!codeMatch && !subjectPattern.test(combined) && !genericTitle && !scheduleNode) return;

    let time=findTime(attrs) || findTime(raw) || findTime(titleCandidate);
    let day=findDay(attrs) || findDay(raw) || findDay(titleCandidate);

    if(!time){
      time=timeFromDate(el.getAttribute && (
        el.getAttribute('data-start') ||
        el.getAttribute('data-datetime') ||
        el.getAttribute('data-date-time') ||
        ''
      ));
    }
    if(!day){
      day=dayFromDate(el.getAttribute && (
        el.getAttribute('data-start') ||
        el.getAttribute('data-datetime') ||
        el.getAttribute('data-date-time') ||
        ''
      ));
    }

    if(!time || !day){
      let parent=el.parentElement;
      for(let depth=0;depth<4 && parent;depth++,parent=parent.parentElement){
        const parentAttrs=safe(attrText(parent));
        if(!time) time=findTime(parentAttrs) || timeFromDate(parent.getAttribute && parent.getAttribute('data-start') || '');
        if(!day) day=findDay(parentAttrs) || dayFromDate(parent.getAttribute && parent.getAttribute('data-start') || '');
        if(time && day) break;
      }
    }

    if(!time) time=inferTimeFromPosition(el);
    if(!day) day=inferDayFromPosition(el);

    const code=codeMatch ? codeMatch[0] : '';
    let title=titleCandidate || raw || attrs;
    title=cleanScheduleTitle(title,code,time,day);

    if(title.length>140){
      const fragments=title.split(/\s{2,}|\|/).map(clean).filter(Boolean);
      const candidate=fragments.find(function(fragment){
        return fragment.length>=3 && fragment.length<=110 &&
          (subjectPattern.test(fragment) || codePattern.test(fragment));
      });
      if(candidate) title=candidate;
    }

    if(title.length<3 && code) title=code;
    if(title.length<3) return;
    addScheduleRecord(time,day,title,code,'');
  });

  // Non-calendar fallback: only use explicit time/day values from the
  // row itself. This prevents the first visible 08:00 axis label from
  // becoming the time for every subject.
  const fallbackNodes=Array.from(clone.querySelectorAll('li,td,tr,.card,.item,.row'));
  fallbackNodes.forEach(function(el){
    if(eventNodes.indexOf(el)>=0) return;
    const raw=safe(textOf(el));
    if(!raw || raw.length>320) return;
    const attrs=safe(attrText(el));
    const combined=safe([raw,attrs].filter(Boolean).join(' | '));
    if(!codePattern.test(combined) && !subjectPattern.test(combined)) return;

    const codeMatch=combined.match(codePattern);
    const code=codeMatch ? codeMatch[0] : '';
    const time=findTime(attrs) || findTime(raw);
    const day=findDay(attrs) || findDay(raw);
    const title=cleanScheduleTitle(raw,code,time,day);
    if(title.length<3 || (!time && !day)) return;
    let room='';
    const roomMatch=combined.match(/(?:room|venue|location)\s*[:#-]?\s*([A-Z0-9-]+)/i);
    if(roomMatch) room=roomMatch[1];
    addScheduleRecord(time,day,title,code,room);
  });

  scheduleStructured.forEach(function(record){records.push(record);});

  return JSON.stringify({
    cards:unique(cards).slice(0,8),
    tables:unique(tables).slice(0,12),
    lines:unique(lines).slice(0,60),
    records:unique(records).slice(0,160),
    overallAttendance:overallAttendance
  });
})();
