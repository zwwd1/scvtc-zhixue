/* Authentication bootstrap only: observed JSON request headers, never HTML/student UI data. */
(() => {
  if (window !== top || location.origin !== 'https://jwxt.scvtc.edu.cn' || window.__jwxtHeaderObserver) return;
  window.__jwxtHeaderObserver = true;
  const documentId=crypto.randomUUID();
  const identity='/jwgr/api/student/studentInfo/querySelf';
  const paths=new Set([identity,'/jwgr/api/baseInfo/semester/selectCurrentXnXq','/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule']);
  const target=url=>{try{const u=new URL(url,location.href);return u.origin===location.origin&&paths.has(u.pathname)?u:null;}catch(_){return null;}};
  const headers=value=>{const safe={};try{new Headers(value||{}).forEach((v,k)=>{if(['permission','authorization','x-auth-token','token'].includes(k.toLowerCase())&&v.length<=8192)safe[k]=v;});}catch(_){}return safe;};
  const send=(page,url,head,status,body='')=>{
    if(status<200||status>=300)return;
    if(page!==location.href)return;
    try{window.JwxtHeaderBridge?.postMessage(JSON.stringify({page,documentId,url,headers:head,identityBody:body}));}catch(_){}
  };
  const originalFetch=window.fetch;
  window.fetch=function(input,options){
    const page=location.href,u=target(input instanceof Request?input.url:input),head=headers(options?.headers||(input instanceof Request?input.headers:{}));
    return originalFetch.apply(this,arguments).then(response=>{
      if(u){if(u.pathname===identity){const clone=response.clone();clone.text().then(body=>send(page,u.href,head,response.status,body)).catch(()=>{});}else send(page,u.href,head,response.status);}
      return response;
    });
  };
  const open=XMLHttpRequest.prototype.open,set=XMLHttpRequest.prototype.setRequestHeader,sendRequest=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open=function(method,url){this.__jwxtObserved={page:location.href,url:target(url),headers:{}};return open.apply(this,arguments);};
  XMLHttpRequest.prototype.setRequestHeader=function(k,v){if(this.__jwxtObserved){Object.assign(this.__jwxtObserved.headers,headers({[k]:v}));}return set.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(){const m=this.__jwxtObserved;if(m?.url)this.addEventListener('load',()=>{
    try{send(m.page,m.url.href,m.headers,this.status,m.url.pathname===identity?(this.responseType==='json'?JSON.stringify(this.response):this.responseText):'');}catch(_){}
  },{once:true});return sendRequest.apply(this,arguments);};
})();
