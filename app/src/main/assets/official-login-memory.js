(() => {
  if (window.__officialLoginMemory || window !== top || location.protocol !== 'https:') return;
  const visible = e => {
    const rect = e.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    for (let node = e; node instanceof Element; node = node.parentElement) {
      const style = getComputedStyle(node);
      if (style.display === 'none' || ['hidden','collapse'].includes(style.visibility) ||
          Number(style.opacity) === 0) return false;
    }
    return true;
  };
  const challenge = e => /captcha|verifycode|verification|otp|one-time-code|验证码/i.test(e.name+' '+e.id+' '+e.autocomplete+' '+e.placeholder);
  // iView uses .ivu-modal for notices as well as challenges. A notice is not a CAPTCHA.
  const humanChallenge = () =>
    [...document.querySelectorAll('iframe[src*="captcha"],img[src*="captcha"],input[autocomplete="one-time-code"],.vue-auth-box_,.auth-control_')].some(visible) ||
    [...document.querySelectorAll('.ivu-modal')].some(e => visible(e) &&
      /验证码|安全验证|二次认证|人机验证|拖动滑块/.test(e.textContent || ''));
  const casPage = location.origin === 'https://cas.scvtc.edu.cn' &&
    ['/cas/WEB/index.html','/cas/H5/index.html'].includes(location.pathname);
  const allowedService = () => {
    const service = new URL(location.href).searchParams.get('service');
    if (!service) return true;
    try {
      const u = new URL(service);
      return u.protocol === 'https:' && !u.username && !u.password &&
        (u.hostname === 'jwxt.scvtc.edu.cn' && ['', '443'].includes(u.port) && u.pathname === '/api/cas/login' ||
         u.hostname === 'www.shulin-soft.com' && u.port === '8267' && u.pathname === '/casLogin.html');
    } catch { return false; }
  };
  const identify = (allowHuman = false) => {
    if (casPage && (!allowedService() || !['','#','#/'].includes(location.hash))) return null;
    const passwords = [...document.querySelectorAll('input[type=password]')].filter(visible);
    if (passwords.length !== 1) return null;
    const password = passwords[0], form = password.form;
    // Actual H5 Vue/iView DOM has no form: the official button's handler owns
    // encryption and submission. Scope this exception to the observed panel.
    const h5 = casPage && location.pathname === '/cas/H5/index.html';
    const panels = [...document.querySelectorAll('.login-panel > .content-info')].filter(visible);
    const scope = form || (h5 && panels.length === 1 ? panels[0] : null);
    if (!scope || !scope.contains(password)) return null;
    if (h5 && (!password.matches('.account-input--pwd input.ivu-input') || password.placeholder !== '请输入密码')) return null;
    const action = new URL(form?.getAttribute('action') || location.href, location.href);
    if (action.origin !== location.origin || action.protocol !== 'https:') return null;
    const fields = [...scope.querySelectorAll('input')].filter(visible);
    const human=fields.some(e => visible(e) && challenge(e)) || humanChallenge();
    if(human&&!allowHuman)return null;
    const users = fields.filter(e => ['text','email','tel'].includes(e.type) && e !== password && !challenge(e));
    if (users.length !== 1 || fields.some(e => e.required && ![users[0],password].includes(e) && !challenge(e))) return null;
    if (h5 && (!users[0].matches('.account-input:not(.account-input--pwd) input.ivu-input') || users[0].placeholder !== '请输入账号')) return null;
    // Observed CAS Vue login: inputs have no name/id and .login-button is outside
    // the form. Use that exact verified page adapter, preserving its own handler.
    const schoolCas=location.origin==='https://cas.scvtc.edu.cn'&&location.pathname==='/cas/WEB/index.html';
    const buttons = [...scope.querySelectorAll(h5 ? '.info-button > button.button-item' : 'button,input[type=submit]'),...(schoolCas?[...document.querySelectorAll('.login-button')]:[])].filter(e => visible(e) && !e.disabled && e.getAttribute('aria-disabled') !== 'true' && !e.classList.contains('ivu-btn-loading') &&
      (e.type === 'submit' || /^(登\s*录|login|sign\s*in)$/i.test((e.textContent || e.value || '').trim())));
    if (buttons.length !== 1) return null;
    const button = buttons[0], username = users[0];
    const binding = { origin:location.origin, path:location.pathname, action:action.origin+action.pathname,
      user:username.name+'|'+username.id+'|'+username.placeholder, password:password.name+'|'+password.id+'|'+password.placeholder,
      button:(button.textContent || button.value || '').trim()+'|'+(button.type||button.tagName) };
    return { username,password,button,binding,scope };
  };
  const capture = found => {
    if (!found || !found.password.value) return;
    window.OfficialLoginBridge?.postMessage(JSON.stringify({page:location.href,binding:found.binding,
      username:found.username.value,password:found.password.value}));
  };
  document.addEventListener('click', event => {
    if (!event.isTrusted) return;
    const found = identify(true);
    if (found?.button.contains(event.target)) capture(found);
  }, true);
  document.addEventListener('keydown', event => {
    if (!event.isTrusted || event.key !== 'Enter' || event.isComposing) return;
    const found=identify(true);
    if (found && ([found.username,found.password].includes(event.target)||(event.target instanceof HTMLInputElement&&found.scope.contains(event.target)&&challenge(event.target)))) capture(found);
  }, true);
  const matches = (found,binding) => found && Object.keys(found.binding).length === Object.keys(binding).length &&
    Object.keys(found.binding).every(key => found.binding[key] === binding[key]);
  const fill=(found,username,password)=>{
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
    for (const [input,value] of [[found.username,username],[found.password,password]]) {
      setter.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));
    }
  };
  window.__officialLoginMemory = { describe:() => identify(true)?.binding || null,
    challengeRequired:() => humanChallenge() || [...document.querySelectorAll('input')].some(e => visible(e) && challenge(e)),
    matches:binding => !!matches(identify(),binding), matchesForInput:binding=>!!matches(identify(true),binding),
    configure(binding,username,password,submit=true){
      const found=identify(true);if(!matches(found,binding))return false;
      fill(found,username,password);if(submit&&matches(identify(),binding))found.button.click();return true;
    },restore(binding,username,password) {
    const found = identify();
    if (!matches(found,binding)) return false;
    fill(found,username,password);
    found.button.click(); return true;
  }};
})();
