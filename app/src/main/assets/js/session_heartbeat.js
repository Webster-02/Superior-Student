(function(){
  var controller = typeof AbortController === 'function' ? new AbortController() : null;
  var timeoutId = null;
  var options = {credentials:'include',cache:'no-store'};
  if (controller) options.signal = controller.signal;

  var request = fetch('/student/dashboard', options)
    .then(function(response){
      var finalUrl=(response.url||'').toLowerCase();
      // Explicit authentication codes take precedence over generic URL signals.
      if(response.status===401 || response.status===403){
        return 'EXPIRED';
      }
      // Server/transport errors are not evidence of a rejected session, even
      // if an error page happens to use a login-looking URL.
      if(!response.ok){
        return 'OFFLINE';
      }
      if(finalUrl.indexOf('/web/login')!==-1 || finalUrl.indexOf('/tsg/login')!==-1 ||
         finalUrl.indexOf('/student/login')!==-1){
        return 'EXPIRED';
      }
      return response.clone().text().then(function(body){
        var text=(body||'').toLowerCase();
        if(/invalid session|session expired|session is expired|not logged in|please log in again/.test(text)){
          return 'EXPIRED';
        }
        return 'OK';
      }).catch(function(){ return 'OFFLINE'; });
    })
    .catch(function(){ return 'OFFLINE'; });

  var timeout = new Promise(function(resolve){
    timeoutId = setTimeout(function(){
      try { if(controller) controller.abort(); } catch (_) {}
      resolve('OFFLINE');
    }, 8000);
  });

  return Promise.race([request, timeout]).then(function(state){
    if(timeoutId!==null) clearTimeout(timeoutId);
    return state;
  },function(){
    if(timeoutId!==null) clearTimeout(timeoutId);
    return 'OFFLINE';
  });
})();
