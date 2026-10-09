package com.tyust.course.scvtc

import android.app.Application
import android.content.Context
import com.tyust.course.manager.UserManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** First verified login must enable resumption before any course has arrived. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SchoolScopeBootstrapTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val user get() = UserManager.getInstance()

    @Before fun prepare() {
        context.getSharedPreferences("scvtc_profile", 0).edit().clear().commit()
        context.getSharedPreferences("course_selector_prefs", 0).edit().clear().commit()
        user.setDemoMode(false)
        user.init(context)
        ScvtcRuntime.initialize(context)
        ScvtcRuntime.confirm("2026000001", "2026-2027-1")
    }

    @After fun close() { ScvtcRuntime.db.close() }

    @Test fun firstLoginCanResumeWithoutAnExistingCourseSnapshot() = runBlocking {
        assertNull(ScvtcRuntime.snapshot())
        assertFalse(ScvtcSyncWork.eligible())
        ScvtcRuntime.registerVerifiedIdentity("2026000001", "2026-2027-1", "Fixture")
        assertTrue(user.isLoggedIn)
        assertTrue(ScvtcSyncWork.eligible())
        // Recreate the native profile as a subsequent process would, without
        // relying on a saved schedule to recover the selected account.
        user.init(context)
        assertEquals("2026000001", user.studentId)
        assertEquals("scvtc", user.currentSchool.id)
        assertTrue(ScvtcSyncWork.eligible())
        assertNull(ScvtcRuntime.snapshot())
    }

    @Test fun anotherAccountCannotEnableThePendingScope() {
        val previous = user.studentId
        assertThrows(IllegalArgumentException::class.java) {
            ScvtcRuntime.registerVerifiedIdentity("2026000002", "2026-2027-1", "Other")
        }
        assertEquals(previous, user.studentId)
        assertFalse(ScvtcSyncWork.eligible())
    }
}
