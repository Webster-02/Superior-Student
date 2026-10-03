(function(){
  const user=document.querySelector('input[name="login"]');
  const pass=document.querySelector('input[name="password"],input[type="password"]');
  if(!user||!pass)return 'NO_FORM';
  user.value=JSON.parse(atob('%USERNAME_B64%'));
  pass.value=JSON.parse(atob('%PASSWORD_B64%'));
  user.dispatchEvent(new Event('input',{bubbles:true}));
  user.dispatchEvent(new Event('change',{bubbles:true}));
  pass.dispatchEvent(new Event('input',{bubbles:true}));
  pass.dispatchEvent(new Event('change',{bubbles:true}));
  const form=pass.form||user.form;
  if(!form)return 'NO_FORM';
  const redirect=form.querySelector('input[name="redirect"]');
  if(redirect)redirect.value='/student/dashboard';
  HTMLFormElement.prototype.submit.call(form);
  return 'SUBMITTED';
})();
