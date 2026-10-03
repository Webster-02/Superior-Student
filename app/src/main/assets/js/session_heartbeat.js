(function(){
  return fetch('/student/dashboard',{credentials:'include',cache:'no-store'})
    .then(function(response){
      var finalUrl=(response.url||'').toLowerCase();
      if(finalUrl.indexOf('/web/login')!==-1 || response.redirected && finalUrl.indexOf('/web/login')!==-1){
        return 'EXPIRED';
      }
      return response.ok ? 'OK' : 'OFFLINE';
    })
    .catch(function(){ return 'OFFLINE'; });
})();
