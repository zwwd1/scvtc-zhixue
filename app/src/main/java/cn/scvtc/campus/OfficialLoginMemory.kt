package cn.scvtc.campus

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.WebView
import androidx.webkit.*
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

/** Credentials are captured only from a user's real submit, then bound to verified identity. */
class OfficialLoginMemory(private val context:Context) {
    private val origins=setOf("https://jwxt.scvtc.edu.cn","https://portal.scvtc.edu.cn","https://cas.scvtc.edu.cn")
    private val prefs=context.getSharedPreferences("official_login_memory",Context.MODE_PRIVATE)
    companion object {
        private val KEY_LOCK=Any()
        private const val ENROLLMENT_TTL=15*60*1000L
    }
    /** An encrypted short-lived candidate survives the visible WebView and is
     * promoted only by a matching authenticated student API response. */
    private fun stage(value:JSONObject):Boolean=runCatching {
        val account=value.getString("username")
        check(prefs.edit().putString("enrollment:$account",seal(account,
            JSONObject(value.toString()).put("capturedAt",System.currentTimeMillis()).toString())).commit())
        true
    }.getOrDefault(false)
    fun configured(account:String):Boolean=prefs.getString(account,null)?.let{open(account,it)}!=null
    private fun enrollment(account:String):JSONObject? {
        val value=prefs.getString("enrollment:$account",null)?.let{open(account,it)}?:return null
        val age=System.currentTimeMillis()-value.optLong("capturedAt",0)
        if(age !in 0..ENROLLMENT_TTL || value.optString("username")!=account) {
            prefs.edit().remove("enrollment:$account").apply();return null
        }
        return value
    }
    private var lastAttempt=0L
    private var recoveringPage=""
    private var usedH5Entry=false
    /** Keep the user's verified mobile form binding when the JWXT SSO entry
     * redirects to the desktop CAS form. Do not send a password to a new form. */
    fun recoveryNavigation(web:WebView,account:String,url:String):Boolean {
        val current=Uri.parse(url)
        if(current.scheme!="https"||current.host!="cas.scvtc.edu.cn"||current.port !in setOf(-1,443)||current.path!="/cas/WEB/index.html"||usedH5Entry)return false
        // Keep the actual JWXT service from the official SSO redirect. Sending
        // the user to an unrelated service loses the target Session handoff.
        val service=current.getQueryParameter("service")?:return false
        val callback=Uri.parse(service)
        if(callback.scheme!="https"||callback.host!="jwxt.scvtc.edu.cn"||callback.port !in setOf(-1,443)||callback.userInfo!=null||callback.path!="/api/cas/login")return false
        val mobile=Uri.parse("https://cas.scvtc.edu.cn/cas/H5/index.html").buildUpon().appendQueryParameter("service",service).fragment("/").build()
        usedH5Entry=true
        web.loadUrl(mobile.toString())
        return true
    }
    private fun trusted(url:String):Boolean=runCatching{Uri.parse(url).let{it.scheme=="https"&&it.port in setOf(-1,443)&&it.userInfo==null&&"https://${it.host}" in origins}}.getOrDefault(false)
    private fun key():SecretKey = synchronized(KEY_LOCK) {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        store.getKey("scvtc.official.login.v1",null) as? SecretKey ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder("scvtc.official.login.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    private fun seal(account:String,text:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(account.toByteArray())
        return Base64.encodeToString(cipher.iv+cipher.doFinal(text.toByteArray()),Base64.NO_WRAP)
    }
    private fun open(account:String,text:String):JSONObject?=runCatching{
        val bytes=Base64.decode(text,Base64.NO_WRAP);require(bytes.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));cipher.updateAAD(account.toByteArray())
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size))))
    }.getOrNull()
    /** Only observed headers for the three verified JSON endpoints are retained.
     * The per-account AES-GCM AAD prevents reuse in another account. */
    fun apiHeaders(account:String,url:String):Map<String,String> =
        prefs.getString("headers:$account:${Uri.parse(url).path}",null)?.let{open(account,it)}?.let{value->
            value.keys().asSequence().associateWith{value.getString(it)}
        }.orEmpty()
    private fun rememberHeaders(account:String,url:String,headers:JSONObject){
        if(Uri.parse(url).scheme!="https" || Uri.parse(url).host!="jwxt.scvtc.edu.cn" || url.substringBefore('?') !in setOf(JwxtApi.IDENTITY,JwxtApi.TERM,JwxtApi.SCHEDULE))return
        val safe=JSONObject()
        headers.keys().forEach{key->if(key.lowercase() in setOf("permission","authorization","x-auth-token","token")){
            val value=headers.optString(key);if(value.length in 1..8192)safe.put(key,value)
        }}
        runCatching{prefs.edit().putString("headers:$account:${Uri.parse(url).path}",seal(account,safe.toString())).commit()}
    }
    /** Session cookies are not included in exports or cloud backups. Restore only
     * an empty target-domain session; never overwrite a newly established SSO. */
    suspend fun restoreSession(account:String)=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){
        val manager=android.webkit.CookieManager.getInstance()
        if(manager.getCookie(JwxtApi.BASE).orEmpty().split(';').any{it.trim().startsWith("LYSESSIONID=")})return@withContext
        val saved=prefs.getString("session:$account",null)?.let{open(account,it)}?:return@withContext
        for(name in listOf("LYSESSIONID","user")){
            val value=saved.optString(name);if(value.isBlank()||value.contains(';')||value.contains('\n')||value.contains('\r'))continue
            kotlinx.coroutines.suspendCancellableCoroutine<Unit>{continuation->
                manager.setCookie(JwxtApi.BASE,"$name=$value; Path=/; Secure"+if(name=="LYSESSIONID")"; HttpOnly" else ""){
                    if(continuation.isActive)continuation.resumeWith(Result.success(Unit))
                }
            }
        }
        manager.flush()
    }
    private fun rememberSession(account:String){
        val value=JSONObject()
        android.webkit.CookieManager.getInstance().getCookie(JwxtApi.BASE).orEmpty().split(';').forEach{part->
            val name=part.substringBefore('=').trim();if(name in setOf("LYSESSIONID","user"))value.put(name,part.substringAfter('=',""))
        }
        if(value.optString("LYSESSIONID").isNotBlank())runCatching{prefs.edit().putString("session:$account",seal(account,value.toString())).commit()}
        android.webkit.CookieManager.getInstance().flush()
    }
    fun install(web:WebView,expectedAccount:()->String={""}) {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return
        WebViewCompat.addWebMessageListener(web,"OfficialLoginBridge",origins){_,message,origin,main,_->
            if(main && origin.toString().trimEnd('/') in origins)runCatching {
                val m=JSONObject(message.data.orEmpty())
                require(trusted(m.getString("page")) && web.url==m.getString("page"))
                require(m.getJSONObject("binding").getString("origin")==origin.toString().trimEnd('/'))
                require(m.getString("username").matches(Regex("[0-9]{6,20}")) && m.getString("password").length in 1..512)
                check(stage(m))
            }
        }
        var document="";var verified="";val staged=mutableMapOf<String,JSONObject>()
        WebViewCompat.addWebMessageListener(web,"JwxtHeaderBridge",setOf("https://jwxt.scvtc.edu.cn")){_,message,origin,main,_->
            if(main && origin.toString().trimEnd('/')=="https://jwxt.scvtc.edu.cn")runCatching{
                val raw=message.data.orEmpty();require(raw.length<=2_600_000)
                val m=JSONObject(raw);require(m.getString("page")==web.url)
                // Hash navigation stays in the same document. Keep its verified
                // student identity so newly observed schedule headers are saved.
                // Reloads receive a fresh ID and must prove identity again.
                val doc=m.getString("documentId");require(doc.matches(Regex("[0-9a-fA-F-]{36}")))
                if(doc!=document){document=doc;verified="";staged.clear()}
                val url=m.getString("url").substringBefore('?');require(url in setOf(JwxtApi.IDENTITY,JwxtApi.TERM,JwxtApi.SCHEDULE))
                staged[url]=m.getJSONObject("headers")
                if(url==JwxtApi.IDENTITY){
                    verified=cn.scvtc.campus.core.OfficialIdentity.account(m.optString("identityBody"))
                    require(verified.isNotBlank()&&(expectedAccount().isBlank()||expectedAccount()==verified))
                }
                if(verified.isNotBlank()&&(expectedAccount().isBlank()||expectedAccount()==verified)){
                    staged.forEach{(path,headers)->rememberHeaders(verified,path,headers)};staged.clear()
                }
            }
        }
        val observer=context.assets.open("jwxt-auth-session.js").bufferedReader().use{it.readText()}
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,observer,setOf("https://jwxt.scvtc.edu.cn"))
        val script=context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()}
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,script,origins)
    }
    fun confirmed(account:String,promoteEnrollment:Boolean=false) {
        if(account.isBlank())return
        lastAttempt=0L
        recoveringPage=""
        usedH5Entry=false
        rememberSession(account)
        prefs.edit().remove("attempt:$account").remove("pending:$account").apply()
        if(!promoteEnrollment)return
        val value=enrollment(account)?:return
        if(account.isBlank()||value.optString("username")!=account)return
        recoveringPage=""
        runCatching{check(prefs.edit().putString(account,seal(account,value.toString())).remove("enrollment:$account").remove("attempt:$account").commit())}
    }
    fun prepare(web:WebView) {
        if(trusted(web.url.orEmpty()) && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            web.evaluateJavascript(context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()},null)
        if(web.url?.startsWith("https://jwxt.scvtc.edu.cn/")==true && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            web.evaluateJavascript(context.assets.open("jwxt-auth-session.js").bufferedReader().use{it.readText()},null)
    }
    /** Explicit native input is staged, and saved only after the real identity response. */
    fun configure(web:WebView,account:String,password:String,onResult:(Boolean)->Unit) {
        awaitForm(web) { ready ->
            if(ready) configureReady(web,account,password,onResult) else onResult(false)
        }
    }
    private fun configureReady(web:WebView,account:String,password:String,onResult:(Boolean)->Unit) {
        val page=web.url.orEmpty()
        if(!trusted(page)||!account.matches(Regex("[0-9]{6,20}"))||password.length !in 1..512){onResult(false);return}
        prepare(web)
        web.evaluateJavascript("JSON.stringify(window.__officialLoginMemory?.describe() || null)"){raw->
            val binding=runCatching{val value=org.json.JSONTokener(raw).nextValue() as? String;value?.let(::JSONObject)}.getOrNull()
            if(binding==null||web.url!=page||binding.optString("origin")!="https://${Uri.parse(page).host}"){onResult(false);return@evaluateJavascript}
            if(!stage(JSONObject().put("page",page).put("binding",binding).put("username",account).put("password",password))) {onResult(false);return@evaluateJavascript}
            prefs.edit().remove("pending:$account").remove("attempt:$account").apply();recoveringPage=""
            // Fill a human-challenge form, but only report automatic submission
            // when the official submit handler can actually be invoked.
            web.evaluateJavascript("Boolean(window.__officialLoginMemory?.matches($binding))"){canSubmit->
            web.evaluateJavascript("window.__officialLoginMemory?.configure($binding,${JSONObject.quote(account)},${JSONObject.quote(password)})"){value->
                if(value!="true")prefs.edit().remove("enrollment:$account").apply()
                onResult(value=="true" && canSubmit=="true")
            }
            }
        }
    }
    /** A transport failure is not a rejected password; allow a later bounded attempt. */
    fun failedNetwork(account:String){recoveringPage="";usedH5Entry=false;prefs.edit().remove("pending:$account").apply()}
    /** The official click handler performs its own CAS encryption; no guessed password protocol. */
    fun recover(web:WebView,account:String,onResult:(Boolean)->Unit) {
        awaitForm(web) { ready ->
            if(ready) recoverBoundForm(web,account,onResult) else onResult(false)
        }
    }
    private fun awaitForm(web:WebView,onResult:(Boolean)->Unit) {
        val page=web.url.orEmpty()
        if(!trusted(page)){onResult(false);return}
        var probes=0
        // CAS is a SPA: document load is earlier than its visible form mounting.
        // Wait for a recognisable form without sending credentials or clicking.
        fun ready(){
            if(web.url!=page){onResult(false);return}
            prepare(web)
            web.evaluateJavascript("!!window.__officialLoginMemory?.describe()"){result->
                if(web.url!=page)onResult(false)
                else if(result=="true")onResult(true)
                else if(++probes<20)web.postDelayed({ready()},400)
                else onResult(false)
            }
        }
        ready()
    }
    private fun recoverBoundForm(web:WebView,account:String,onResult:(Boolean)->Unit) {
        val url=web.url.orEmpty()
        if(url==recoveringPage && System.currentTimeMillis()-lastAttempt<120_000){onResult(true);return}
        lastAttempt=maxOf(lastAttempt,prefs.getLong("attempt:$account",0L))
        // A pending attempt is bounded by the same throttle. A failed login or
        // process death must not disable all future recoveries permanently.
        if(account.isBlank()||!trusted(url)||System.currentTimeMillis()-lastAttempt<120_000){onResult(false);return}
        val saved=enrollment(account)?:prefs.getString(account,null)?.let{open(account,it)}?:run{onResult(false);return}
        if(saved.getJSONObject("binding").optString("origin")!="https://${Uri.parse(url).host}"){onResult(false);return}
        val script=context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()}
        web.evaluateJavascript(script,null)
        web.evaluateJavascript("window.__officialLoginMemory?.matches(${saved.getJSONObject("binding")})"){matches->
            if(web.url!=url){onResult(false)}
            else if(matches!="true"){
                // A real CAS captcha requires the user, but they need not retype
                // their account/password when this exact bound form still matches.
                web.evaluateJavascript("window.__officialLoginMemory?.configure(${saved.getJSONObject("binding")},${JSONObject.quote(saved.getString("username"))},${JSONObject.quote(saved.getString("password"))},false)"){onResult(false)}
            }
            else if(System.currentTimeMillis()-lastAttempt<120_000){onResult(false)}
            else {
                lastAttempt=System.currentTimeMillis()
                if(!prefs.edit().putLong("attempt:$account",lastAttempt).putBoolean("pending:$account",true).commit()){onResult(false);return@evaluateJavascript}
                recoveringPage=url
                web.evaluateJavascript("window.__officialLoginMemory?.restore(${saved.getJSONObject("binding")},${JSONObject.quote(saved.getString("username"))},${JSONObject.quote(saved.getString("password"))})"){onResult(it=="true")}
            }
        }
    }
    fun forget(account:String){
        recoveringPage="";usedH5Entry=false
        val edit=prefs.edit().remove("enrollment:$account").remove(account).remove("attempt:$account").remove("pending:$account").remove("session:$account")
        prefs.all.keys.filter{it.startsWith("headers:$account:")}.forEach(edit::remove);edit.apply()
    }
}
