(function(){
  const text=(document.body&&document.body.innerText||'').toLowerCase();
  return text.includes('wrong login') || text.includes('invalid login') || text.includes('incorrect') || text.includes('authentication failed');
})();
