(function(){
  const text = (document.body && document.body.innerText || '').toLowerCase();
  const banner = Array.from(document.querySelectorAll(
    '.alert-danger,.o_login_error,.invalid-feedback,[role="alert"]'
  )).map(function(node) { return (node.innerText || node.textContent || '').toLowerCase(); }).join(' ');
  const message = text + ' ' + banner;
  return /wrong login|wrong password|invalid login|invalid username|invalid password|incorrect password|authentication failed|access denied|login or password is incorrect/.test(message);
})();