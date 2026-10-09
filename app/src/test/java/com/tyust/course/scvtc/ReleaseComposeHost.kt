package com.tyust.course.scvtc

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/** Unit-test host only: release APKs must not include ui-test-manifest activities. */
class ReleaseComposeActivityRule : ExternalResource() {
    lateinit var controller: ActivityController<ComponentActivity>
    override fun before() { controller = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    override fun after() { controller.pause().stop().destroy() }
}
fun createReleaseComposeRule() = AndroidComposeTestRule(ReleaseComposeActivityRule()) { it.controller.get() }
