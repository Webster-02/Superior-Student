(function() {
  const visible = function(element) {
    if (!element) return false;
    const style = window.getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' &&
      Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;
  };
  const user = document.querySelector(
    'input[name="login"],#login,input[autocomplete="username"],input[name="email"]'
  );
  const password = document.querySelector(
    'input[name="password"],#password,input[type="password"]'
  );
  const bodyText = (document.body && document.body.innerText || '').toLowerCase();
  const errorText = /wrong login|wrong password|invalid login|invalid username|invalid password|incorrect password|authentication failed|access denied|login or password is incorrect/.test(bodyText);
  if (user && password && visible(user) && visible(password)) {
    if (errorText) return 'LOGIN_ERROR';
    const errorBanner = document.querySelector('.alert-danger,.o_login_error,[role="alert"]');
    if (errorBanner && visible(errorBanner) && /wrong|invalid|incorrect|failed|denied/i.test(errorBanner.innerText || '')) {
      return 'LOGIN_ERROR';
    }
    return 'LOGIN_FORM';
  }
  const authenticatedMarker = document.querySelector(
    'a[href*="/web/session/logout"],a[href*="logout"],.o_user_menu,.o_main_navbar'
  );
  if (authenticatedMarker && visible(authenticatedMarker)) return 'AUTHENTICATED';
  return 'UNKNOWN';
})();