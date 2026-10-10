package com.tyust.course.scvtc

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.*
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.webkit.*
import cn.scvtc.campus.core.*
import com.tyust.course.manager.UserManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Application-owned official browser; native HTTP owns all student-data reads. */
object ScvtcWebSession {
 var foreground=WeakReference<FragmentActivity>(null)
 var web:WebView?=null;private set
 var controller:ScvtcCapture?=null;private set
 val page=kotlinx.coroutines.flow.MutableStateFlow(OfficialWebPage())
 var fileChooser:((ValueCallback<Array<Uri>>,WebChromeClient.FileChooserParams)->Boolean)?=null
 private var popup:android.app.Dialog?=null
 private var popupWeb:WebView?=null
 private var captureScript=""

 @SuppressLint("SetJavaScriptEnabled")
 fun obtain(activity:FragmentActivity,initialUrl:String?=null):WebView {
  web?.let {
   if(controller?.visible!=true)(it.context as MutableContextWrapper).baseContext=activity
   if(initialUrl!=null&&initialUrl!=it.url)navigate(initialUrl)
   return it
  }
  val view=WebView(MutableContextWrapper(activity));web=view
  val c=ScvtcCapture(view);controller=c
  captureScript=activity.assets.open("scvtc-capture.js").bufferedReader().use{it.readText()}
  configure(view,c)
  view.loadUrl(initialUrl?.takeIf(OfficialWebNavigation::trusted)?:School.HOME)
  return view
 }
 fun observedPage(view:WebView,url:String,title:String=""){
  if(view.url!=url||!OfficialWebNavigation.trusted(url))return
  page.value=OfficialWebPage(OfficialWebNavigation.title(url,title),url)
 }
 @SuppressLint("SetJavaScriptEnabled")
 private fun configure(view:WebView,capture:ScvtcCapture?){
  view.settings.apply{
   javaScriptEnabled=true;domStorageEnabled=true
   setSupportMultipleWindows(true);javaScriptCanOpenWindowsAutomatically=false
   setSupportZoom(true);builtInZoomControls=true;displayZoomControls=false
   useWideViewPort=true;loadWithOverviewMode=true
   mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
   allowFileAccess=false;allowContentAccess=false
  }
  CookieManager.getInstance().setAcceptCookie(true)
  CookieManager.getInstance().setAcceptThirdPartyCookies(view,true)
  val login=cn.scvtc.campus.OfficialLoginMemory(ScvtcRuntime.context)
  capture?.loginMemory=login
  login.install(view){capture?.authenticationAccount?.ifBlank { ScvtcRuntime.account } ?: ScvtcRuntime.account}
  val messages=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
  val early=messages&&WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
  if(messages)WebViewCompat.addWebMessageListener(view,"CampusBridge",setOf(School.ORIGIN)){_,message,origin,main,_ ->
   if(main&&origin.toString().trimEnd('/')==School.ORIGIN){
    val text=message.data.orEmpty()
    if(capture!=null)capture.receive(text)
    else runCatching{JSONObject(text)}.getOrNull()?.takeIf{it.optString("kind")=="navigation"}?.let{observedPage(view,it.optString("page"),it.optString("title"))}
   }
  }
  if(early)WebViewCompat.addDocumentStartJavaScript(view,captureScript,setOf(School.ORIGIN))
  view.webViewClient=object:WebViewClient(){
   override fun onPageStarted(v:WebView,url:String?,icon:android.graphics.Bitmap?){
    capture?.resetDocument()
    page.value=OfficialWebPage(OfficialWebNavigation.title(url.orEmpty()),url.orEmpty(),loading=true)
   }
   override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean{
    val url=r.url.toString()
    if(url=="about:blank"||OfficialWebNavigation.trusted(url))return false
    if(r.isForMainFrame){external(v,url);if(v===popupWeb)popup?.dismiss()}
    return true
   }
   override fun onPageFinished(v:WebView,url:String){
    observedPage(v,url,v.title.orEmpty())
    login.prepare(v)
    val u=Uri.parse(url)
    if(u.scheme=="https"&&u.host=="www.shulin-soft.com"&&u.port==8267&&u.path=="/casLogin.html"){
     v.loadUrl(cn.scvtc.campus.CasAuthManager.JWXT_SSO_ENTRY);return
    }
    val authenticationAccount=capture?.authenticationAccount?.ifBlank { ScvtcRuntime.account } ?: ScvtcRuntime.account
    if(login.recoveryNavigation(v,authenticationAccount,url))return
    if(u.host=="jwxt.scvtc.edu.cn"){
     if(messages&&!early)v.evaluateJavascript(captureScript,null)
     v.evaluateJavascript("window.__scvtc?.inspectReady()",null)
    }else if(u.host=="cas.scvtc.edu.cn"&&authenticationAccount.isNotBlank()){
     login.recover(v,authenticationAccount){restored->if(!restored&&v.url==url)page.value=page.value.copy(error="请在学校官方页面完成补充认证")}
    }
    CookieManager.getInstance().flush()
   }
   override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){
    if(r.isForMainFrame){login.failedNetwork(ScvtcRuntime.account);page.value=page.value.copy(loading=false,error="官方页面连接失败（${e.errorCode}）；可刷新或用系统浏览器打开")}
   }
   override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,response:WebResourceResponse){
    if(r.isForMainFrame&&response.statusCode>=400)page.value=page.value.copy(loading=false,error="官方页面返回 HTTP ${response.statusCode}")
   }
  }
  view.webChromeClient=object:WebChromeClient(){
   override fun onReceivedTitle(v:WebView,title:String?){observedPage(v,v.url.orEmpty(),title.orEmpty())}
   override fun onCreateWindow(v:WebView,isDialog:Boolean,isUserGesture:Boolean,resultMsg:android.os.Message):Boolean{
    val activity=(v.context as? MutableContextWrapper)?.baseContext as? FragmentActivity?:return false
    if(!isUserGesture)return false
    popup?.dismiss()
    val child=WebView(MutableContextWrapper(activity));popupWeb=child
    configure(child,null)
    val dialog=android.app.Dialog(activity);popup=dialog
    val layout=android.widget.LinearLayout(activity).apply{orientation=android.widget.LinearLayout.VERTICAL}
    layout.addView(android.widget.Button(activity).apply{text="关闭官网新窗口";setOnClickListener{dialog.dismiss()}})
    layout.addView(child,android.widget.LinearLayout.LayoutParams(-1,0,1f))
    dialog.setContentView(layout)
    dialog.setOnDismissListener{
     child.stopLoading();child.destroy()
     if(popup===dialog){popup=null;popupWeb=null;web?.let{observedPage(it,it.url.orEmpty(),it.title.orEmpty())}}
    }
    dialog.show();dialog.window?.setLayout(-1,-1)
    (resultMsg.obj as WebView.WebViewTransport).webView=child
    resultMsg.sendToTarget()
    return true
   }
   override fun onCloseWindow(window:WebView){if(window===popupWeb)popup?.dismiss()}
   override fun onShowFileChooser(v:WebView,callback:ValueCallback<Array<Uri>>,parameters:FileChooserParams):Boolean =
    fileChooser?.invoke(callback,parameters)?:false
  }
 }
 fun navigate(url:String){
  val view=web?:return
  if(OfficialWebNavigation.trusted(url))view.loadUrl(url)else external(view,url)
 }
 fun external(view:WebView,url:String){runCatching{view.context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{page.value=page.value.copy(error="未能打开目标服务：系统没有可用的浏览器或应用")}}
 fun attachVisible(activity:FragmentActivity,container:FrameLayout){
  val w=obtain(activity);(w.context as MutableContextWrapper).baseContext=activity
  (w.parent as? android.view.ViewGroup)?.removeView(w)
  container.addView(w,FrameLayout.LayoutParams(-1,-1));controller?.visible=true;foreground=WeakReference(activity);w.onResume()
 }
 fun detachVisible(activity:FragmentActivity){
  if((web?.context as? MutableContextWrapper)?.baseContext!==activity)return
  popup?.dismiss();fileChooser=null;foreground=WeakReference(null);controller?.visible=false
  web?.let{it.onPause();(it.parent as? android.view.ViewGroup)?.removeView(it);(it.context as MutableContextWrapper).baseContext=ScvtcRuntime.context}
  ScvtcSyncWork.enqueue(ScvtcRuntime.context)
 }
 fun close(){popup?.dismiss();controller?.dispose();controller=null;web?.let{(it.parent as? android.view.ViewGroup)?.removeView(it);it.destroy()};web=null;page.value=OfficialWebPage()}
}
/** Observes authenticated JSON identity and semester responses only. WebView
 * establishes SSO; ScvtcRuntime performs every data read through native HTTP. */
