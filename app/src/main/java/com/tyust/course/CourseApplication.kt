package com.tyust.course

import android.app.Application
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.AppThemeCoordinator
import com.tyust.course.ui.system.GlassRuntimeGuard

class CourseApplication : Application() {
    private var mainProcess = false
    override fun onCreate() {
        super.onCreate()
        // Isolated plugin UIDs must never initialize application storage or providers.
        if (android.os.Process.myUid() != applicationInfo.uid) return
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            runCatching { java.io.File("/proc/self/cmdline").inputStream().use {
                val bytes = ByteArray(256)
                val count = it.read(bytes)
                String(bytes, 0, count.coerceAtLeast(0)).substringBefore('\u0000')
            } }.getOrNull()
        }
        if (processName == packageName) {
            mainProcess = true
            com.tyust.course.scvtc.ScvtcRuntime.initialize(this)
            com.tyust.course.manager.UserManager.getInstance().init(this)
            com.tyust.course.usage.UsageStatsManager.initialize(this)
            val user = com.tyust.course.manager.UserManager.getInstance()
            if (user.currentSchool == null) user.currentSchool = user.getSchoolById("scvtc")
            com.tyust.course.diagnostics.AppDiagnostics.install(this)
            com.tyust.course.academic.plugin.AcademicProviderRegistry.initialize(this)
            com.tyust.course.academic.plugin.PluginPages.initialize(this)
            GlassRuntimeGuard.initialize(this)
            AppearanceSettingsManager.initialize(this)
            AppThemeCoordinator.initialize(this)
            com.tyust.course.scvtc.NextAppearance.initialize()
            com.tyust.course.schedule.ScheduleReminderScheduler.get(this).start(this)
            com.tyust.course.schedule.ScheduleWidgetUpdater.start(this)
            com.tyust.course.scvtc.ScvtcSyncWork.start(this)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mainProcess) {
            AppThemeCoordinator.configurationChanged()
            com.tyust.course.schedule.ScheduleWidgetUpdater.update(this)
        }
    }
}
