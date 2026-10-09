package cn.scvtc.campus
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cn.scvtc.campus.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.security.*
import java.util.concurrent.TimeUnit
import javax.crypto.*
import javax.crypto.spec.*
import androidx.work.*

/** Backup credentials are distinct from school credentials; the server sees ciphertext only. */
class CloudBackup private constructor(private val context:Context){
 data class Config(val endpoint:String,val token:String,val phrase:String)
 val status=MutableStateFlow("云服务已暂停；课表保存在本机")
 init { runCatching { WorkManager.getInstance(context).cancelUniqueWork("scvtc.cloud.upload") } }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val mutex=Mutex()
 private val prefs=context.getSharedPreferences("encrypted_cloud_backup",0)
 private val codec=Json{ignoreUnknownKeys=true;encodeDefaults=true}
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(20,TimeUnit.SECONDS).build()
 private val random=SecureRandom()
 private fun base64(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
 private fun bytes(s:String)=Base64.decode(s,Base64.NO_WRAP)
 private fun pendingValue(entry:String,value:String):JSONObject{
  val packed=bytes(value);require(packed.size>28)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,deviceKey(),GCMParameterSpec(128,packed.copyOfRange(0,12)));cipher.updateAAD(entry.removePrefix("pending:").toByteArray())
  return JSONObject(String(cipher.doFinal(packed.copyOfRange(12,packed.size))))
 }
 private fun deviceKey():SecretKey{
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  return (store.getKey("scvtc.cloud.config",null) as? SecretKey)?:KeyGenerator.getInstance("AES","AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("scvtc.cloud.config",3).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 fun config():Config?=runCatching{
  val b=bytes(prefs.getString("config",null)?:return null);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,deviceKey(),GCMParameterSpec(128,b.copyOfRange(0,12)));val o=JSONObject(String(c.doFinal(b.copyOfRange(12,b.size))));Config(o.getString("endpoint"),o.getString("token"),o.getString("phrase"))
 }.getOrNull()
 fun configure(endpoint:String,token:String,phrase:String){
  check(!PAUSED){"云服务已暂停；无需配置服务器"}
  val url=java.net.URI(endpoint.trim());require(url.scheme=="https"&&url.host!=null&&url.userInfo==null&&url.query==null&&url.fragment==null){"云备份需要 HTTPS 服务地址"}
  require(token.length>=24&&phrase.length>=16){"备份令牌至少24字符，恢复密钥至少16字符"}
  val text=JSONObject().put("endpoint",endpoint.trim().trimEnd('/')).put("token",token.trim()).put("phrase",phrase).toString()
  val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,deviceKey())
  check(prefs.edit().putString("config",base64(c.iv+c.doFinal(text.toByteArray()))).commit())
  status.value="云备份已配置；下次成功同步后自动备份"
 }
 fun disable(){prefs.edit().remove("config").apply();WorkManager.getInstance(context).cancelUniqueWork("scvtc.cloud.upload");status.value="云备份已关闭；本地内容保留"}
 fun newPhrase():String=base64(ByteArray(32).also(random::nextBytes))
 private fun id(e:Extraction)=MessageDigest.getInstance("SHA-256").digest("${e.account}|${e.semester}".toByteArray()).joinToString(""){"%02x".format(it)}
 private fun key(phrase:String,salt:ByteArray):SecretKey=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(phrase.toCharArray(),salt,120000,256)).encoded,"AES")
 private fun encrypt(config:Config,e:Extraction,time:Long):String{
  val salt=ByteArray(16).also(random::nextBytes);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(config.phrase,salt));cipher.updateAAD(id(e).toByteArray())
  val data=JSONObject().put("savedAt",time).put("schedule",codec.encodeToString(e)).toString().toByteArray()
  return JSONObject().put("version",1).put("salt",base64(salt)).put("iv",base64(cipher.iv)).put("ciphertext",base64(cipher.doFinal(data))).toString()
 }
 private fun decrypt(config:Config,text:String,identity:Extraction):Pair<Extraction,Long>{
  val o=JSONObject(text);require(o.getInt("version")==1)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(config.phrase,bytes(o.getString("salt"))),GCMParameterSpec(128,bytes(o.getString("iv"))));cipher.updateAAD(id(identity).toByteArray())
  val result=JSONObject(String(cipher.doFinal(bytes(o.getString("ciphertext")))))
  val e=codec.decodeFromString<Extraction>(result.getString("schedule"));require(e.account==identity.account&&e.semester==identity.semester);ScheduleWriteGuard.validate(null,e);require(e.meetings.isNotEmpty())
  return e to result.getLong("savedAt")
 }
 fun publish(e:Extraction,time:Long){
  if(PAUSED)return
  if(e.module!="schedule"||e.meetings.isEmpty()||config()==null)return
  scope.launch{mutex.withLock{
   ScheduleWriteGuard.validate(null,e)
   val entry="pending:"+id(e)
   val newer=prefs.getString(entry,null)?.let{runCatching{pendingValue(entry,it).getLong("savedAt")>time}.getOrDefault(false)}?:false
   if(!newer){
   val text=JSONObject().put("savedAt",time).put("schedule",codec.encodeToString(e)).toString()
   val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,deviceKey());cipher.updateAAD(id(e).toByteArray())
   check(prefs.edit().putString("pending:"+id(e),base64(cipher.iv+cipher.doFinal(text.toByteArray()))).commit())
   }
   status.value="课表已保存，等待自动加密上传"
   WorkManager.getInstance(context).enqueueUniqueWork("scvtc.cloud.upload",ExistingWorkPolicy.KEEP,
    OneTimeWorkRequestBuilder<CloudBackupWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
  }}
 }
 /** Durable encrypted outbox survives the Activity and process; failures keep local data. */
 suspend fun flushPending():Boolean=withContext(Dispatchers.IO){mutex.withLock{
  if(PAUSED)return@withLock true
  val c=config()?:return@withLock true
  val pending=prefs.all.filterKeys{it.startsWith("pending:")}
  var complete=true
  for((entry,value)in pending){try{
   val data=pendingValue(entry,value as String)
   val e=codec.decodeFromString<Extraction>(data.getString("schedule"));val time=data.getLong("savedAt")
   require(entry=="pending:"+id(e));ScheduleWriteGuard.validate(null,e)
   val url=c.endpoint+"/v1/snapshots/"+id(e);var etag:String?=null
   var newer=false
   client.newCall(Request.Builder().url(url).header("Authorization","Bearer "+c.token).build()).execute().use{r->
    if(r.code==200){etag=r.header("ETag")?:error("云端没有版本号");val remote=decrypt(c,r.body?.string()?:error("云端响应为空"),e);newer=remote.second>=time}
    else require(r.code==404){"云服务 HTTP ${r.code}，本地课表保留"}
   }
   if(!newer){
    val builder=Request.Builder().url(url).header("Authorization","Bearer "+c.token).put(encrypt(c,e,time).toRequestBody("application/json".toMediaType()))
    if(etag==null)builder.header("If-None-Match","*")else builder.header("If-Match",etag!!)
    client.newCall(builder.build()).execute().use{r->require(r.isSuccessful){if(r.code==412)"云版本已变化，已阻止覆盖"else"云服务 HTTP ${r.code}"}}
   }
   if(prefs.getString(entry,null)==value)prefs.edit().remove(entry).commit()
   status.value=if(newer)"云端已有同样或较新的备份，本地内容保留"else"已自动备份课表（加密）"
  }catch(e:CancellationException){throw e}catch(e:Exception){complete=false;status.value="云备份等待重试："+e.message.orEmpty().take(100)}}
  complete&&prefs.all.keys.none{it.startsWith("pending:")}
 }}
 data class Revision(val etag:String,val created:Long)
 suspend fun history(account:String,semester:String):List<Revision> = withContext(Dispatchers.IO){
  if(PAUSED)return@withContext emptyList()
  val c=config()?:return@withContext emptyList();val identity=Extraction("schedule",account,semester)
  client.newCall(Request.Builder().url(c.endpoint+"/v1/snapshots/"+id(identity)+"/history").header("Authorization","Bearer "+c.token).build()).execute().use{r->
   require(r.isSuccessful){"云服务 HTTP ${r.code}"};val array=org.json.JSONArray(r.body?.string()?:"[]")
   (0 until array.length()).map{val row=array.getJSONObject(it);Revision(row.getString("etag"),row.getLong("created"))}.also{require(it.all{r->r.etag.matches(Regex("[0-9a-f]{64}"))})}
  }
 }
 suspend fun restore(account:String,semester:String,revision:String?=null):Extraction?=withContext(Dispatchers.IO){mutex.withLock{
  if(PAUSED)return@withLock null
  val c=config()?:return@withLock null;val identity=Extraction("schedule",account,semester)
  try{require(revision==null||revision.matches(Regex("[0-9a-f]{64}")));client.newCall(Request.Builder().url(c.endpoint+"/v1/snapshots/"+id(identity)+(revision?.let{"/history/$it"}?:"")).header("Authorization","Bearer "+c.token).build()).execute().use{r->
   if(r.code==404)return@withLock null;require(r.isSuccessful){"云服务 HTTP ${r.code}"}
   decrypt(c,r.body?.string()?:error("云端响应为空"),identity).first.copy(full=false).also{status.value="已取得云备份，等待本地校验保存"}
  }}catch(e:CancellationException){throw e}catch(e:Exception){status.value="云备份未恢复：${e.message.orEmpty().take(100)}";null}
 }}
 companion object {
  const val PAUSED=true
  @Volatile private var instance:CloudBackup?=null
  fun get(c:Context):CloudBackup=instance?:synchronized(this){instance?:CloudBackup(c.applicationContext).also{instance=it}}
 }
}

class CloudBackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result=try{
  if(CloudBackup.get(applicationContext).flushPending())Result.success()
  else if(runAttemptCount<4)Result.retry()else Result.failure(workDataOf("code" to "CLOUD_RETRY_LIMIT"))
 }catch(e:CancellationException){throw e}catch(e:Exception){if(runAttemptCount<4)Result.retry()else Result.failure(workDataOf("code" to "CLOUD_UPLOAD_FAILED"))}
}
