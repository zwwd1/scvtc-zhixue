package com.tyust.course.scvtc

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.tyust.course.ui.system.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tyust.course.ui.system.glass.liquidChip
import com.tyust.course.ui.system.glass.rememberInteractiveOptics
import com.tyust.course.ui.system.glass.GlassChipAppearance
import com.tyust.course.ui.system.LocalControlBackdrop
import com.tyust.course.ui.system.isBackdropSupported
import androidx.compose.ui.unit.dp
import cn.scvtc.campus.*
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.AppThemeMode
import com.tyust.course.ui.theme.CourseSelectorTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miLight

object NextAppearance {
 var theme by mutableStateOf(ThemeState(style=VisualStyle.ZHENGFANG));private set
 var dockStyle by mutableIntStateOf(0);private set
 private var initialized=false
 private val persistenceScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
 private var persistence:Job?=null
 private fun persistTheme(){
  persistence?.cancel()
  persistence=persistenceScope.launch{delay(240);ScvtcRuntime.context.getSharedPreferences("next_appearance",0).edit().putString("theme",ScvtcRuntime.json.encodeToString(theme)).apply()}
 }
 fun updateDockStyle(value:Int){dockStyle=value;ScvtcRuntime.context.getSharedPreferences("next_appearance",0).edit().putInt("dockStyle",value).apply()}
 fun initialize(){val p=ScvtcRuntime.context.getSharedPreferences("next_appearance",0);theme=p.getString("theme",null)?.let{runCatching{ScvtcRuntime.json.decodeFromString<ThemeState>(it)}.getOrNull()}?:ThemeState(style=VisualStyle.ZHENGFANG);initialized=true;theme=theme.copy(ui=UiSystem.MIUIX,style=VisualStyle.ZHENGFANG,courseMaterial=0,mode=when(AppearanceSettingsManager.themeMode){AppThemeMode.System->ThemeMode.SYSTEM;AppThemeMode.Light->ThemeMode.LIGHT;AppThemeMode.Dark->ThemeMode.DARK});p.edit().putString("theme",ScvtcRuntime.json.encodeToString(theme)).apply()}
 fun receiveMode(value:AppThemeMode){if(!initialized)return;val mode=when(value){AppThemeMode.System->ThemeMode.SYSTEM;AppThemeMode.Light->ThemeMode.LIGHT;AppThemeMode.Dark->ThemeMode.DARK};if(theme.mode!=mode){theme=theme.copy(mode=mode);runCatching{ScvtcRuntime.context.getSharedPreferences("next_appearance",0).edit().putString("theme",ScvtcRuntime.json.encodeToString(theme)).apply()}}}
 fun update(t:ThemeState){
  val previous=theme;if(previous==t)return
  theme=t;persistTheme()
  if(t.mode!=previous.mode)AppearanceSettingsManager.updateThemeMode(when(t.mode){ThemeMode.SYSTEM->AppThemeMode.System;ThemeMode.LIGHT->AppThemeMode.Light;ThemeMode.DARK->AppThemeMode.Dark})
 }
}
@Composable fun NextTheme(content: @Composable () -> Unit) {
 val t=NextAppearance.theme
 val dark=t.effectiveDark(com.tyust.course.manager.AppThemeCoordinator.systemNight)
 val platformFeedback=LocalHapticFeedback.current
 val feedback=remember(platformFeedback,t.haptic) {
  object:HapticFeedback {
   override fun performHapticFeedback(hapticFeedbackType:HapticFeedbackType) {
    if(t.haptic)platformFeedback.performHapticFeedback(hapticFeedbackType)
   }
  }
 }
 CompositionLocalProvider(LocalHapticFeedback provides feedback) {
  CourseSelectorTheme(darkTheme=dark,dynamicColor=t.ui==UiSystem.MATERIAL) {
   if(t.ui==UiSystem.MIUIX)MiuixTheme(colors=if(dark)miDark()else miLight()){CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides MaterialTheme.colorScheme.onBackground){content()}}
   else CompositionLocalProvider(LocalIndication provides ripple()){content()}
  }
 }
}
@Composable fun NextText(text:String,modifier:Modifier=Modifier){if(NextAppearance.theme.ui==UiSystem.MIUIX)top.yukonga.miuix.kmp.basic.Text(text,modifier=modifier)else Text(text,modifier)}
@Composable fun NextButton(text:String,modifier:Modifier=Modifier,onClick:()->Unit) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) LiquidButton(onClick=onClick,modifier=modifier){NextText(text)}
 else Button(onClick,modifier){Text(text)}
}
@Composable fun NextChoice(title:String,items:List<String>,selected:Int,onSelect:(Int)->Unit) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) InsetGroupedRow(title=title,trailing={
  LiquidPicker(options=items.map { LiquidPickerOption(it) },selectedIndex=selected,onSelect=onSelect,maxSelectedLabelLines=2)
 }) else cn.scvtc.campus.StableMaterialChoice(items,selected,onSelect=onSelect){open->
  ListItem(headlineContent={Text(title)},supportingContent={Text(items.getOrElse(selected){items.firstOrNull().orEmpty()})},trailingContent={TextButton(open){Text("选择")}})
 }
}
@Composable fun NextSwitch(title:String,checked:Boolean,onChange:(Boolean)->Unit) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) InsetGroupedRow(title=title,trailing={LiquidSwitch(checked,onChange)})
 else ListItem(headlineContent={Text(title)},trailingContent={Switch(checked,onChange)})
}
@Composable fun NextGroup(content:@Composable ColumnScope.()->Unit) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) InsetGroupedSection(content=content)
 else ElevatedCard(Modifier.fillMaxWidth()){Column{content()}}
}
@Composable fun NextRow(title:String,summary:String="",onClick:()->Unit) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) InsetGroupedRow(title=title,subtitle=summary,trailing={NextText("›")},onClick=onClick)
 else ListItem(headlineContent={Text(title)},supportingContent={if(summary.isNotBlank())Text(summary)},trailingContent={TextButton(onClick){Text("打开")}})
}
@Composable fun NextSlider(value:Float,onChange:(Float)->Unit,range:ClosedFloatingPointRange<Float>) {
 if(NextAppearance.theme.ui==UiSystem.MIUIX) {
  val current=rememberUpdatedState(value)
  LiquidSlider(value={current.value},onValueChange=onChange,valueRange=range,
   trackBrush=Brush.horizontalGradient(listOf(Color(0xFF007AFF),Color(0xFF90CAFF))))
 } else Slider(value,onChange,valueRange=range,steps=9)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable fun NextSegmented(options:List<String>,selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier){
 if(NextAppearance.theme.ui==UiSystem.MIUIX)top.yukonga.miuix.kmp.basic.TabRow(tabs=options,selectedTabIndex=selected,onTabSelected=onSelect,modifier=modifier)
 else SingleChoiceSegmentedButtonRow(modifier){options.forEachIndexed{i,label->SegmentedButton(selected=i==selected,onClick={onSelect(i)},shape=SegmentedButtonDefaults.itemShape(i,options.size)){Text(label)}}}
}

/** Keep the base project's sampled optics around real Miuix controls. No second click handler. */
@Composable fun Modifier.nextGlassSurface(interactive:Boolean=true,enabled:Boolean=true):Modifier {
 val backdrop=LocalControlBackdrop.current
 val optics=rememberInteractiveOptics()
 val shape=RoundedCornerShape(when(NextAppearance.theme.style){VisualStyle.CLASSIC->if(interactive)12.dp else 14.dp;VisualStyle.SLEEPDOWN->28.dp;VisualStyle.ZHENGFANG->if(interactive)18.dp else 22.dp})
 return if(NextAppearance.theme.ui==UiSystem.MIUIX && backdrop!=null && isBackdropSupported())
  this.liquidChip(backdrop,shape,optics,enabled=enabled,interactive=interactive,
   appearance=if(interactive)GlassChipAppearance.Default else GlassChipAppearance.BlurredPanel,
   lightSurface=!NextAppearance.theme.effectiveDark(com.tyust.course.manager.AppThemeCoordinator.systemNight))
   .background(MaterialTheme.colorScheme.surface.copy(alpha=if(interactive).08f else if(NextAppearance.theme.style==VisualStyle.SLEEPDOWN).91f else .85f),RoundedCornerShape(if(NextAppearance.theme.style==VisualStyle.SLEEPDOWN)28.dp else 22.dp))
 else if(NextAppearance.theme.ui==UiSystem.MIUIX)this.background(MaterialTheme.colorScheme.surface,shape) else this
}
