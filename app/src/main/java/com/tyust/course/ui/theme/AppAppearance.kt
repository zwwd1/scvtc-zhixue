package com.tyust.course.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.WallpaperMode
import com.tyust.course.manager.WallpaperStyle
import com.tyust.course.manager.WallpaperToneMap

@Immutable
data class AppAppearance(val isDark: Boolean = false)

val LocalAppAppearance = staticCompositionLocalOf { AppAppearance() }

@Composable
internal fun rememberAppWallpaperToneMap(dark: Boolean = LocalAppAppearance.current.isDark): WallpaperToneMap {
    val original = AppearanceSettingsManager.toneMap
    val useNightPreset = dark && AppearanceSettingsManager.mode == WallpaperMode.Preset && !AppearanceSettingsManager.style.isDark
    return remember(original, useNightPreset) {
        if (useNightPreset) WallpaperToneMap.uniform(BackgroundDark.toArgb()) else original
    }
}

/** Night variants affect built-in presets only; imported pictures and colors stay intact. */
@Composable
fun rememberAppWallpaperStyle(): WallpaperStyle {
    val original = AppearanceSettingsManager.style
    val dark = LocalAppAppearance.current.isDark
    val mode = AppearanceSettingsManager.mode
    val target = remember(original, dark, mode) {
        if (!dark || mode != WallpaperMode.Preset || original.isDark) original else original.copy(
            baseColor = BackgroundDark,
            glowColor = Color(0xFF263349),
            shadeColor = Color(0xFF05080E),
            isDark = true,
            accents = original.accents.map { it.copy(alpha = it.alpha * 0.55f) }
        )
    }
    val transition=androidx.compose.animation.core.tween<Color>(
        if(mode==WallpaperMode.Preset && android.animation.ValueAnimator.areAnimatorsEnabled())320 else 0,
        easing=androidx.compose.animation.core.FastOutSlowInEasing)
    return target.copy(
        baseColor=androidx.compose.animation.animateColorAsState(target.baseColor,transition,label="wallpaper-base").value,
        glowColor=androidx.compose.animation.animateColorAsState(target.glowColor,transition,label="wallpaper-glow").value,
        shadeColor=androidx.compose.animation.animateColorAsState(target.shadeColor,transition,label="wallpaper-shade").value,
        accents=target.accents.mapIndexed { index,accent ->
            androidx.compose.runtime.key(index) {
                accent.copy(
                    color=androidx.compose.animation.animateColorAsState(accent.color,transition,label="wallpaper-accent-$index").value,
                    alpha=androidx.compose.animation.core.animateFloatAsState(accent.alpha,
                        androidx.compose.animation.core.tween(if(mode==WallpaperMode.Preset && android.animation.ValueAnimator.areAnimatorsEnabled())320 else 0),
                        label="wallpaper-accent-alpha-$index").value
                )
            }
        }
    )
}
