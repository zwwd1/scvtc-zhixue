package com.tyust.course.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Visible action surface, including when a dialog has no glass sampling source. */
@Composable
fun SystemDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {

    val color = when {
        destructive -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    LiquidButton(onClick, modifier = modifier, enabled = enabled,
        style = if (primary || destructive) LiquidButtonStyle.Tinted else LiquidButtonStyle.Transparent,
        tint = color, tintAlpha = 0.16f, contentColor = color, content = content)
}
