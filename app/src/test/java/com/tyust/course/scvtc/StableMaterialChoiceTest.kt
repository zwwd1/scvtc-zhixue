package com.tyust.course.scvtc

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.tyust.course.scvtc.createReleaseComposeRule
import cn.scvtc.campus.StableMaterialChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StableMaterialChoiceTest {
    @get:Rule val compose = createReleaseComposeRule()

    @Test fun choiceWaitsForExitBeforeReplacingTheUiOwner() {
        var calls = 0
        val replaceOwner = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                if (replaceOwner.value) Text("新界面")
                else StableMaterialChoice(listOf("保持", "切换"), 0, onSelect = {
                    calls++
                    replaceOwner.value = true
                }) { open -> TextButton(open) { Text("打开选择") } }
            }
        }
        compose.onNodeWithText("打开选择").performClick()
        compose.onNodeWithText("切换").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("切换").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("新界面").assertIsDisplayed()
        compose.onNodeWithText("切换").assertDoesNotExist()
    }

    @Test fun choosingTheCurrentValueClosesWithoutApplyingItAgain() {
        var calls = 0
        compose.setContent {
            MaterialTheme {
                StableMaterialChoice(listOf("保持", "切换"), 0, onSelect = { calls++ }) {
                    open -> TextButton(open) { Text("打开选择") }
                }
            }
        }
        compose.onNodeWithText("打开选择").performClick()
        compose.onNodeWithText("✓ 保持").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("切换").assertDoesNotExist()
    }
}
