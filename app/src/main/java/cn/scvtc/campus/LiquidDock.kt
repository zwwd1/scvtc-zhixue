package cn.scvtc.campus

import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop

val tabLabels = listOf("首页", "课表", "服务", "我的")
val tabIcons = listOf(Icons.Outlined.Home, Icons.Outlined.CalendarMonth, Icons.Outlined.GridView, Icons.Outlined.PersonOutline)

/** Thin campus binding to the user's Reading-Yemo-Beta 1.1.1 dock. */
@Composable
fun LiquidDock(selected: Int, onSelect: (Int) -> Unit, backdrop: LayerBackdrop,
               theme: ThemeState, dark: Boolean, modifier: Modifier = Modifier, minimized:Boolean=false,customItems:List<NavigationItem>?=null,onExpand:()->Unit={}) {
  val haptic = LocalHapticFeedback.current
  val normal = listOf(Icons.Outlined.Home, Icons.Outlined.CalendarMonth, Icons.Outlined.GridView, Icons.Outlined.PersonOutline)
  val active = listOf(Icons.Filled.Home, Icons.Filled.CalendarMonth, Icons.Filled.GridView, Icons.Filled.Person)
  val items = customItems ?: listOf("首页", "课表", "服务", "我的").mapIndexed { i, label ->
    NavigationItem(label = label, icon = if (i == selected) active[i] else normal[i])
  }
  val safeSelected=selected.coerceIn(0,items.lastIndex.coerceAtLeast(0))
  val compact=minimized&&theme.collapseDock
  BoxWithConstraints(modifier,contentAlignment=Alignment.Center){
   val width by animateDpAsState(if(compact)176.dp else maxWidth,if(theme.reduceMotion)snap()else spring(.84f,340f),label="reader-dock-width")
   val profile=if(theme.style==VisualStyle.SLEEPDOWN)theme.copy(radius=(theme.radius*.875f).coerceIn(20f,36f),blur=maxOf(8f,theme.blur),refraction=theme.refraction*.65f)else theme
   IosLiquidGlassNavigationBar(items = if(compact)listOf(items[safeSelected])else items, selectedIndex = if(compact)0 else safeSelected,
    onItemClick = { if(compact)onExpand()else {if (it != selected && theme.haptic) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(it) }},
    backdrop = backdrop, isBlurActive = true, modifier = Modifier.width(width),
    blurEnabled = theme.blur > 0f, isDark = dark, glass = profile)
  }
}
