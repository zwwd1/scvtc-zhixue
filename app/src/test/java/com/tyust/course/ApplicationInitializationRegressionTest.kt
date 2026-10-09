package com.tyust.course

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tyust.course.usage.UsageStatsManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowProcess
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Runs the real Application entry point; SDK 33 JVM regression, not a device launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApplicationInitializationRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val reporterField = UsageStatsManager::class.java.getDeclaredField("reporter").apply { isAccessible = true }

    @Before fun prepare() {
        reporterField.set(null, null)
        ShadowProcess.setUid(context.applicationInfo.uid)
        ShadowApplication.setProcessName(context.packageName)
    }

    @After fun clearReporter() { reporterField.set(null, null) }

    private fun application() = CourseApplication().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
    }

    @Test fun mainApplicationInitializesNoticeBeforeFirstActivity() {
        val expectedSchool = com.tyust.course.manager.UserManager.getInstance().currentSchool?.id ?: "scvtc"
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        application().onCreate()
        assertNotNull(reporterField.get(null))
        assertNotNull(UsageStatsManager.preferences.value)
        // Startup must also preserve a school already chosen by the user.
        assertEquals(expectedSchool, com.tyust.course.manager.UserManager.getInstance().currentSchool?.id)
    }

    @Test fun isolatedUidDoesNotInitializeMainProcessUsageState() {
        ShadowProcess.setUid(context.applicationInfo.uid + 1000)
        application().onCreate()
        assertNull(reporterField.get(null))
    }

    @Test fun pluginProcessDoesNotInitializeMainProcessUsageState() {
        ShadowApplication.setProcessName(context.packageName + ":plugin")
        application().onCreate()
        assertNull(reporterField.get(null))
    }
}
