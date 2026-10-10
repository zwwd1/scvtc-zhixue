package com.tyust.course.scvtc
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.tyust.course.ui.system.SystemIconButton
import cn.scvtc.campus.ThemeMode
@Composable fun CampusQuickActions(
 refreshLabel:String="刷新课表",
 onRefresh:()->Unit={ScvtcRuntime.startNativeSync()},
){
 val running by ScvtcRuntime.taskRunning.collectAsState()
 val sync by ScvtcRuntime.syncState.collectAsState()
 val dark=NextAppearance.theme.effectiveDark(com.tyust.course.manager.AppThemeCoordinator.systemNight)
 Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
  SystemIconButton(icon=Icons.Outlined.Sync,contentDescription=if(running||sync.busy)"正在同步"else refreshLabel,enabled=!running&&!sync.busy,tint=MaterialTheme.colorScheme.onBackground,onClick=onRefresh)
  SystemIconButton(icon=if(dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,contentDescription="切换浅色深色",tint=MaterialTheme.colorScheme.onBackground,onClick={NextAppearance.update(NextAppearance.theme.copy(mode=if(dark)ThemeMode.LIGHT else ThemeMode.DARK))})
 }
}
