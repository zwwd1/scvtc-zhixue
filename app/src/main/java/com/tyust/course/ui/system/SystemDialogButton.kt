package com.tyust.course.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tyust.course.scvtc.nextGlassSurface
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

    if (com.tyust.course.scvtc.NextAppearance.theme.ui == cn.scvtc.campus.UiSystem.MIUIX) {
        top.yukonga.miuix.kmp.basic.Button(onClick = onClick, modifier = modifier.nextGlassSurface(enabled=enabled), enabled = enabled,
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color=if(primary)MaterialTheme.colorScheme.primary.copy(alpha=.14f)else Color.Transparent)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        androidx.compose.material3.Button(onClick = onClick, modifier = modifier.nextGlassSurface(enabled=enabled), enabled = enabled, content = content)
    }
}
