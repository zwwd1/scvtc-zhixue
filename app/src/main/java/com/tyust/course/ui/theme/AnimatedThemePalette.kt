package com.tyust.course.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Retain the native palette and optics while theme roles change together. */
@Composable
internal fun animatedThemePalette(target: ColorScheme): ColorScheme {
    val transition = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = if (ValueAnimator.areAnimatorsEnabled()) 320 else 0,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )
    return target.copy(
        primary = animateColorAsState(target.primary, transition, label = "theme-primary").value,
        onPrimary = animateColorAsState(target.onPrimary, transition, label = "theme-onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, transition, label = "theme-primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, transition, label = "theme-onPrimaryContainer").value,
        inversePrimary = animateColorAsState(target.inversePrimary, transition, label = "theme-inversePrimary").value,
        secondary = animateColorAsState(target.secondary, transition, label = "theme-secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, transition, label = "theme-onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, transition, label = "theme-secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, transition, label = "theme-onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, transition, label = "theme-tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, transition, label = "theme-onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, transition, label = "theme-tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, transition, label = "theme-onTertiaryContainer").value,
        background = animateColorAsState(target.background, transition, label = "theme-background").value,
        onBackground = animateColorAsState(target.onBackground, transition, label = "theme-onBackground").value,
        surface = animateColorAsState(target.surface, transition, label = "theme-surface").value,
        onSurface = animateColorAsState(target.onSurface, transition, label = "theme-onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, transition, label = "theme-surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, transition, label = "theme-onSurfaceVariant").value,
        surfaceTint = animateColorAsState(target.surfaceTint, transition, label = "theme-surfaceTint").value,
        inverseSurface = animateColorAsState(target.inverseSurface, transition, label = "theme-inverseSurface").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, transition, label = "theme-inverseOnSurface").value,
        error = animateColorAsState(target.error, transition, label = "theme-error").value,
        onError = animateColorAsState(target.onError, transition, label = "theme-onError").value,
        errorContainer = animateColorAsState(target.errorContainer, transition, label = "theme-errorContainer").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, transition, label = "theme-onErrorContainer").value,
        outline = animateColorAsState(target.outline, transition, label = "theme-outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, transition, label = "theme-outlineVariant").value,
        scrim = animateColorAsState(target.scrim, transition, label = "theme-scrim").value,
        surfaceBright = animateColorAsState(target.surfaceBright, transition, label = "theme-surfaceBright").value,
        surfaceDim = animateColorAsState(target.surfaceDim, transition, label = "theme-surfaceDim").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, transition, label = "theme-surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, transition, label = "theme-surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, transition, label = "theme-surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, transition, label = "theme-surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, transition, label = "theme-surfaceContainerLowest").value
    )
}

