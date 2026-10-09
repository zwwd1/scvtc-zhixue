package com.tyust.course.scvtc
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.tyust.course.ui.system.SystemIconButton
import cn.scvtc.campus.ThemeMode
import kotlinx.coroutines.launch
@Composable fun CampusQuickActions(){
 val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)}
 val dark=NextAppearance.theme.effectiveDark(com.tyust.course.manager.AppThemeCoordinator.systemNight)
 Row{
  SystemIconButton(icon=Icons.Outlined.Sync,contentDescription="刷新课表",enabled=!busy,tint=MaterialTheme.colorScheme.onBackground,onClick={if(!busy){busy=true;scope.launch{try{ScvtcRuntime.synchronize()}catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e}finally{busy=false}}}})
  SystemIconButton(icon=if(dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,contentDescription="切换浅色深色",tint=MaterialTheme.colorScheme.onBackground,onClick={NextAppearance.update(NextAppearance.theme.copy(mode=if(dark)ThemeMode.LIGHT else ThemeMode.DARK))})
 }
}
