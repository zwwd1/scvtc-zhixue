package com.tyust.course.activation

import android.app.Application
import android.content.ContentResolver
import android.provider.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Reproduce the provider exception in the user's API 37 crash report.
 * Runs the real startup/settings lookup on SDK 33, not an API 37 device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class,shadows=[RejectDeviceSettings::class])
class DeviceIdentifierFailureTest {
    @Test fun rejectedProviderDoesNotPreventStartupActivation() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        ActivationManager.clearActivation(context)
        assertTrue(ActivationManager.checkActivation(context))
        assertEquals(DeviceUtils.getDeviceId(context),ActivationManager.getSavedDeviceId(context))
    }
    @Test fun settingsCanReadIdentifierBeforeStartupHasSavedIt() {
        val context=RuntimeEnvironment.getApplication()
        ActivationManager.clearActivation(context)
        val first=ActivationManager.getSavedDeviceId(context)
        assertTrue(first.matches(Regex("[0-9A-F]{8}")))
        assertEquals(first,ActivationManager.getSavedDeviceId(context))
    }
}

@Implements(Settings.Secure::class)
class RejectDeviceSettings {
    companion object {
        @JvmStatic @Implementation
        fun getString(resolver:ContentResolver,name:String):String? {
            throw IllegalArgumentException("Synthetic rejected system provider")
        }
    }
}
