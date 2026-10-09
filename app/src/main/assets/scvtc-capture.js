/* 川职专用：只观察正常查询的副本；不读取密码、不自动提交业务。 */
(() => {
  if (window.__scvtc) return;
  const allowed = new Set(['/jwgr/api/student/studentInfo/querySelf','/jwgr/api/baseInfo/semester/selectCurrentXnXq','/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule']);
  let generation = '', sequence = 0, active = null, readyGeneration = '';
  const documentId=Date.now()+':document:'+Math.random().toString(36).slice(2);
  let observedAccount='',observedTerm='',lastSchedule=null,consumedScheduleSequence=0;
  let navigationMode='';
  let readingServices=false,servicesCancelled=false;
  const listeners = new Set();
  const post = data => { try { window.CampusBridge?.postMessage(JSON.stringify({...data, autoService:readingServices, generation, documentId, page:location.href})); } catch (_) {} };
  const reset = () => { if(active)active.cancelled=true;lastSchedule=null;consumedScheduleSequence=0; generation=Date.now()+':'+Math.random().toString(36).slice(2);post({kind:'hello'}); };
  reset();
  for (const name of ['pushState','replaceState']) {const original=history[name];history[name]=function(){const r=original.apply(this,arguments);reset();return r;};}
  addEventListener('hashchange',reset);addEventListener('popstate',reset);
  const target = url => {try {const u=new URL(url,location.href);return u.origin==='https://jwxt.scvtc.edu.cn'&&allowed.has(u.pathname);}catch(_){return false;}};
  const cleanHeaders = headers => {
    const result={};try{new Headers(headers||{}).forEach((v,k)=>{if(['content-type','authorization','x-requested-with','x-auth-token','token'].includes(k.toLowerCase()))result[k]=v;});}catch(_){}return result;
  };
  const bodyString = body => {if(body==null)return '';if(typeof body==='string')return body;if(body instanceof URLSearchParams)return body.toString();if(body instanceof FormData)return JSON.stringify(Object.fromEntries([...body.entries()].filter(([,v])=>typeof v==='string')));return '';};
  const documents = () => {const result=[document];for(let i=0;i<result.length;i++)for(const f of result[i].querySelectorAll('iframe')){try{const d=f.contentDocument;if(d&&f.contentWindow.location.origin===location.origin&&!result.includes(d))result.push(d);}catch(_){}}return result;};
  const controls = () => {
    const all=documents().flatMap(d=>[...d.querySelectorAll('button,[role=button],input[type=button]')]).filter(e=>(e.innerText||e.value||'').replace(/\s/g,'')==='查询');
    const candidates=[];
    for(const button of all){let p=button.parentElement;for(let depth=0;p&&depth<7;depth++,p=p.parentElement){
      const nums=[...p.querySelectorAll('input[type=number],input[role=spinbutton],.el-input-number input')].filter(e=>e.getAttribute('type')!=='password');
      if(nums.length===1||nums.length===2){candidates.push({button,start:nums[0],end:nums[1]||nums[0],single:nums.length===1,container:p});break;}
    }}
    if(candidates.length!==1)throw Error('周次控件未唯一识别：查询按钮 '+all.length+'，候选范围 '+candidates.length);
    return candidates[0];
  };
  const scope = () => {
    const text=documents().map(d=>d.body?.innerText||'').join(' ');
    const account=text.match(/(?:学号|学生编号)[：:\s]*([0-9]{6,20})/)?.[1]||'';
    const selected=documents().flatMap(d=>[...d.querySelectorAll('select')].map(e=>e.options[e.selectedIndex]?.text||'').concat([...d.querySelectorAll('.el-select .el-input__inner,.el-select__selected-item,input[readonly]')].map(e=>e.value||e.textContent||''))).join(' ');
    const semester=(selected+' '+text).match(/20\d{2}[-—]20\d{2}[-—][12]/)?.[0]||'';
    const teachingWeek=Number(text.match(/(?:当前教学周|本周为|当前为)\s*[：:]?\s*第?\s*(\d{1,2})\s*周/)?.[1]||0);
    return {account:observedAccount||account,semester:observedTerm||semester,teachingWeek};
  };
  const atStart = (url,method,request,headers) => ({url:new URL(url,location.href).href,method:String(method||'GET').toUpperCase(),request,headers,page:location.href,generation,sequence:++sequence,batch:active?.id||'',identity:scope()});
  const requestRange = text => {
    let pairs=[];try {const value=JSON.parse(text);const walk=(o)=>{if(o&&typeof o==='object')for(const [k,v] of Object.entries(o)){if(typeof v==='object')walk(v);else pairs.push([k,String(v)]);}};walk(value);}catch(_){try{pairs=[...new URLSearchParams(text).entries()];}catch(_){}}
    const start=pairs.filter(([k])=>/(start|begin|from|起).*(week|zc|周)|(week|zc|周).*(start|begin|from)/i.test(k));
    const end=pairs.filter(([k])=>/(end|stop|to|止).*(week|zc|周)|(week|zc|周).*(end|stop|to)/i.test(k));
    if(start.length!==1||end.length!==1)return null;
    const a=Number(start[0][1]),b=Number(end[0][1]);
    if(!Number.isInteger(a)||!Number.isInteger(b)||a<1||b<a||b>100)return null;
    // JWGR sends both bounds and the actual requested weeks. Filtered/missing weeks
    // cannot prove complete coverage even when the text fields display a range.
    try{const value=JSON.parse(text);if(Array.isArray(value.weeks)){
      const weeks=new Set(value.weeks.map(Number));if(weeks.size!==b-a+1||Array.from({length:b-a+1},(_,i)=>i+a).some(w=>!weeks.has(w)))return null;
    }}catch(_){}
    return {start:a,end:b,fields:[start[0][0],end[0][0]]};
  };
  const relay = (m,body,status) => {
    if(m.page!==location.href||m.generation!==generation)return;
    if(body.length>2400000){post({kind:'error',error:'响应过大，未截断保存；需要分段查询'});return;}
    const event={...m,kind:'response',body,status,range:requestRange(m.request)};
    let expired=status===401||status===403;
    try{const root=JSON.parse(body);expired=expired||['401','403'].includes(String(root.code));}catch(_){}
    if(expired){post({kind:'session-expired',batch:m.batch});return;}
    if(status>=200&&status<300){try{
      const root=JSON.parse(body);const ok=['200','0'].includes(String(root.code))&&root.success!==false;
      if(event.url.split('?')[0].endsWith('/querySelf')){
        observedAccount='';const ids=new Set();let count=0;
        const walk=(v,depth=0)=>{if(depth>24||++count>10000)throw Error('Identity too large');if(v&&typeof v==='object')for(const [key,value]of Object.entries(v)){if(['studentNumber','studentCode','studentNo','studentId','xh'].includes(key)&&/^[0-9]{6,20}$/.test(String(value)))ids.add(String(value));else if(value&&typeof value==='object')walk(value,depth+1);}};
        if(ok){walk(root);if(ids.size===1)observedAccount=[...ids][0];}
      }
      if(ok&&event.url.split('?')[0].endsWith('/selectCurrentXnXq')&&/^20[0-9]{2}-20[0-9]{2}-[12]$/.test(root.data?.semester||''))observedTerm=root.data.semester;
      if(ok&&event.url.split('?')[0].endsWith('/studentCourseSchedule')&&Array.isArray(root.data))lastSchedule=event;
    }catch(_){}}
    post(event);listeners.forEach(f=>f(event));setTimeout(inspectReady,0);
  };
  const fetchOriginal=window.fetch;
  if(fetchOriginal)window.fetch=function(input,options){
    const url=typeof input==='string'||input instanceof URL?String(input):input.url;
    let m=null,raw=Promise.resolve('');
    if(target(url)){
      m=atStart(url,options?.method||(input instanceof Request?input.method:'GET'),'',cleanHeaders(options?.headers||(input instanceof Request?input.headers:{})));
      try {raw=options?.body!==undefined?Promise.resolve(bodyString(options.body)):input instanceof Request?input.clone().text():Promise.resolve('');} catch(_) {}
    }
    const promise=fetchOriginal.apply(this,arguments);
    // Clone in the first response callback, before the school's caller consumes r.json().
    if(m)promise.then(response=>{
      const copy=response.clone();
      return Promise.all([raw,copy.text()]).then(([request,body])=>{m.request=request;relay(m,body,response.status);});
    }).catch(()=>{});
    return promise;
  };
  const meta=new WeakMap();const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send,setHeader=XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open=function(method,url){meta.set(this,{url,method,headers:{}});return open.apply(this,arguments);};
  XMLHttpRequest.prototype.setRequestHeader=function(k,v){const m=meta.get(this);if(m&&['content-type','authorization','x-requested-with','x-auth-token','token'].includes(String(k).toLowerCase()))m.headers[k]=v;return setHeader.apply(this,arguments);};
  XMLHttpRequest.prototype.send=function(request){const initial=meta.get(this);if(initial&&target(initial.url)){
    const m=atStart(initial.url,initial.method,bodyString(request),initial.headers);
    this.addEventListener('load',()=>{try{const body=this.responseType==='json'?JSON.stringify(this.response):!this.responseType||this.responseType==='text'?this.responseText:'';relay(m,body,this.status);}catch(_){}},{once:true});
    this.addEventListener('error',()=>post({kind:'error',error:'目标查询网络失败',batch:m.batch}),{once:true});
  }return send.apply(this,arguments);};
  const snapshot = extra => {
    const docs=documents();const frames=[];
    for(const frame of document.querySelectorAll('iframe')){try{if(frame.contentDocument&&frame.contentWindow.location.origin===location.origin){frames.push({accessible:true});}else frames.push({accessible:false});}catch(_){frames.push({accessible:false});}}
    const clones=docs.map(doc=>{const clone=doc.documentElement.cloneNode(true);clone.querySelectorAll('input,textarea,script,style,svg').forEach(e=>e.remove());clone.querySelectorAll('img[src^="data:"]').forEach(e=>e.removeAttribute('src'));return clone.outerHTML;});
    const courseNodes=docs.reduce((n,d)=>n+d.querySelectorAll('[class*=courseBox],[data-course-name]').length,0);
    const emptySchedule=courseNodes===0&&docs.some(d=>[...d.querySelectorAll('table')].some(table=>{
      const headers=[...(table.querySelector('thead')||table.rows[0]||table).querySelectorAll('th,td')].map(e=>e.textContent.trim());
      const days=new Set(headers.filter(t=>/星期[一二三四五六日天]|周[一二三四五六日天]/.test(t)));
      const cells=[...table.querySelectorAll('tbody td')];
      return days.size>=5&&table.querySelectorAll('tbody tr').length>0&&cells.length>0&&
        cells.every(cell=>{const t=cell.textContent.replace(/\s/g,'');return !t||t==='-'||/^第?\d+(?:[-,，]\d+)*节-?$/.test(t);});
    }));
    const html=clones.join('\n');if(html.length>2400000)throw Error('页面过大，需分段查询；未截断保存');
    post({kind:'dom',html,login:!!document.querySelector('input[type=password]'),courseNodes,emptySchedule,frames,identity:scope(),...extra});
  };
  const setValue=(element,value)=>{const w=element.ownerDocument.defaultView;const setter=Object.getOwnPropertyDescriptor(w.HTMLInputElement.prototype,'value').set;setter.call(element,String(value));element.dispatchEvent(new w.Event('input',{bubbles:true}));element.dispatchEvent(new w.Event('change',{bubbles:true}));};
  const check=task=>{if(task.cancelled||task!==active||task.generation!==generation)throw Error('任务已取消或页面已切换');};
  // A real response or a completed render cycle can unlock DOM import.
  // Unknown request range fields never authorize replacing an entire cached range.
  const waitQuery=(task,start,end)=>{
    const minSequence=sequence+1;
    let resolve,reject,observer,timer,stable,busySeen=false,changed=false,done=false;
    const promise=new Promise((a,b)=>{resolve=a;reject=b;});
    const isBusy=()=>documents().flatMap(d=>[...d.querySelectorAll('.el-loading-mask,.ant-spin-spinning,.ajax-loading,[aria-busy=true]')]).some(e=>{const s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden'&&e.getBoundingClientRect().height>0;});
    const finish=(error,evidence)=>{if(done)return;done=true;clearTimeout(timer);clearTimeout(stable);observer?.disconnect();listeners.delete(listener);error?reject(error):resolve(evidence);};
    const listener=event=>{
      if(event.batch!==task.id||event.sequence<minSequence||!event.url.includes('/studentCourseSchedule'))return;
      if(event.range&&(event.range.start!==start||event.range.end!==end))return;
      let valid=event.status>=200&&event.status<300;
      try{const root=JSON.parse(event.body);valid=valid&&['200','0'].includes(String(root.code))&&root.success!==false;}catch(_){valid=false;}
      finish(valid?null:Error('目标查询未成功，原课程保留'),event.range?'request-range':'response');
    };
    listeners.add(listener);
    const tables=documents().flatMap(d=>[...d.querySelectorAll('table')]).filter(t=>/星期[一二三四五六日天]|周[一二三四五六日天]/.test(t.textContent));
    observer=new MutationObserver(records=>{
      if(isBusy())busySeen=true;
      if(records.some(r=>tables.some(t=>t.contains(r.target))))changed=true;
      clearTimeout(stable);
      if(!isBusy()&&(busySeen||changed))stable=setTimeout(()=>finish(null,'render-cycle'),500);
    });
    documents().forEach(d=>{if(d.body)observer.observe(d.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['style','class','aria-busy']});});
    timer=setTimeout(()=>finish(Error('未观察到本次查询的响应或课表更新，请刷新后重新同步')),25000);
    return {promise,cancel:()=>finish(Error('任务已取消'))};
  };
  const settled = task => new Promise((resolve,reject)=>{
    let stable;const finish=()=>{observer.disconnect();clearTimeout(deadline);clearTimeout(stable);try{check(task);resolve();}catch(e){reject(e);}};
    const schedule=()=>{clearTimeout(stable);stable=setTimeout(()=>{const loading=documents().flatMap(d=>[...d.querySelectorAll('.el-loading-mask,.ant-spin-spinning,.ajax-loading,[aria-busy=true]')]).some(e=>{const style=getComputedStyle(e);return style.display!=='none'&&style.visibility!=='hidden'&&e.getBoundingClientRect().height>0;});if(loading){schedule();return;}finish();},500);};
    const observer=new MutationObserver(schedule);observer.observe(document.body,{childList:true,subtree:true,characterData:true});const deadline=setTimeout(()=>{observer.disconnect();clearTimeout(stable);reject(Error('课表尚未加载完成'));},16000);schedule();
  });
  const homeSchedule=()=>location.hash.split('?')[0].endsWith('/student/index')&&lastSchedule?.generation===generation&&observedAccount&&observedTerm;
  const inspectReady=()=>{if(document.querySelector('input[type=password]')){post({kind:'auth'});return;}if(readyGeneration===generation)return;
    if(homeSchedule()){readyGeneration=generation;post({kind:'ready',identity:scope()});return;}
    if(!location.hash.includes('studentSchedule'))return;try{controls();readyGeneration=generation;post({kind:'ready',identity:scope()});}catch(_){}};

  let resolvedModule='',resolvedPage='';
  const labelsMatch=(element,labels)=>labels.includes((element.innerText||element.textContent||'').replace(/\s+/g,''));
  const visible=e=>{const r=e.getBoundingClientRect(),style=getComputedStyle(e);return r.width>0&&r.height>0&&style.display!=='none'&&style.visibility!=='hidden';};
  const openModule=async(id,labels)=>{
    resolvedModule='';labels=labels.map(s=>s.replace(/\s+/g,''));
    if(id==='credits'&&location.hash.split('?')[0].endsWith('/student/index')&&observedAccount&&observedTerm&&documents().some(d=>/已完成毕业学分\s*\d+(?:\.\d+)?\s*分/.test(d.body?.innerText||'')&&/未完成毕业学分\s*\d+(?:\.\d+)?\s*分/.test(d.body?.innerText||''))){resolvedModule=id;resolvedPage=location.href;post({kind:'module',module:id});return;}
    // The observed school homepage exposes read-only services under the actual '我的查询' tab.
    const tab=documents().flatMap(d=>[...d.querySelectorAll('[class*=service-index-serivceContainer] [role=tab]')]).find(e=>e.innerText.trim()==='我的查询');
    if(tab&&tab.getAttribute('aria-selected')!=='true'){tab.click();await new Promise(r=>setTimeout(r,350));}
    const menus=documents().flatMap(d=>[...d.querySelectorAll('.el-menu-item,.ant-menu-item,[role=menuitem],nav a,aside a,a[href]')]).filter(e=>visible(e)&&labelsMatch(e,labels));
    const headings=documents().flatMap(d=>[...d.querySelectorAll('h1,h2,h3,.el-card__header,.el-breadcrumb__inner')]).filter(e=>visible(e)&&labelsMatch(e,labels));
    if(headings.length>0){resolvedModule=id;resolvedPage=location.href;post({kind:'module',module:id});return;}
    if(menus.length!==1){post({kind:'error',error:'请在官方菜单选择目标服务，未找到唯一实际入口'});return;}
    const before=location.href;
    // The live JWGR menu opens read-only pages using window.open. Capture only
    // the synchronous URL produced by this requested menu item, then navigate
    // within the same trusted SPA document. No global popup permission change.
    const originalOpen=window.open;let opened='';
    window.open=function(url){try{const u=new URL(url,location.href);if(u.origin===location.origin&&u.pathname==='/jwgr/'&&u.hash.startsWith('#/jwxt/js/student/')){opened=u.href;return null;}}catch(_){}return null;};
    try{menus[0].click();}finally{window.open=originalOpen;}
    if(opened){const u=new URL(opened);if(u.search!==location.search)throw Error('服务入口改变了认证查询，已停止自动导航');location.hash=u.hash;}
    for(let i=0;i<40;i++){await new Promise(r=>setTimeout(r,150));if(location.href!==before ||documents().some(d=>[...d.querySelectorAll('h1,h2,h3,.el-card__header,.el-breadcrumb__inner')].some(e=>visible(e)&&labelsMatch(e,labels)))){resolvedModule=id;resolvedPage=location.href;post({kind:'module',module:id});return;}}
    post({kind:'error',error:'官方服务页面尚未打开，未导入主页内容'});
  };
  const readModule=async(id)=>{
    if(resolvedModule!==id||resolvedPage!==location.href){post({kind:'error',error:'请先在官方菜单进入此服务'});return false;}
    if(active)return false;const task={root:Date.now()+':service:'+Math.random().toString(36).slice(2),id:'',generation,cancelled:false};active=task;
    try{
      await settled(task);check(task);
      if(document.querySelector('input[type=password]'))throw Error('学校要求重新认证，原记录保留');
      let previous='';
      for(let i=0;i<50;i++){
        check(task);const fingerprint=documents().map(d=>[...d.querySelectorAll('table tbody')].map(e=>e.innerText).join('|')).join('|');
        if(i>0 && fingerprint===previous)throw Error('分页内容未变化，已停止，保留已读记录');previous=fingerprint;
        task.id=task.root+':'+i;
        const next=documents().flatMap(d=>[...d.querySelectorAll('.el-pagination .btn-next,.ant-pagination-next,[aria-label="下一页"],[title="下一页"]')]).find(e=>visible(e)&&!e.disabled&&!e.classList.contains('disabled')&&!e.classList.contains('ant-pagination-disabled')&&e.getAttribute('aria-disabled')!=='true');
        if(i===49&&next)throw Error('超过50页，未提交不完整服务快照');
        const saved=new Promise((resolve,reject)=>{task.ack={resolve,reject};task.ackTimer=setTimeout(()=>reject(Error('本地保存未确认')),20000);});
        snapshot({module:id,batch:task.id,queried:true,evidence:'official-module',completed:i+1,total:50,serviceFirst:i===0,serviceRun:task.root,servicePage:i+1,serviceHasNext:!!next});await saved;
        if(!next)break;next.click();await new Promise((resolve,reject)=>{let tries=0;const poll=()=>{try{check(task);}catch(e){reject(e);return;}const now=documents().map(d=>[...d.querySelectorAll('table tbody')].map(e=>e.innerText).join('|')).join('|');if(now!==fingerprint){resolve();return;}if(++tries>80){reject(Error('分页查询超时'));return;}setTimeout(poll,150);};poll();});await settled(task);
      }
      post({kind:'progress',stage:'服务读取完成',module:id});
      return true;
    }catch(e){post({kind:'error',error:e.message});return false;}finally{if(active===task)active=null;}
  };
  window.__scvtc={
    openModule,readModule,
    readServices:async modules=>{
      if(readingServices)return;
      const readonly=new Set(['grades','credits','distribution','classSchedule','curriculum','courses','attendance','exams','levelExamResults','statusWarning','studentInfo','calendar','rooms','venue','notices']);
      readingServices=true;servicesCancelled=false;let completed=0,unavailable=0;
      try{
        for(const [id,labels]of modules){
          if(servicesCancelled)throw Error('任务已取消');
          if(!readonly.has(id))continue;
          if(document.querySelector('input[type=password]'))throw Error('AUTH_REQUIRED：学校需要本人认证');
          post({kind:'progress',stage:'自动整理服务',module:id});
          location.hash='#/jwxt/js/student/index';
          for(let i=0;i<60;i++){if(documents().some(d=>[...d.querySelectorAll('[role=tab]')].some(e=>visible(e)&&e.innerText.trim()==='我的查询')))break;await new Promise(r=>setTimeout(r,100));}
          await openModule(id,labels);
          if(resolvedModule!==id){unavailable++;post({kind:'service-status',module:id,state:'unavailable'});continue;}
          // Completion is emitted only after every page's native commit ACK.
          const saved=await readModule(id);
          if(saved)completed++;else unavailable++;
          post({kind:'service-status',module:id,state:saved?'saved':'unavailable'});
        }
        post({kind:'service-summary',completed,unavailable});
      }catch(e){post({kind:'service-summary',completed,unavailable,error:e.message});}
      finally{readingServices=false;}
    },
    inspectReady,
    snapshot:()=>{try{snapshot({});}catch(e){post({kind:'error',error:e.message});}},
    cancel:()=>{if(readingServices)servicesCancelled=true;if(active){active.cancelled=true;active.query?.cancel();clearTimeout(active.ackTimer);active.ack?.reject(Error('任务已取消'));}active=null;post({kind:'progress',stage:'已取消'});},
    sync:async(mode='range')=>{
      mode=navigationMode||mode;navigationMode='';
      if(active&&!active.cancelled)return;const task={root:Date.now()+':batch:'+Math.random().toString(36).slice(2),id:'',generation,cancelled:false};active=task;
      try{
        if(document.querySelector('input[type=password]'))throw Error('当前仍在登录页');
        if(homeSchedule()){
          // The real school's '查看学期课表' opens a new window. Keep this known
          // read-only SPA route in the same document so SSO and verified identity
          // survive; do not enable arbitrary popups or submit any write service.
          const entry=documents().flatMap(d=>[...d.querySelectorAll('a,button,[role=button],span')]).filter(e=>visible(e)&&(e.textContent||'').replace(/\s/g,'')==='查看学期课表');
          if(entry.length===1){navigationMode=mode;post({kind:'progress',stage:'进入学期课表'});location.hash='#/jwxt/js/student/personal/studentSchedule';return;}
          // Re-envelope a fresh, actual page response. Its unknown request bounds cannot authorize replacement.
          const observed=lastSchedule;if(observed.sequence<=consumedScheduleSequence){location.reload();return;}
          consumedScheduleSequence=observed.sequence;task.id=task.root+':observed';
          post({...observed,sequence:++sequence,batch:task.id,identity:scope()});await settled(task);check(task);
          const saved=new Promise((resolve,reject)=>{task.ack={resolve,reject};task.ackTimer=setTimeout(()=>reject(Error('本地保存未确认')),20000);});
          snapshot({batch:task.id,range:observed.range||{},queried:true,evidence:'response',completed:1,total:1,partial:true});await saved;
          post({kind:'progress',stage:'当前页读取完成',batch:task.id,completed:1,total:1,partial:true});return;
        }
        const c=controls();const upper=Number(c.end.max)||Number(c.end.value)||28;const end=Math.min(100,upper);if(!Number.isInteger(end)||end<1)throw Error('学校总周数未确认');const ranges=mode==='weekly'||c.single?Array.from({length:end},(_,i)=>[i+1,i+1]):[[1,end]];
        for(let i=0;i<ranges.length;i++){
          check(task);const [a,b]=ranges[i];post({kind:'progress',stage:'查询中',batch:task.id,start:a,end:b,completed:i,total:ranges.length});
          task.id=task.root+':'+i;setValue(c.start,a);if(!c.single)setValue(c.end,b);
          await new Promise(resolve=>{let v=c.container;while(v&&!v.__vue__)v=v.parentElement;if(v?.__vue__?.$nextTick)v.__vue__.$nextTick(resolve);else setTimeout(resolve,0);});check(task);
          if(Number(c.start.value)!==a||(!c.single&&Number(c.end.value)!==b))throw Error('页面未接受周次值');
          const query=waitQuery(task,a,b);task.query=query;c.button.click();const evidence=await query.promise;task.query=null;check(task);await settled(task);check(task);
          // Register the acknowledgement before publishing: a fast Room commit must not be lost.
          const saved=new Promise((resolve,reject)=>{task.ack={resolve,reject};task.ackTimer=setTimeout(()=>reject(Error('数据桥或数据库保存未确认')),20000);});
          snapshot({batch:task.id,range:{start:a,end:b},queried:true,evidence,completed:i+1,total:ranges.length});
          await saved;
        }
        post({kind:'progress',stage:'完成',batch:task.id,completed:ranges.length,total:ranges.length});
      }catch(e){if(!task.cancelled&&task.generation===generation)post({kind:'error',batch:task.id,error:e.message});}finally{if(active===task)active=null;}
    },
    acknowledge:(batch,ok,error)=>{if(active?.id!==batch||!active.ack)return;clearTimeout(active.ackTimer);const ack=active.ack;active.ack=null;if(ok)ack.resolve();else ack.reject(Error(error||'保存失败'));}
  };
let readyTimer;const readyObserver=new MutationObserver(()=>{clearTimeout(readyTimer);readyTimer=setTimeout(inspectReady,400);});readyObserver.observe(document,{subtree:true,childList:true});addEventListener('hashchange',()=>setTimeout(inspectReady,400));addEventListener('DOMContentLoaded',inspectReady);setTimeout(inspectReady,400);
})();
