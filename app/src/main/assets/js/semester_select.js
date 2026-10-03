(function(){
                      const wanted=JSON.parse(atob('%SEMESTER_B64%'));
                      const selects=Array.from(document.querySelectorAll('select'));
                      const select=selects.find(function(s){
                        return Array.from(s.options||[]).some(function(o){
                          return (o.textContent||'').trim()===wanted;
                        });
                      });
                      if(!select)return 'NO_SELECT';
                      const option=Array.from(select.options||[]).find(function(o){
                        return (o.textContent||'').trim()===wanted;
                      });
                      if(!option)return 'NO_OPTION';
                      select.value=option.value;
                      select.dispatchEvent(new Event('change',{bubbles:true}));
                      return 'CHANGED';
                    })();
