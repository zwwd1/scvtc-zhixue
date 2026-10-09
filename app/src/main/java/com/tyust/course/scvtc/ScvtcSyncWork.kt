package com.tyust.course.scvtc

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.work.*
import com.tyust.course.manager.UserManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/** WorkManager survives Activity destruction. All reads share Runtime's session mutex. */
object ScvtcSyncWork {
    private const val PERIODIC = "scvtc.periodic"
    private const val READ = "scvtc.read"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun start(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is ScvtcWebActivity) return
                scope.launch {
                    if (eligible()) {
                        ScvtcRuntime.restoreLastGood()
                        enqueue(app)
                    }
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        periodic(app)
        scope.launch { if (eligible()) ScvtcRuntime.restoreLastGood() }
    }
    private fun periodic(context:Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ScvtcSyncWorker>(2, TimeUnit.HOURS).setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    internal fun eligible(): Boolean = UserManager.getInstance().let {
        !it.isDemoMode && it.currentSchool?.id == "scvtc" && it.studentId == ScvtcRuntime.account &&
            ScvtcRuntime.account.isNotBlank() && ScvtcRuntime.semester.isNotBlank()
    }
    fun enqueue(context: Context) {
        if (!eligible()) return
        periodic(context)
        val prefs=context.getSharedPreferences("scvtc_profile",0)
        if (System.currentTimeMillis()-prefs.getLong("lastAttempt",0L)<5*60*1000L) return
        val data=workDataOf("account" to ScvtcRuntime.account,"semester" to ScvtcRuntime.semester)
        WorkManager.getInstance(context).enqueueUniqueWork(READ,ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScvtcSyncWorker>().setInputData(data).setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(READ)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}

class ScvtcSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ScvtcSyncWork.eligible()) return Result.failure(workDataOf("code" to "NO_ACTIVE_SCOPE"))
        val a=inputData.getString("account")?:ScvtcRuntime.account
        val t=inputData.getString("semester")?:ScvtcRuntime.semester
        if (a!=ScvtcRuntime.account || t!=ScvtcRuntime.semester) return Result.failure(workDataOf("code" to "SCOPE_CHANGED"))
        applicationContext.getSharedPreferences("scvtc_profile",0).edit().putLong("lastAttempt",System.currentTimeMillis()).apply()
        return try {
            ScvtcRuntime.synchronize()
            Result.success(workDataOf("code" to "SAVED"))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message=e.message.orEmpty()
            val needsUser=message.contains("AUTH_REQUIRED") || message.contains("认证") || message.contains("账号") || message.contains("解析") || message.contains("结构")
            if (!needsUser && runAttemptCount<3) Result.retry()
            else Result.failure(workDataOf("code" to if(needsUser)"AUTH_OR_PROTOCOL" else "RETRY_LIMIT"))
        }
    }
}
