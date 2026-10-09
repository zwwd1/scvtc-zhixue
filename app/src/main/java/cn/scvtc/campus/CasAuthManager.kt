package cn.scvtc.campus

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import cn.scvtc.campus.core.School
import kotlinx.coroutines.*

/** Official browser rendering is used only for CAS/SSO authentication. Student
 * data is obtained and validated separately by JwxtApi over native HTTP. */
class CasAuthManager(private val context:Context,private val api:JwxtApi) {
    companion object {
        // Observed official frontend config and actual HTTP 302. Its service
        // callback is /api/cas/login on JWXT, distinct from the H5 Shulin callback.
        val JWXT_SSO_ENTRY=School.ORIGIN+"/jwgr/api/api/cas/login?pattern=teacher-login&returnUrl="+Uri.encode(School.HOME)
        const val PROVIDED_H5_ENTRY="https://cas.scvtc.edu.cn/cas/H5/index.html?service=https://www.shulin-soft.com:8267/casLogin.html#/"
    }
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun restoreJwxt(account:String):Unit=withContext(Dispatchers.Main) {
        val login=OfficialLoginMemory(context.applicationContext)
        val failure=CompletableDeferred<Unit>()
        val web=WebView(context.applicationContext)
        web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false;web.settings.allowContentAccess=false
        web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
        // Give the official form a real viewport. Its visibility checks must
        // also work without a foreground Activity or an attached container.
        val metrics=context.resources.displayMetrics
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,android.view.View.MeasureSpec.EXACTLY))
        web.layout(0,0,metrics.widthPixels,metrics.heightPixels);web.onResume()
        login.install(web){account}
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {
                val u=r.url
                val approved=u.scheme=="https" && (u.host in setOf("cas.scvtc.edu.cn","jwxt.scvtc.edu.cn")&&u.port in setOf(-1,443) || u.host=="www.shulin-soft.com"&&u.port==8267&&u.path=="/casLogin.html")
                if(!approved)failure.completeExceptionally(IllegalStateException("SSO 跳转到未确认来源；原内容保留"))
                return !approved
            }
            override fun onPageFinished(v:WebView,url:String) {
                login.prepare(v);CookieManager.getInstance().flush()
                if(Uri.parse(url).host=="www.shulin-soft.com") {v.loadUrl(JWXT_SSO_ENTRY);return}
                if(login.recoveryNavigation(v,account,url))return
                if(Uri.parse(url).host=="cas.scvtc.edu.cn")v.postDelayed({
                    if(v.url==url)v.evaluateJavascript("!!document.querySelector('input[type=password]')") { result ->
                        if(result=="true")login.recover(v,account) { restored ->
                            if(!restored)failure.completeExceptionally(JwxtAuthenticationRequired())
                        }
                    }
                },1500)
            }
            override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError) {
                if(r.isForMainFrame){login.failedNetwork(account);failure.completeExceptionally(IllegalStateException("SSO 网络连接失败；原课表保留"))}
            }
        }
        // Target-domain navigation obtains its own Session after CAS. A login
        // URL (including the supplied H5 callback) is never a success signal.
        web.loadUrl(JWXT_SSO_ENTRY)
        try {
            withTimeout(90_000) {
                while(true) {
                    if(failure.isCompleted)failure.await()
                    try {api.student(account);login.confirmed(account);break}
                    catch(e:JwxtAuthenticationRequired){delay(1500)}
                }
            }
        } finally {web.stopLoading();web.destroy();CookieManager.getInstance().flush()}
    }
}

/** A single bounded SSO/credential recovery, followed by a fresh identity API.
 * HTTP errors never erase login/profile/course state or launch a login Activity. */
class JwxtSessionManager(private val api:JwxtApi,private val recover:suspend (String)->Unit) {
    suspend fun verifiedStudent(account:String):JwxtStudent = try {api.student(account)}
    catch(e:JwxtAuthenticationRequired){recover(account);api.student(account)}
    suspend fun <T> withSession(account:String,read:suspend (JwxtStudent)->T):T {
        try {return read(api.student(account))}
        catch(e:JwxtAuthenticationRequired){recover(account);return read(api.student(account))}
    }
}