class ScvtcCapture(private val web:WebView){
 var loginMemory:cn.scvtc.campus.OfficialLoginMemory?=null
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val messages=Channel<String>(32)
 var visible=true
 var authenticationAccount=""
 val authenticationVerified=kotlinx.coroutines.flow.MutableStateFlow(false)
 var module="schedule"
 var detectedAccount="";var detectedTerm="";var detectedName=""
 private var verifiedAccount=""
 private var documentId=""
 private var officialMonday:OfficialTerm?=null
 var onScopeRequired:(()->Unit)?=null
 private var generation=""
 private var startedGeneration=""
 private var authenticationRetries=0
 fun begin(resetAuthentication:Boolean=true){if(resetAuthentication){authenticationRetries=0;authenticationVerified.value=false};startedGeneration=""}
 init{scope.launch{for(text in messages){try{consume(JSONObject(text))}catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message?:"身份确认未完成")}}}}
 fun receive(text:String){if(text.length>2600000||messages.trySend(text).isFailure)fail("学校页面消息未完成，请重新连接")}
 fun dispose(){scope.cancel();messages.close()}
 fun resetDocument(){verifiedAccount="";documentId="";officialMonday=null;generation="";detectedAccount="";detectedTerm=""}
 fun stop(){startedGeneration=generation}
 fun fail(message:String){ScvtcRuntime.status.value="${message.take(180)}；已保留离线课表"}
 fun start(mode:String="range",resetAuthentication:Boolean=true){
  require(mode in setOf("range","weekly"));begin(resetAuthentication);startedGeneration=generation
  ScvtcRuntime.startNativeSync(mode)
 }
 private fun recoverSession(){
  if(ScvtcRuntime.account.isBlank()){ScvtcRuntime.status.value="请完成一次官方登录，身份确认后会自动读取数据";return}
  if(authenticationRetries>=1){fail("自动恢复未完成，请在官方页补充认证");return}
  authenticationRetries++
  ScvtcRuntime.status.value="正在恢复学校会话并继续同步；离线课表保留"
  web.loadUrl(cn.scvtc.campus.CasAuthManager.JWXT_SSO_ENTRY)
 }
 private suspend fun startVerifiedSync(){
  val calendar=officialMonday?:return
  if(verifiedAccount.isBlank()||generation.isBlank()||startedGeneration==generation)return
  require(authenticationAccount.isBlank()||authenticationAccount==verifiedAccount){"请使用本次输入的学校账号完成认证"}
  require(ScvtcRuntime.account.isBlank()||ScvtcRuntime.account==verifiedAccount||authenticationAccount==verifiedAccount){"官方账号与当前缓存账号不同，请确认学校账号"}
  val memory=loginMemory?:return
  val api=cn.scvtc.campus.JwxtApi(headers={memory.apiHeaders(verifiedAccount,it)})
  val student=api.student(verifiedAccount)
  require(api.calendar().first.semester==calendar.semester){"学校学期已改变，请重新连接"}
  ScvtcRuntime.confirm(verifiedAccount,calendar.semester)
  ScvtcRuntime.registerVerifiedIdentity(verifiedAccount,calendar.semester,
      listOf("studentName","name","xm","姓名").firstNotNullOfOrNull{student.fields[it]?.takeIf(String::isNotBlank)}.orEmpty().ifBlank{detectedName})
  loginMemory?.confirmed(verifiedAccount,promoteEnrollment=true)
  start(resetAuthentication=false)
  authenticationVerified.value=true
 }
 private suspend fun consume(m:JSONObject){
  val page=m.optString("page")
  if(Uri.parse(page).scheme!="https"||Uri.parse(page).host!="jwxt.scvtc.edu.cn"||web.url!=page)return
  val kind=m.optString("kind");val epoch=m.optString("generation")
  if(kind=="navigation"){ScvtcWebSession.observedPage(web,page,m.optString("title"));return}
  if(kind=="hello"){
   val doc=m.optString("documentId");if(doc.isBlank()||doc!=documentId){verifiedAccount="";officialMonday=null;detectedName=""}
   documentId=doc;generation=epoch;return
  }
  if(epoch!=generation||generation.isBlank())return
  when(kind){
   "session-expired","auth"->recoverSession()
   "ready"->startVerifiedSync()
   "response"->{
    val url=m.optString("url").substringBefore('?')
    if(url !in setOf(cn.scvtc.campus.JwxtApi.IDENTITY,cn.scvtc.campus.JwxtApi.TERM))return
    if(m.optInt("status") !in 200..299){if(m.optInt("status") in setOf(401,403))recoverSession()else fail("学校身份接口暂不可用");return}
    if(url==cn.scvtc.campus.JwxtApi.IDENTITY){
     verifiedAccount=OfficialIdentity.account(m.optString("body"));detectedAccount=verifiedAccount
     val rows=JSONObject(m.optString("body")).optJSONObject("data")?.optJSONArray("rows")
     val student=rows?.takeIf{it.length()==1}?.optJSONObject(0)
     detectedName=sequenceOf("studentName","name","xm").map{student?.optString(it).orEmpty()}.firstOrNull{it.isNotBlank()}.orEmpty()
    }else{
     officialMonday=OfficialSemester.parse(m.optString("body"));detectedTerm=officialMonday?.semester.orEmpty()
    }
    startVerifiedSync()
   }
  }
 }
}
