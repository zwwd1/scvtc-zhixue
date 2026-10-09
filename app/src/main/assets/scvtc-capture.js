/* Official navigation and identity JSON only. No HTML data extraction or business submission. */
(() => {
  if (window.__scvtc) return;
  const documentId = Date.now() + ':document:' + Math.random().toString(36).slice(2);
  const generation = documentId;
  const paths = new Set(['/jwgr/api/student/studentInfo/querySelf', '/jwgr/api/baseInfo/semester/selectCurrentXnXq']);
  const post = data => {
    try { window.CampusBridge?.postMessage(JSON.stringify({...data, documentId, generation, page: location.href})); } catch (_) {}
  };
  post({kind: 'hello'});
  const target = value => {
    try { const u = new URL(value, location.href); return u.origin === 'https://jwxt.scvtc.edu.cn' && paths.has(u.pathname) ? u.href : ''; }
    catch (_) { return ''; }
  };
  const response = (url, body, status) => {
    if (status === 401 || status === 403) { post({kind: 'session-expired'}); return; }
    if (body.length <= 2400000) post({kind: 'response', url, body, status});
  };
  const fetchOriginal = window.fetch;
  if (fetchOriginal) window.fetch = async function(...args) {
    const result = await fetchOriginal.apply(this, args);
    const url = target(typeof args[0] === 'string' ? args[0] : args[0]?.url || String(args[0]));
    if (url) result.clone().text().then(body => response(url, body, result.status)).catch(() => {});
    return result;
  };
  const requests = new WeakMap();
  const openOriginal = XMLHttpRequest.prototype.open;
  const sendOriginal = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
    requests.set(this, target(url));
    return openOriginal.call(this, method, url, ...rest);
  };
  XMLHttpRequest.prototype.send = function(...args) {
    const url = requests.get(this);
    if (url) this.addEventListener('load', () => {
      try { response(url, this.responseType === 'json' ? JSON.stringify(this.response) : this.responseText, this.status); } catch (_) {}
    }, {once: true});
    return sendOriginal.apply(this, args);
  };
  let lastNavigation = '', scheduled = false;
  const visible = element => element.getBoundingClientRect().width > 0 && element.getBoundingClientRect().height > 0;
  const navigation = () => {
    scheduled = false;
    const documents = [document];
    for (let i = 0; i < documents.length; i++) for (const frame of documents[i].querySelectorAll('iframe')) {
      try { if (frame.contentWindow.location.origin === location.origin && frame.contentDocument && !documents.includes(frame.contentDocument)) documents.push(frame.contentDocument); } catch (_) {}
    }
    const text = element => (element?.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 60);
    const headings = documents.flatMap(d => [...d.querySelectorAll('.el-breadcrumb__inner, main h1, main h2, .el-card__header, .ant-modal-title, .ant-drawer-title, .ant-breadcrumb')]).filter(visible);
    const tabs = documents.flatMap(d => [...d.querySelectorAll('[role="tab"][aria-selected="true"]')]).filter(visible);
    const title = text(headings.at(-1)) || text(tabs.at(-1)) || document.title || '学校官方教务系统';
    const key = location.href + '\n' + title;
    if (lastNavigation !== key) { lastNavigation = key; post({kind: 'navigation', title}); }
  };
  const scheduleNavigation = () => { if (!scheduled) { scheduled = true; requestAnimationFrame(navigation); } };
  for (const name of ['pushState', 'replaceState']) {
    const original = history[name];
    history[name] = function(...args) { const result = original.apply(this, args); scheduleNavigation(); return result; };
  }
  addEventListener('hashchange', scheduleNavigation);
  addEventListener('popstate', scheduleNavigation);
  addEventListener('DOMContentLoaded', scheduleNavigation);
  new MutationObserver(scheduleNavigation).observe(document, {subtree: true, childList: true, attributes: true, attributeFilter: ['aria-selected']});
  window.__scvtc = {inspectReady: scheduleNavigation};
  scheduleNavigation();
  // Real window.open, form handlers and school validation remain owned by the browser.
})();
