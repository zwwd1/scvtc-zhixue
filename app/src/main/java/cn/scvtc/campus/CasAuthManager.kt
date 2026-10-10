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
    suspend fun restoreJwxt(account:String) { authenticate(account,null) }
    suspend fun signIn(account:String,password:String):JwxtStudent {
        require(account.matches(Regex("[0-9]{6,20}")) && password.length in 1..512){"请输入正确的学号和密码"}
        return authenticate(account,password)
    }
    private suspend fun authenticate(account:String,password:String?):JwxtStudent=withContext(Dispatchers.Main) {
        val login=OfficialLoginMemory(context.applicationContext)
        val failure=CompletableDeferred<Unit>()
        val recoveryPages=mutableSetOf<String>()
        var submitted=password==null
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
                val approved=u.scheme=="https" && u.userInfo==null && (u.host in setOf("cas.scvtc.edu.cn","jwxt.scvtc.edu.cn")&&u.port in setOf(-1,443) || u.host=="www.shulin-soft.com"&&u.port==8267&&u.path=="/casLogin.html")
                if(!approved)failure.completeExceptionally(IllegalStateException("SSO 跳转到未确认来源；原内容保留"))
                return !approved
            }
            override fun onPageFinished(v:WebView,url:String) {
                login.prepare(v);CookieManager.getInstance().flush()
                if(Uri.parse(url).host=="www.shulin-soft.com") {v.loadUrl(JWXT_SSO_ENTRY);return}
                if(login.recoveryNavigation(v,account,url))return
                if(Uri.parse(url).host=="cas.scvtc.edu.cn"&&recoveryPages.add(url)) {
                    val completed:(Boolean)->Unit={restored->
                        if(restored)submitted=true
                        if(!restored&&v.url==url)failure.completeExceptionally(JwxtAuthenticationRequired())
                    }
                    if(password==null)login.recover(v,account,completed)
                    else login.configure(v,account,password,completed)
                }
            }
            override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError) {
                if(r.isForMainFrame){login.failedNetwork(account);failure.completeExceptionally(IllegalStateException("SSO 网络连接失败；原课表保留"))}
            }
        }
        // Target-domain navigation obtains its own Session after CAS. A login
        // URL (including the supplied H5 callback) is never a success signal.
        try {
            // An explicit password login must submit those credentials. An old
            // CAS/target cookie must not promote an untested replacement password.
            if(password!=null)clearLoginCookies()
            web.loadUrl(if(password==null)JWXT_SSO_ENTRY else PROVIDED_H5_ENTRY)
            withTimeout(90_000) {
                while(true) {
                    if(failure.isCompleted)failure.await()
                    if(!submitted){delay(500);continue}
                    try {
                        val student=api.student(account)
                        login.confirmed(student.account,promoteEnrollment=password!=null)
                        return@withTimeout student
                    }
                    catch(e:JwxtAuthenticationRequired){delay(1500)}
                }
                @Suppress("UNREACHABLE_CODE") error("身份未核验")
            }
        } catch(e:TimeoutCancellationException){
            currentCoroutineContext().ensureActive()
            throw JwxtAuthenticationRequired()
        } finally {web.stopLoading();web.destroy();CookieManager.getInstance().flush()}
    }
    private suspend fun clearLoginCookies() {
        val manager=CookieManager.getInstance()
        val locations=listOf(
            "https://cas.scvtc.edu.cn/cas/H5/index.html" to listOf("/","/cas","/cas/","/cas/H5"),
            JwxtApi.BASE to listOf("/","/jwgr","/jwgr/","/jwgr/api")
        )
        for((url,paths) in locations) {
            val names=manager.getCookie(url).orEmpty().split(';')
                .map{it.substringBefore('=').trim()}.filter{it.matches(Regex("[A-Za-z0-9_-]+"))}
            for(name in names)for(path in paths)suspendCancellableCoroutine<Unit>{continuation->
                manager.setCookie(url,"$name=; Path=$path; Max-Age=0; Secure"){
                    if(continuation.isActive)continuation.resumeWith(Result.success(Unit))
                }
            }
        }
        manager.flush()
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
