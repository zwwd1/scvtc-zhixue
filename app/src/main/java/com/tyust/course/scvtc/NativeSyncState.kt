package com.tyust.course.scvtc

import android.content.Context
import android.net.ConnectivityManager
import java.text.DateFormat
import java.util.Date

enum class NativeSyncStage { NONE, PREPARING, AUTHENTICATING, READING, WRITING, SUCCESS, FAILED, OFFLINE, AUTH_REQUIRED, CACHE }
data class NativeSyncState(
    val stage:NativeSyncStage=NativeSyncStage.NONE,val operation:String="课表",val lastAttempt:Long=0,
    val lastSuccess:Long=0,val message:String="尚未同步",
) {
    val busy get()=stage in setOf(NativeSyncStage.PREPARING,NativeSyncStage.AUTHENTICATING,NativeSyncStage.READING,NativeSyncStage.WRITING)
    val label get()=if(stage==NativeSyncStage.SUCCESS)"${operation}同步成功 · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSuccess))}" else message
}
internal class NativeSyncProgress(private val context:Context) {
    val state=kotlinx.coroutines.flow.MutableStateFlow(NativeSyncState())
    private val prefs=context.getSharedPreferences("native_sync_progress",Context.MODE_PRIVATE)
    private var key=""
    fun restore(account:String,term:String,operation:String="课表",cachedAt:Long=0){
        if(state.value.busy)return
        key=java.security.MessageDigest.getInstance("SHA-256").digest("$account|$term|$operation".toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        val success=prefs.getLong("success:$key",cachedAt)
        state.value=NativeSyncState(if(success>0)NativeSyncStage.CACHE else NativeSyncStage.NONE,operation,
            prefs.getLong("attempt:$key",0),success,if(success>0)"本机缓存 · 点击刷新" else "尚未同步 · 点击连接")
    }
    fun begin(account:String,term:String,operation:String="课表",cachedAt:Long=0){
        restore(account,term,operation,cachedAt)
        val time=System.currentTimeMillis();prefs.edit().putLong("attempt:$key",time).apply()
        state.value=state.value.copy(stage=NativeSyncStage.PREPARING,lastAttempt=time,message="准备同步$operation…")
    }
    fun phase(stage:NativeSyncStage,message:String){state.value=state.value.copy(stage=stage,message=message)}
    fun committed(time:Long){prefs.edit().putLong("success:$key",time).apply();state.value=state.value.copy(lastSuccess=time)}
    fun complete(){phase(NativeSyncStage.SUCCESS,"同步成功")}
    fun failure(error:Exception){
        val auth=error is cn.scvtc.campus.JwxtAuthenticationRequired
        val offline=context.getSystemService(ConnectivityManager::class.java).activeNetwork==null
        phase(when{auth->NativeSyncStage.AUTH_REQUIRED;offline->NativeSyncStage.OFFLINE;else->NativeSyncStage.FAILED},
            when{auth->"需补充认证 · 缓存保留";offline->"离线 · 使用上次缓存";else->"同步失败 · 点击重试"})
    }
}
