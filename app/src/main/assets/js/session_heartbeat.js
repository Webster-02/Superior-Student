(function(){
  return fetch('/student/dashboard',{credentials:'include',cache:'no-store'})
    .then(function(response){
      var finalUrl=(response.url||'').toLowerCase();
      if(finalUrl.indexOf('/web/login')!==-1){
        return 'EXPIRED';
      }
      if(response.status===401 || response.status===403){
        return 'EXPIRED';
      }
      if(!response.ok){
        // Server and transport failures are not evidence of an invalid session.
        return 'OFFLINE';
      }
      return response.clone().text().then(function(body){
        var text=(body||'').toLowerCase();
        if(/invalid session|session expired|session is expired|not logged in|please log in again/.test(text)){
          return 'EXPIRED';
        }
        return 'OK';
      }).catch(function(){ return 'OK'; });
    })
    .catch(function(){ return 'OFFLINE'; });
})();
