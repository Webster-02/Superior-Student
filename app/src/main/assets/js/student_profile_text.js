(function(){
  function clean(v){return (v||'').replace(/\s+/g,' ').trim();}
  function safe(v){return clean(v).replace(/\bERP\b/gi,'').replace(/\bOdoo\b/gi,'').trim();}
  function textOf(el){return el ? safe(el.innerText || el.textContent || el.value || '') : '';}
  function validName(v){
    v=clean(v);
    return v.length>=3 && v.length<=80 &&
      !/session|expire|dashboard|welcome|student information/i.test(v) &&
      !/functional english|quantitative reasoning|civics and community engagement/i.test(v) &&
      !/^SU\d+[-A-Z0-9]*$/i.test(v) &&
      !/^\d[\d .:/-]*$/.test(v);
  }

  let name='';
  const selectors=[
    '.student-details h1','.student-details h2','.student-name','.student_name',
    '[data-field="student_name"]','[name="student_name"]',
    '[class*="student"] [class*="name"]'
  ];
  for(const selector of selectors){
    for(const el of Array.from(document.querySelectorAll(selector))){
      const v=textOf(el);
      if(validName(v) && v.split(/\s+/).length>=2){name=v;break;}
    }
    if(name)break;
  }

  if(!name){
    for(const label of Array.from(document.querySelectorAll('label,.o_form_label,dt'))){
      const labelText=textOf(label);
      if(!/^(student\s*name|full\s*name|name)$/i.test(labelText))continue;
      const parent=label.parentElement;
      const candidates=[
        parent && parent.querySelector('.o_field_widget,.o_field_char,.o_field_text,.form-control,dd'),
        parent && parent.nextElementSibling
      ];
      for(const el of candidates){
        const v=textOf(el);
        if(validName(v)){name=v;break;}
      }
      if(name)break;
    }
  }

  const fields=[];
  const seen=new Set();
  function addField(label,value){
    label=safe(label); value=safe(value);
    if(!label||!value||label.length>100||value.length>180||label.toLowerCase()===value.toLowerCase())return;
    if(/password|csrf|token|search|login/i.test(label))return;
    const key=label.toLowerCase()+'|'+value.toLowerCase();
    if(seen.has(key))return;
    seen.add(key);
    fields.push({label:label,value:value});
  }

  document.querySelectorAll('label[for]').forEach(function(label){
    const id=label.getAttribute('for'); if(!id)return;
    const field=Array.from(document.querySelectorAll('[id]')).find(function(n){return n.id===id;});
    addField(textOf(label),textOf(field));
  });

  document.querySelectorAll('dt').forEach(function(dt){
    if(dt.nextElementSibling)addField(textOf(dt),textOf(dt.nextElementSibling));
  });

  document.querySelectorAll('tr').forEach(function(tr){
    const cells=Array.from(tr.querySelectorAll('th,td')).map(textOf).filter(Boolean);
    if(cells.length===2)addField(cells[0],cells[1]);
  });

  document.querySelectorAll('.o_group,.o_form_sheet,.o_form_nosheet,.o_form_view,.form-group,.form-row,.profile-item,.profile-field,.info-row,.info-item').forEach(function(row){
    const label=row.querySelector('label,.o_form_label,dt,.form-label');
    const value=row.querySelector('.o_field_widget,.o_field_char,.o_field_text,.o_field_integer,.o_field_float,.o_field_many2one,.form-control,dd');
    if(label&&value)addField(textOf(label),textOf(value));
  });

  const cards=Array.from(document.querySelectorAll('.stat-card,.summary-card,.info-box'))
    .map(textOf).filter(Boolean).slice(0,20);
  const body=safe(document.body ? (document.body.innerText||document.body.textContent||'') : '');
  return JSON.stringify({
    name:name,
    fields:fields,
    cards:cards,
    body:body,
    hasUsefulFields:fields.length>0 || cards.length>0
  });
})();
