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
  const pageRootSelectors=['#page_content_inner','#page_content','.user_profile','.user_content',
    '.o_form_view','.o_form_sheet_bg','.o_portal_wrap','.o_portal_my_home',
    'main,[role="main"]','.oe_structure','.container-fluid','.container'];
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
  // Preserve the ERP profile form's visible read-only values, but never emit
  // hidden/edit controls as profile information.
  clone.querySelectorAll('form input,form button,form select,form textarea,form .profile_edit_fields').forEach(function(el){el.remove();});

  const records=[];
  if(/\/student\/profile/i.test(location.pathname)){
    function addProfileRecord(label,value){
      label=safe(label).replace(/[\s:]+$/,'').trim();
      value=safe(value);
      if(!label || !value || label.length>90 || value.length>260 || label.toLowerCase()===value.toLowerCase()) return;
      if(/^(created on|updated on|last updated|notifications?)$/i.test(label)) return;
      if(/^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}$/.test(value)) return;
      if(label.toLowerCase()==='nationlity') label='Nationality';
      records.push({headers:['Field','Value'],values:[label,value]});
    }

    // Superior's student portal uses a custom legacy theme, not standard Odoo
    // form labels. Capture its actual identity heading and academic summary.
    const profileHeading=clone.querySelector('.user_heading .heading_b');
    if(profileHeading){
      const parts=Array.from(profileHeading.querySelectorAll('span')).map(function(node){return safe(textOf(node));}).filter(Boolean);
      if(parts[0]) addProfileRecord('Student Name',parts[0]);
      if(parts[1]) addProfileRecord('Registration No',parts[1]);
      if(parts[2]) addProfileRecord('Faculty',parts[2]);
    }
    clone.querySelectorAll('.user_stats > li').forEach(function(item){
      const label=safe(textOf(item.querySelector('.sub-heading')));
      const value=safe(textOf(item.querySelector('h4')));
      if(label && value) addProfileRecord(label,value);
    });

    // Contact Information, Personal Detail and Family Detail use .md-list-content.
    // The value may be an md-list-heading span or a plain text node (for Gender).
    clone.querySelectorAll('.user_content .md-list-content').forEach(function(item){
      const labelNode=item.querySelector('.uk-text-small.uk-text-muted');
      const label=safe(textOf(labelNode));
      if(!label) return;
      const valueNodes=Array.from(item.querySelectorAll('.md-list-heading'));
      let value=unique(valueNodes.map(function(node){return safe(textOf(node));})
        .filter(function(text){return text && text.toLowerCase()!==label.toLowerCase();})).join(' • ');
      if(!value){
        const raw=safe(textOf(item));
        const lowerRaw=raw.toLowerCase(), lowerLabel=label.toLowerCase();
        const at=lowerRaw.lastIndexOf(lowerLabel);
        if(at>=0 && at+lowerLabel.length>=lowerRaw.length-2) value=raw.slice(0,at).trim();
      }
      if(value) addProfileRecord(label,value);
    });

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

  
  return JSON.stringify({
    cards:[],
    tables:[],
    lines:[],
    records:unique(records).slice(0,160),
    overallAttendance:null,
    attendanceDetail:null,
    attendanceDetailLinks:[],
    invoices:[],
    academicResults:[]
  });
})();