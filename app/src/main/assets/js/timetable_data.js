(function(){
  function clean(value){return String(value||'').replace(/\s+/g,' ').trim();}
  function textOf(el){return el ? clean(el.innerText || el.textContent || '') : '';}
  function safe(value){return clean(value).replace(/\bERP\b/gi,'').replace(/\bOdoo\b/gi,'').replace(/\s+/g,' ').trim();}

  // Select the calendar region when possible. The event parser still inspects
  // live DOM attributes and CSS pseudo-content because ERP times may be rendered
  // outside an event's ordinary text.
  const pageRootSelectors=['.fc','.o_calendar_view','.o_calendar_renderer','.cd-schedule',
    'main,[role="main"]','.o_portal_wrap','.container-fluid','.container'];
  let root=null;
  for(const selector of pageRootSelectors){
    const candidate=document.querySelector(selector);
    if(candidate){root=candidate;break;}
  }
  if(!root) root=document.body;
  const clone=root.cloneNode(true);
  clone.querySelectorAll('header,nav,footer,aside,script,style,noscript').forEach(function(el){el.remove();});
  clone.querySelectorAll('form').forEach(function(el){el.remove();});

  const codePattern=/\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b/i;
  const subjectPattern=/\b(?:course|subject|lecture|lab|tutorial|seminar|practical|clinical|rotation)\b/i;

  const scheduleStructured=[];
  const scheduleSeen={};

  function addScheduleRecord(time, day, title, code){
    time=safe(time); day=safe(day); title=safe(title); code=safe(code);
    if(title.length<3 || (!time && !day)) return;
    const key=time+'|'+day+'|'+title+'|'+code;
    if(scheduleSeen[key]) return;
    scheduleSeen[key]=true;
    scheduleStructured.push({
      headers:['Day','Time','Class','Course Code'],
      values:[day,time,title,code]
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
    const m=safe(value).match(/\b(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*(?:[-–—]|to)\s*(?:[01]?\d|2[0-3]):[0-5]\d)?\b/i);
    return m ? m[0].replace(/\s+/g,' ').trim() : '';
  }

  // Some ERP themes draw event times with CSS generated content instead of
  // putting the time in innerText/textContent. Read generated content first.
  function pseudoContent(el, pseudo){
    if(!el || !window.getComputedStyle) return '';
    try{
      const value=window.getComputedStyle(el,pseudo).content || '';
      if(!value || value==='none' || value==='normal' || value==='"' || value==="''") return '';
      return value.replace(/^(['"])(.*)\1$/,'$2').trim();
    }catch(_){return '';}
  }

  function timeFromEventDecorations(el){
    if(!el) return '';
    const nodes=[el].concat(Array.from(el.querySelectorAll ? el.querySelectorAll('*') : []));
    const parts=[];
    nodes.forEach(function(node){
      const attrs=attrText(node);
      if(attrs) parts.push(attrs);
      ['::before','::after'].forEach(function(pseudo){
        const generated=pseudoContent(node,pseudo);
        if(generated) parts.push(generated);
      });
    });
    return findTime(parts.join(' | '));
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

  function endTimeFromDate(value){
    return timeFromDate(value);
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
    if(code) title=title.replace(code,' ');
    if(time) title=title.replace(time,' ');
    if(day) title=title.replace(new RegExp('\\b'+day+'\\b','ig'),' ');
    title=title
      .replace(/\b20\d{2}\s*[-–]\s*20\d{2}\s*[-–]\s*[A-Z0-9-]+\b/ig,' ')
      .replace(/\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b/ig,' ')
      .replace(/\b\d+\s*\.{1,3}(?:\s*\(\s*\)\s*)+/g,' ')
      .replace(/\b\d+\s*\.{1,3}\b/g,' ')
      .replace(/\(\s*\)/g,' ')
      .replace(/\b\d{1,2}\s*[-–]\s*\d{1,2}\s*\[?\s*(?:Lecture|Lab|Tutorial|Practical|Theory)\s*\]?/ig,' ')
      .replace(/\b(?:Lecture|Lab|Tutorial|Practical|Theory)\b/ig,' ')
      .replace(/(?:^|[|•])\s*(?:Room\s*)?[A-Z]{1,3}\d{1,3}\s*(?:\|)?/ig,' ')
      .replace(/\s+/g,' ')
      .replace(/^[-•:|]+|[-•:|]+$/g,' ')
      .trim();
    return title;
  }

  function collectTimeLabels(){
    const labels=[];
    const nodes=Array.from(document.querySelectorAll('.cd-schedule__timeline li,.fc-timegrid-slot-label,.fc-timegrid-slot-label-cushion,.fc-timegrid-axis-cushion,[class*="timegrid-slot-label"],div,span,td,th'));
    nodes.forEach(function(el){
      const value=safe(textOf(el));
      if(!/^\d{1,2}:\d{2}$/.test(value)) return;
      const customTimeline = el.closest ? el.closest('.cd-schedule__timeline') : null;
      // For the custom ERP calendar, each LI marks the real time-slot boundary;
      // its nested SPAN is label text and may be offset by CSS padding.
      if(customTimeline && String(el.tagName).toLowerCase() !== 'li') return;
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

  function inferTimeAtPosition(position){
    if(!timeLabels.length || !Number.isFinite(position)) return '';
    const ordered=timeLabels.slice().sort(function(a,b){return a.top-b.top;});
    let best=null;
    let bestDistance=Infinity;
    ordered.forEach(function(label){
      const d=Math.abs(position-label.top);
      if(d<bestDistance){bestDistance=d;best=label;}
    });
    if(!best) return '';

    for(let i=0;i<ordered.length-1;i++){
      const a=ordered[i], b=ordered[i+1];
      if(position>=a.top && position<=b.top && b.top>a.top){
        const ratio=(position-a.top)/(b.top-a.top);
        const minutes=Math.round(a.minutes+(b.minutes-a.minutes)*ratio);
        const snapped=Math.max(0,Math.min(1439,minutes));
        const hh=String(Math.floor(snapped/60)).padStart(2,'0');
        const mm=String(snapped%60).padStart(2,'0');
        return hh+':'+mm;
      }
    }
    return bestDistance<=140 ? best.time : '';
  }

  function inferTimeFromPosition(el){
    if(!el || !el.getBoundingClientRect) return '';
    const rect=el.getBoundingClientRect();
    if(rect.width<=0 || rect.height<=0) return '';
    return inferTimeAtPosition(rect.top);
  }

  function inferEndTimeFromPosition(el){
    if(!el || !el.getBoundingClientRect) return '';
    const rect=el.getBoundingClientRect();
    if(rect.width<=0 || rect.height<=0) return '';
    return inferTimeAtPosition(rect.bottom);
  }

  function clockMinutes(value){
    const match=String(value||'').match(/^(\d{1,2}):(\d{2})$/);
    if(!match) return null;
    const hour=Number(match[1]), minute=Number(match[2]);
    if(hour<0 || hour>23 || minute<0 || minute>59) return null;
    return hour*60+minute;
  }

  function inferRangeFromPosition(el){
    const start=inferTimeFromPosition(el);
    const end=inferEndTimeFromPosition(el);
    const startMinutes=clockMinutes(start);
    const endMinutes=clockMinutes(end);
    if(startMinutes!==null && endMinutes!==null && endMinutes>startMinutes){
      return start+' - '+end;
    }
    return start;
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
    '.cd-schedule__event',
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

  // Keep one root per calendar event. Nested .fc-event-main elements contain
  // only part of a card and can produce duplicate/mixed titles.
  const eventRootPattern=/cd-schedule__event|fc-timegrid-event|fc-event|fc-daygrid-event|o_calendar_event|calendar-event|schedule-event|timetable-event/i;
  const rootEventNodes=eventNodes.filter(function(el){
    return !eventNodes.some(function(parent){
      if(parent===el || !parent.contains || !parent.contains(el)) return false;
      return eventRootPattern.test(String(parent.className||'')) ||
        (parent.hasAttribute && parent.hasAttribute('data-start') && parent.hasAttribute('data-end'));
    });
  });
  eventNodes.splice(0,eventNodes.length);
  rootEventNodes.forEach(function(el){eventNodes.push(el);});

  eventNodes.forEach(function(el){
    const eventClass=String(el.className||'');
    const isSuperiorCalendarEvent=/(?:^|\s)cd-schedule__event(?:\s|$)/.test(eventClass);
    if(isSuperiorCalendarEvent && el.closest){
      const group=el.closest('.cd-schedule__group');
      if(group){
        // Use the event's own title/code and its nearest weekday group. Do not read
        // the parent group's full text, because it includes sibling subjects too.
        const day=findDay(textOf(group.querySelector('.cd-schedule__top-info'))) || findDay(textOf(group));
        const names=Array.from(el.querySelectorAll('.cd-schedule__name')).map(function(node){
          return safe(textOf(node));
        }).filter(Boolean);
        const titleSource=names[0] || titleFromElement(el);
        const codeMatch=names.map(function(value){return value.match(codePattern);}).find(Boolean) ||
          safe(textOf(el)).match(codePattern);
        const code=codeMatch ? codeMatch[0] : '';
        const title=cleanScheduleTitle(titleSource,code,'',day);

        const link=el.querySelector('a');
        // Prefer actual ERP-rendered time text, including CSS pseudo-elements.
        // Retain geometric inference for themes that position events without a text label.
        const decoratedTime=timeFromEventDecorations(el);
        const decoratedLinkTime=link ? timeFromEventDecorations(link) : '';
        let time=decoratedTime || decoratedLinkTime;
        const inferredTime=inferRangeFromPosition(el);
        const inferredLinkTime=link ? inferRangeFromPosition(link) : '';
        const geometryTime=[inferredLinkTime,inferredTime].find(function(value){
          return value && value.indexOf(' - ')>=0;
        }) || inferredLinkTime || inferredTime;
        if(!time || time.indexOf(' - ')<0){
          if(geometryTime && geometryTime.indexOf(' - ')>=0) time=geometryTime;
          else if(!time) time=geometryTime;
        }

        if(day && title.length>=3) addScheduleRecord(time,day,title,code);
        return;
      }
    }

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
    const startDateValue=el.getAttribute && (
      el.getAttribute('data-start') ||
      el.getAttribute('data-datetime') ||
      el.getAttribute('data-date-time') || ''
    );
    const endDateValue=el.getAttribute && (
      el.getAttribute('data-end') ||
      el.getAttribute('data-end-time') ||
      el.getAttribute('data-endtime') || ''
    );
    const explicitStart=timeFromDate(startDateValue);
    const explicitEnd=endTimeFromDate(endDateValue);

    if(!day){
      day=dayFromDate(startDateValue);
    }
    if(!time || !/(?:[01]?\d|2[0-3]):[0-5]\d\s*[-–—]\s*(?:[01]?\d|2[0-3]):[0-5]\d/.test(time)){
      const start=explicitStart || findTime(time);
      const end=explicitEnd || inferEndTimeFromPosition(el);
      if(start && end && start!==end){
        time=start+' - '+end;
      } else if(start){
        time=start;
      } else {
        time=inferTimeFromPosition(el);
      }
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
    addScheduleRecord(time,day,title,code);
  });

  // Non-calendar fallback: only use explicit time/day values from the
  // row itself. This prevents the first visible 08:00 axis label from
  // becoming the time for every subject.
  const fallbackNodes=Array.from(clone.querySelectorAll('li,td,tr,.card,.item,.row'));
  fallbackNodes.forEach(function(el){
    // Never parse an aggregate calendar/week container as a class: it contains
    // several sibling subjects and caused multiple classes to merge into one card.
    if(el.closest && el.closest('.cd-schedule__event, .cd-schedule__group, .cd-schedule, .fc-event, .o_calendar_event, [class*="calendar-event"], [class*="schedule-event"], [class*="timetable-event"]')) return;
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
    addScheduleRecord(time,day,title,code);
  });

  
  return JSON.stringify({
    cards:[],
    tables:[],
    lines:[],
    records:scheduleStructured.slice(0,160),
    overallAttendance:null,
    attendanceDetail:null,
    attendanceDetailLinks:[],
    invoices:[],
    academicResults:[]
  });
})();