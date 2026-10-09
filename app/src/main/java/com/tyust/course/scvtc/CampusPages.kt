package com.tyust.course.scvtc

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.scvtc.campus.*
import cn.scvtc.campus.core.*
import com.tyust.course.manager.UserManager
import com.tyust.course.manager.StartupPage
import com.tyust.course.scvtc.*
import com.tyust.course.schedule.*
import com.tyust.course.ui.system.*
import com.tyust.course.ui.system.glass.GlassLensFreshness
import com.tyust.course.ui.system.glass.LocalPageGlassFreshness
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.time.LocalDate
import java.time.LocalTime
import java.lang.ref.WeakReference


import com.tyust.course.BottomNavItem
@Composable fun NextHome(revision:Int,onSchedule:()->Unit,onLogin:()->Unit){
 val schoolDate=cn.scvtc.campus.rememberSchoolDate()
 val snapshot by produceState<Extraction?>(null,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.extraction()}}
 val status by ScvtcRuntime.status.collectAsState()
 val context=androidx.compose.ui.platform.LocalContext.current
 val base=remember(revision){ScheduleRepository(context).timeBase(UserManager.getInstance().currentAccountStorageKey,ScvtcRuntime.semester,ScvtcRuntime.semester)}
 val week=base.firstWeekDate.takeIf(String::isNotBlank)?.let{TeachingCalendar.weekAt(LocalDate.parse(it),schoolDate)}
 val courses=snapshot?.meetings.orEmpty();val today=schoolDate.dayOfWeek.value
 val todays=courses.filter{it.day==today && (week==null||week in it.weeks)}.sortedBy{it.startNode}
 LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,126.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.statusBarsPadding()){
  item{Row{Text("首页",Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);CampusQuickActions()};NextText(if(week==null)"川职·校园"else"${ScvtcRuntime.semester} · 第 $week 周")}
  item{NextGroup{NextRow("原生课程","保留基底课程管理"){CampusNavigation.request.value="campus.nativeCourses"};NextRow("选课与抢课","保留原生操作，需学校支持"){CampusNavigation.request.value="app.grab"};NextRow("原生成绩","直接读取本校成绩接口"){CampusNavigation.request.value="app.grades"};NextRow("插件中心","原生插件与学校适配"){CampusNavigation.request.value="campus.nativeExtensions"};NextRow("我的课表",if(snapshot==null)"完成官方登录后自动读取"else"${courses.size} 个安排 · ${snapshot!!.coverageWeeks.size} 周已读取",onSchedule);NextRow("同步与登录",status,onLogin)}}
  item{Text(if(week==null)"当前星期的课程安排"else"今日课程",style=MaterialTheme.typography.titleLarge)}
  if(week==null)item{NextText("在课表设置中填写第一周周一后，可准确显示今日课程和下一节课。")}
  if(todays.isEmpty())item{NextGroup{NextText(if(snapshot==null)"尚未取得真实课表"else"当前查看范围没有课程",Modifier.padding(20.dp))}}
  items(todays,key={it.stableId()}){m->NextGroup{NextRow(m.name,"${m.startNode}–${m.endNode} 节 · ${m.room}\n${m.teacher} · ${m.weeks.joinToString("、")} 周",onSchedule)}}
  item{NextText("首页优先读取本地课表。服务页可按实际官方菜单读取记录，认证失败保留缓存。")}
 }
}
@Composable fun NextServices(official:()->Unit){
 var query by rememberSaveable{mutableStateOf("")};var selected by rememberSaveable{mutableStateOf("")}
 var selectedTerm by rememberSaveable{mutableStateOf("all")}
 val sync by ScvtcRuntime.syncState.collectAsState()
 val context=androidx.compose.ui.platform.LocalContext.current;val revision by ScvtcRuntime.revision.collectAsState()
 val cached by produceState<Extraction?>(null,selected,revision){value=if(selected !in cn.scvtc.campus.JwxtServices.endpoints)null else withContext(Dispatchers.IO){if(selected=="grades")ScvtcRuntime.allGrades()else ScvtcRuntime.service(selected)}}
 val grades by produceState<Extraction?>(null,selected,revision){if(selected=="credits")value=withContext(Dispatchers.IO){ScvtcRuntime.allGrades()}}
 val terms by produceState<List<String>>(emptyList(),selected){if(selected=="grades"&&ScvtcRuntime.account.isNotBlank())value=ScvtcRuntime.officialSemesters()}
 val savedAt by produceState(0L,selected,revision){if(selected in cn.scvtc.campus.JwxtServices.endpoints)value=withContext(Dispatchers.IO){ScvtcRuntime.serviceSavedAt(selected)}}
 fun open(id:String,url:String?=null){context.startActivity(Intent(context,ScvtcWebActivity::class.java).putExtra("module",id).also{if(url!=null)it.putExtra("url",url)})}
 if(selected in School.visiblePrimaryServiceIds){
  androidx.activity.compose.BackHandler{selected=""}
  LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,126.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.statusBarsPadding()){
   item{Row{NextButton("返回"){selected=""};Text(School.service(selected)?.title.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)}}
   if(selected in cn.scvtc.campus.JwxtServices.endpoints) item{NextGroup{NextRow(if(sync.busy)"正在同步…"else"刷新本校数据",sync.label){
    if(!sync.busy){ScvtcRuntime.refreshService(selected);if(selected=="credits")ScvtcRuntime.refreshService("grades")}
   }}}
   if(selected=="grades")item{
    val actualTerms=(terms+cached?.records.orEmpty().mapNotNull{it.fields["学期"]}).distinct().sortedDescending()
    val options=listOf("all")+actualTerms
    NextChoice("查询学期",listOf("全部学期")+actualTerms,options.indexOf(selectedTerm).coerceAtLeast(0)){selectedTerm=options[it]}
   }
   if(selected=="credits")item{NextGroup{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    val summary=CreditSummary.from(grades?.records.orEmpty())
    NextText(if(grades==null)"已获学分：尚未取得真实成绩记录"else"成绩中已确认获得：${summary.display} 学分 · ${summary.courses} 门课程去重")
    NextText(if(summary.uncounted>0)"${summary.uncounted} 条记录缺少课程代码或获得学分，未计入汇总。"else"按课程代码合并重修尝试，取学校获得学分的最大值。")
    NextText("毕业学分要求与成绩所得分别展示；汇总不能代替学校毕业资格审核。")
   }}}
   item{NextGroup{NextRow("打开官方服务","直接使用学校官方页面"){open(selected)};val native=selected in cn.scvtc.campus.JwxtServices.endpoints;NextText(if(!native)"该模块的原生接口尚未适配，请通过官方页面使用。"else if(cached!=null)if(cached!!.emptyConfirmed)"学校接口确认当前没有记录"else"已保存 ${cached!!.records.size} 条记录"else"完成首次官方登录后自动读取；本机尚无此服务的记录。",Modifier.padding(16.dp))}}
   if(savedAt>0)item{NextText("上次成功：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(savedAt))}")}
   val visibleRecords=cached?.records.orEmpty().filter{selected!="grades"||selectedTerm=="all"||it.fields["学期"]==selectedTerm}
   if(selected=="grades"&&cached!=null&&visibleRecords.isEmpty())item{NextText("该学期没有成绩记录；完整历史记录仍保留。")}
   items(visibleRecords){record->NextGroup{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){NextText(record.title);record.fields.forEach{(key,value)->NextText("$key：$value")};if(record.link.isNotBlank())NextButton("查看官方详情"){open(selected,record.link)}}}}
   items(cached?.links.orEmpty()){link->NextGroup{NextRow(link.label,link.url){open(selected,link.url)}}}
  }
 }else LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,126.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.statusBarsPadding()){
  item{Row{Text("服务",Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);CampusQuickActions()}}
  item{if(NextAppearance.theme.ui==UiSystem.MIUIX)top.yukonga.miuix.kmp.basic.TextField(query,{query=it},label="搜索学校服务",modifier=Modifier.fillMaxWidth())else OutlinedTextField(query,{query=it},label={Text("搜索学校服务")},modifier=Modifier.fillMaxWidth())}
  val primary=School.services.filter{it.id in School.visiblePrimaryServiceIds && (it.title.contains(query)||it.aliases.any{a->a.contains(query)})}
  item{NextGroup{primary.forEach{service->NextRow(service.title,when(service.id){"schedule"->"日周课表 · 离线查看";"grades"->"按学期查询 · 保留完整记录";else->"毕业要求与官方学分记录"}){if(service.id=="schedule")official()else selected=service.id}}}}
  if(primary.isEmpty())item{NextText("没有匹配的本机服务",Modifier.padding(16.dp))}
  item{Spacer(Modifier.height(8.dp));NextGroup{NextRow("学校官方教务系统","选课、评教、论文与学籍等服务"){open("official")}};NextText("其他业务使用学校实时页面，最终确认与提交由你完成。",Modifier.padding(16.dp))}
 }
}
@Composable fun NextProfile(revision:Int,onLogin:()->Unit,onTools:()->Unit,onSchedule:()->Unit){
 val t=NextAppearance.theme;val user=UserManager.getInstance();val status by ScvtcRuntime.status.collectAsState()
 val snapshot by produceState<ScvtcSnapshot?>(null,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.snapshot()}}
 val scope=rememberCoroutineScope();var syncing by remember{mutableStateOf(false)};var detail by rememberSaveable{mutableStateOf(false)}
 var wallpaper by remember{mutableStateOf(false)}
 var donation by remember{mutableStateOf(false)}
 cn.scvtc.campus.CampusCompanion.initialize(androidx.compose.ui.platform.LocalContext.current)
 if(wallpaper)com.tyust.course.ui.screen.WallpaperSettingsDialog{wallpaper=false}
 if(donation)com.tyust.course.ui.system.GlassSubpage(onDismiss={donation=false}){close->
  Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   NextButton("返回",onClick=close);NextText("Epiphany 的赞赏码")
   cn.scvtc.campus.CampusDonationImage()
   NextText("自愿支持个人维护，不属于学校收费服务。第三方项目署名和许可仍在本项目保留。")
  }
 }
 LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,126.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.statusBarsPadding()){
  item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("我的",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);NextText(user.studentName.orEmpty().ifBlank{"川职·知学"})};CampusQuickActions()}}
  item{NextGroup{NextRow(if(user.isLoggedIn)"学校账号 ${ScvtcRuntime.account.takeLast(4).padStart(8,'•')}"else"登录学校账号",ScvtcRuntime.semester,onLogin);NextRow("课表与作息设置","周/日视图、课程编辑、调课、作息、提醒、小部件",onSchedule);NextRow("CSV / ICS / HTML","原生解析、预览、保存与导出",onTools)}}
  item{NextGroup{NextRow(if(syncing)"同步中…"else"刷新课表",status){if(!syncing){syncing=true;scope.launch{try{ScvtcRuntime.synchronize()}catch(e:Exception){if(e is CancellationException)throw e}finally{syncing=false}}}};snapshot?.let{NextText("最近成功取得：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.fetchedAt))}",Modifier.padding(16.dp))}}}
  item{NextGroup{cn.scvtc.campus.CloudBackupSettings(ScvtcRuntime.account,ScvtcRuntime.semester,NextAppearance.theme.ui==UiSystem.MIUIX){revision->scope.launch{cn.scvtc.campus.CloudBackup.get(ScvtcRuntime.context).restore(ScvtcRuntime.account,ScvtcRuntime.semester,revision)?.let{ScvtcRuntime.save(it)}}}}}
  item{CurrentWeekPreference()}
  item{Text("外观",style=MaterialTheme.typography.titleMedium)}
  item{NextGroup{
   NextRow("壁纸与裁剪","相册选择 · 等比裁剪 · 模糊与暗化 · 实时预览"){wallpaper=true}
   NextChoice("课程卡片材质",listOf("液态玻璃","高斯毛玻璃","普通透明"),t.courseMaterial){NextAppearance.update(NextAppearance.theme.copy(courseMaterial=it))}
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    NextText("课程卡片模糊 · ${t.courseCardBlur.toInt()}")
    NextSlider(t.courseCardBlur,{NextAppearance.update(NextAppearance.theme.copy(courseCardBlur=it))},0f..24f)
    NextText("课程卡片不透明度 · ${(t.courseCardOpacity*100).toInt()}%")
    NextSlider(t.courseCardOpacity,{NextAppearance.update(NextAppearance.theme.copy(courseCardOpacity=it))},.55f..1f)
    NextText("课程卡片圆角 · ${t.courseRadius.toInt()}")
    NextSlider(t.courseRadius,{NextAppearance.update(NextAppearance.theme.copy(courseRadius=it))},4f..24f)
    NextText("卡片高光 · ${(t.courseHighlight*100).toInt()}%")
    NextSlider(t.courseHighlight,{NextAppearance.update(NextAppearance.theme.copy(courseHighlight=it))},0f.. .6f)
    NextText("卡片按压动效 · ${(t.courseMotion*100).toInt()}%")
    NextSlider(t.courseMotion,{NextAppearance.update(NextAppearance.theme.copy(courseMotion=it))},0f..1f)
    NextButton("恢复卡片默认值"){NextAppearance.update(NextAppearance.theme.copy(courseMaterial=0,courseCardBlur=6f,courseCardOpacity=.78f,courseRadius=12f,courseHighlight=.35f,courseMotion=1f))}
    NextText("壁纸、课程卡片和底栏分别调整；字色与背景对比度自动适配。")
   }
  }}
  item{NextGroup{
   NextChoice("动效与布局",listOf("经典 · 阅读原底栏","正方 · 原生玻璃","SleepDown · 柔和玻璃"),t.style.ordinal){NextAppearance.update(NextAppearance.theme.copy(style=VisualStyle.entries[it]))}
   NextSwitch("显示校园助手小澄",cn.scvtc.campus.CampusCompanion.visible){cn.scvtc.campus.CampusCompanion.show(it)}
   Column(Modifier.padding(16.dp)){NextText("角色大小 · ${(cn.scvtc.campus.CampusCompanion.scale*100).toInt()}%");NextSlider(cn.scvtc.campus.CampusCompanion.scale,{cn.scvtc.campus.CampusCompanion.resize(it)},.65f..1f);cn.scvtc.campus.CampusCompanionSlot(preview=true,reduceMotion=t.reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled());NextText("微笑、侧目与眨眼待机；同步时思考，点击笑脸回应。仅在标题预留区域互动，不挡课程。")}
   NextChoice("UI 系统",listOf("Miuix","Material 3"),t.ui.ordinal){NextAppearance.update(NextAppearance.theme.copy(ui=UiSystem.entries[it]))}
   NextChoice("主题",listOf("跟随系统","浅色","深色"),t.mode.ordinal){NextAppearance.update(NextAppearance.theme.copy(mode=ThemeMode.entries[it]))}
   NextSwitch("减少动态效果",t.reduceMotion){NextAppearance.update(NextAppearance.theme.copy(reduceMotion=it))}
   NextSwitch("触觉反馈",t.haptic){NextAppearance.update(NextAppearance.theme.copy(haptic=it))}
   NextChoice("Miuix 底栏",listOf("随当前动效风格 · 滚动收缩","阅读 App 原底栏"),NextAppearance.dockStyle){NextAppearance.updateDockStyle(it)}
   NextSwitch("滚动时收缩底栏",com.tyust.course.manager.AppearanceSettingsManager.navBarAutoCollapseEnabled){com.tyust.course.manager.AppearanceSettingsManager.updateNavBarAutoCollapse(it)}
  }}
  item{NextGroup{NextRow("阅读底栏玻璃参数",if(t.ui==UiSystem.MATERIAL)"参数保留，Material 使用标准底栏"else"阅读 App 原底栏 · 实时背景采样"){detail=!detail}}}
  if(detail){
   val parameters=listOf(Triple("模糊",t.blur,0f..32f),Triple("透明度",t.opacity,0f..1f),Triple("深色透明度",t.darkOpacity,0f..1f),Triple("染色",t.tint,0f..1f),Triple("折射",t.refraction,0f..40f),Triple("高光",t.highlight,0f..1f),Triple("圆角",t.radius,12f..40f),Triple("高度",t.height,52f..80f),Triple("边距",t.margin,8f..36f),Triple("描边",t.border,0f..3f),Triple("阴影",t.shadow,0f..24f),Triple("回弹",t.spring,.5f..2f))
   items(parameters){(name,value,range)->NextGroup{Column(Modifier.padding(16.dp)){NextText("$name · ${"%.2f".format(value)}");NextSlider(value,{v->NextAppearance.update(when(name){"模糊"->NextAppearance.theme.copy(blur=v);"透明度"->NextAppearance.theme.copy(opacity=v);"深色透明度"->NextAppearance.theme.copy(darkOpacity=v);"染色"->NextAppearance.theme.copy(tint=v);"折射"->NextAppearance.theme.copy(refraction=v);"高光"->NextAppearance.theme.copy(highlight=v);"圆角"->NextAppearance.theme.copy(radius=v);"高度"->NextAppearance.theme.copy(height=v);"边距"->NextAppearance.theme.copy(margin=v);"描边"->NextAppearance.theme.copy(border=v);"阴影"->NextAppearance.theme.copy(shadow=v);else->NextAppearance.theme.copy(spring=v)})},range=range)}}}
   item{NextGroup{NextSwitch("有限色散",t.dispersion){NextAppearance.update(NextAppearance.theme.copy(dispersion=it))};NextButton("恢复玻璃默认值",Modifier.padding(16.dp)){NextAppearance.update(ThemeState(ui=t.ui,mode=t.mode,haptic=t.haptic,reduceMotion=t.reduceMotion,style=t.style,collapseDock=t.collapseDock))}}}
  }
  item{NextGroup{NextRow("Epiphany 的赞赏码","自愿支持 · 查看与保存原图"){donation=true};NextRow("安全退出","清理网页登录态，保留账号隔离的离线课表"){ScvtcRuntime.logout()};NextText("川职·知学 ${com.tyust.course.BuildConfig.VERSION_NAME}\n维护者：zwwd1\n非学校官方客户端\n致谢项目：zhengfang-apk、OpenWakeUp 和 Miuix。许可及源码见本项目仓库。",Modifier.padding(16.dp))}}
 }
}
