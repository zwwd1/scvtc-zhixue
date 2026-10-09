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

/** Application-owned session; persistent work can restore SSO without an Activity. */
object ScvtcWebSession {
 var foreground=WeakReference<FragmentActivity>(null)
 var web:WebView?=null;private set
 var controller:ScvtcCapture?=null;private set
 private var hidden:FrameLayout?=null
 @SuppressLint("SetJavaScriptEnabled")
 fun obtain(activity:FragmentActivity):WebView = obtainContext(activity)
 private fun obtainContext(context:Context):WebView {
  web?.let{if(controller?.visible!=true)(it.context as MutableContextWrapper).baseContext=context;return it}
  val view=WebView(MutableContextWrapper(context));web=view
  view.settings.javaScriptEnabled=true;view.settings.domStorageEnabled=true;view.settings.setSupportMultipleWindows(false)
  view.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(view,true)
  val c=ScvtcCapture(view);c.visible=context is FragmentActivity;controller=c
  val login=cn.scvtc.campus.OfficialLoginMemory(ScvtcRuntime.context);c.loginMemory=login;login.install(view){ScvtcRuntime.account}
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){ScvtcRuntime.status.value="系统 WebView 需要更新才能读取课表";return view}
  WebViewCompat.addWebMessageListener(view,"CampusBridge",setOf(School.ORIGIN)){_,message,origin,main,_ ->if(main && origin.toString().trimEnd('/')==School.ORIGIN)c.receive(message.data.orEmpty())}
  val script=context.assets.open("scvtc-capture.js").bufferedReader().use{it.readText()}
  val early=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
  if(early)WebViewCompat.addDocumentStartJavaScript(view,script,setOf(School.ORIGIN))
  view.webViewClient=object:WebViewClient(){
   override fun onPageStarted(v:WebView,url:String?,icon:android.graphics.Bitmap?){c.resetDocument()}
   override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {
    if(r.url.scheme in setOf("https","http"))return false
    runCatching{v.context.startActivity(Intent(Intent.ACTION_VIEW,r.url))};return true
   }
   override fun onPageFinished(v:WebView,url:String){
    login.prepare(v)
    val callback=Uri.parse(url)
    if(callback.scheme=="https"&&callback.host=="www.shulin-soft.com"&&callback.port==8267&&callback.path=="/casLogin.html"){
      v.loadUrl(cn.scvtc.campus.CasAuthManager.JWXT_SSO_ENTRY);return
    }
    if(login.recoveryNavigation(v,ScvtcRuntime.account,url))return
    if(Uri.parse(url).host=="jwxt.scvtc.edu.cn"){if(!early)v.evaluateJavascript(script,null);if(c.module=="schedule")v.evaluateJavascript("window.__scvtc?.inspectReady()",null)
      else {val labels=org.json.JSONArray(listOf(School.service(c.module)?.title.orEmpty())+School.service(c.module)?.aliases.orEmpty());v.evaluateJavascript("window.__scvtc?.openModule(${JSONObject.quote(c.module)},$labels)",null)}}
    else if(Uri.parse(url).host=="cas.scvtc.edu.cn")v.postDelayed({
      if(v.url==url)v.evaluateJavascript("!!document.querySelector('input[type=password]')") { result ->
        if(result=="true")login.recover(v,ScvtcRuntime.account){restored->if(!restored){if(ScvtcRuntime.account.isBlank())ScvtcRuntime.status.value="请完成一次官方登录，身份确认后会自动读取数据"else c.fail("学校需要补充认证，请在官方页面完成；离线课表保留")}}
      }
    },2500)
    CookieManager.getInstance().flush()
   }
   override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){if(r.isForMainFrame){login.failedNetwork(ScvtcRuntime.account);c.fail("官方网页连接失败（${e.errorCode}），离线课表保留")}}
  }
  view.webChromeClient=WebChromeClient()
  view.settings.allowFileAccess=false;view.settings.allowContentAccess=false
  view.loadUrl(School.HOME)
  return view
 }
 fun attachVisible(activity:FragmentActivity,container:FrameLayout){val w=obtain(activity);(w.context as MutableContextWrapper).baseContext=activity;(w.parent as? android.view.ViewGroup)?.removeView(w);container.addView(w,FrameLayout.LayoutParams(-1,-1));controller?.visible=true}
 fun detachVisible(activity:FragmentActivity){if((web?.context as? MutableContextWrapper)?.baseContext!==activity)return;controller?.visible=false;web?.let{(it.parent as? android.view.ViewGroup)?.removeView(it);(it.context as MutableContextWrapper).baseContext=ScvtcRuntime.context};ScvtcSyncWork.enqueue(ScvtcRuntime.context)}
 fun close(){controller?.dispose();controller=null;web?.let{(it.parent as? android.view.ViewGroup)?.removeView(it);it.destroy()};web=null;hidden?.let{(it.parent as? android.view.ViewGroup)?.removeView(it)};hidden=null}
}
/** Observes authenticated JSON identity and semester responses only. WebView
 * establishes SSO; ScvtcRuntime performs every data read through native HTTP. */
class ScvtcCapture(private val web:WebView){
 var loginMemory:cn.scvtc.campus.OfficialLoginMemory?=null
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val messages=Channel<String>(32)
 var visible=true
 var module="schedule"
 var detectedAccount="";var detectedTerm="";var detectedName=""
 private var verifiedAccount=""
 private var documentId=""
 private var officialMonday:OfficialTerm?=null
 var onScopeRequired:(()->Unit)?=null
 private var generation=""
 private var startedGeneration=""
 private var authenticationRetries=0
 fun begin(resetAuthentication:Boolean=true){if(resetAuthentication)authenticationRetries=0;startedGeneration=""}
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
 private fun startVerifiedSync(){
  val calendar=officialMonday?:return
  if(verifiedAccount.isBlank()||generation.isBlank()||startedGeneration==generation)return
  require(ScvtcRuntime.account.isBlank()||ScvtcRuntime.account==verifiedAccount){"官方账号与当前缓存账号不同，请确认学校账号"}
  ScvtcRuntime.confirm(verifiedAccount,calendar.semester)
  ScvtcRuntime.registerVerifiedIdentity(verifiedAccount,calendar.semester,detectedName)
  loginMemory?.confirmed(verifiedAccount)
  start(resetAuthentication=false)
 }
 private fun consume(m:JSONObject){
  val page=m.optString("page")
  if(Uri.parse(page).scheme!="https"||Uri.parse(page).host!="jwxt.scvtc.edu.cn"||web.url!=page)return
  val kind=m.optString("kind");val epoch=m.optString("generation")
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
