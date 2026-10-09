package com.tyust.course.scvtc

import android.app.Application
import cn.scvtc.campus.UiSystem
import cn.scvtc.campus.VisualStyle
import com.tyust.course.manager.AppearanceSettingsManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppearancePreferenceRegressionTest {
    private lateinit var previous: cn.scvtc.campus.ThemeState
    private var previousGlass = true
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        ScvtcRuntime.initialize(context)
        AppearanceSettingsManager.initialize(context)
        NextAppearance.initialize()
        previous = NextAppearance.theme
        previousGlass = AppearanceSettingsManager.glassEffectEnabled
        NextAppearance.update(previous.copy(ui = UiSystem.MIUIX, style = VisualStyle.ZHENGFANG))
    }
    @After fun restore() {
        NextAppearance.update(previous)
        AppearanceSettingsManager.updateGlassEffect(previousGlass)
    }
    @Test fun hapticAndReducedMotionChangesDoNotReenableDisabledGlass() {
        AppearanceSettingsManager.updateGlassEffect(false)
        val theme = NextAppearance.theme
        NextAppearance.update(theme.copy(haptic = !theme.haptic, reduceMotion = !theme.reduceMotion))
        assertFalse(AppearanceSettingsManager.glassEffectEnabled)
    }
    @Test fun loadingSavedCampusAppearancePreservesDisabledGlass() {
        AppearanceSettingsManager.updateGlassEffect(false)
        NextAppearance.initialize()
        assertFalse(AppearanceSettingsManager.glassEffectEnabled)
    }
    @Test fun changingStyleOrUiAppliesTheCorrespondingMaterialDefault() {
        NextAppearance.update(NextAppearance.theme.copy(style = VisualStyle.CLASSIC))
        assertFalse(AppearanceSettingsManager.glassEffectEnabled)
        NextAppearance.update(NextAppearance.theme.copy(style = VisualStyle.SLEEPDOWN))
        assertTrue(AppearanceSettingsManager.glassEffectEnabled)
        NextAppearance.update(NextAppearance.theme.copy(ui = UiSystem.MATERIAL))
        assertFalse(AppearanceSettingsManager.glassEffectEnabled)
    }
}
