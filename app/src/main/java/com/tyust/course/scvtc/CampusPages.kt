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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
 LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,LocalAppOverlayBottomInset.current+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.statusBarsPadding()){
  item{Row{Text("首页",Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);CampusQuickActions()};NextText(if(week==null)"川职·校园"else"${ScvtcRuntime.semester} · 第 $week 周")}
  item{NextGroup{NextRow("成绩与考试","查看成绩卡片、学期汇总与考试安排"){CampusNavigation.request.value="app.grades"}}}
  item{NextGroup{NextRow("我的课表",if(snapshot==null)"完成官方登录后自动读取"else"${courses.size} 个安排 · ${snapshot!!.coverageWeeks.size} 周已读取",onSchedule);NextRow("教务同步",status){CampusNavigation.request.value="profile"}}}
  item{NextGroup{NextRow("AI 助手小澄","学习答疑 · 可选择使用本机课表"){context.startActivity(Intent(context,CampusAiActivity::class.java))}}}
  item{Text(if(week==null)"当前星期的课程安排"else"今日课程",style=MaterialTheme.typography.titleLarge)}
  if(week==null)item{NextText("在课表设置中填写第一周周一后，可准确显示今日课程和下一节课。")}
  if(todays.isEmpty())item{NextGroup{NextText(if(snapshot==null)"尚未取得真实课表"else"当前查看范围没有课程",Modifier.padding(20.dp))}}
  items(todays,key={it.stableId()}){m->NextGroup{NextRow(m.name,"${m.startNode}–${m.endNode} 节 · ${m.room}\n${m.teacher} · ${m.weeks.joinToString("、")} 周",onSchedule)}}
  item{NextText("课表离线可用。学校会话失效时自动尝试恢复，成功后继续同步。")}
 }
}
@Composable fun NextServices(official:()->Unit){
 var query by rememberSaveable{mutableStateOf("")}
 var creditsOpen by rememberSaveable{mutableStateOf(false)}
 val context=androidx.compose.ui.platform.LocalContext.current
 val revision by ScvtcRuntime.revision.collectAsState()
 val sync by ScvtcRuntime.syncState.collectAsState()
 val account=ScvtcRuntime.account
 val bottom=LocalAppOverlayBottomInset.current+24.dp
 fun open(id:String){context.startActivity(Intent(context,ScvtcWebActivity::class.java).putExtra("module",id))}
 if(creditsOpen){
  androidx.activity.compose.BackHandler{creditsOpen=false}
  val requirement by produceState<Extraction?>(null,account,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.service("credits")}}
  val grades by produceState<Extraction?>(null,account,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.allGrades()}}
  val savedAt by produceState(0L,account,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.serviceSavedAt("credits")}}
  val summary=remember(grades){CreditSummary.from(grades?.records.orEmpty())}
  val target=requirement?.records?.firstOrNull()?.fields?.get("毕业学分要求").orEmpty()
  Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,topBar={
   SystemTopBar("学分概况",navigationIcon={SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack,"返回服务中心",{creditsOpen=false})},actions={CampusQuickActions()})
  }){padding->
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=padding.calculateTopPadding()+12.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(16.dp)){
    item{NextGroup{Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("成绩中已确认获得",style=MaterialTheme.typography.titleMedium)
     Text(if(grades==null)"--" else "${summary.display} 学分",style=MaterialTheme.typography.headlineLarge)
     NextText("学校毕业学分要求："+target.ifBlank{"尚未取得"})
     val required=target.toBigDecimalOrNull()
     if(grades!=null && required!=null && required.signum()>0)
      NextText("距离学分要求还差 ${(required-summary.confirmed).max(java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString()} 学分")
     NextText("${summary.courses} 门课程按课程代码合并重修记录，取官方获得学分的最大值。")
     if(summary.uncounted>0)NextText("${summary.uncounted} 条记录缺少课程代码或获得学分，未计入汇总。")
     Text("学分比较不代表学校毕业资格审核通过。",style=MaterialTheme.typography.bodySmall)
    }}}
    item{NextGroup{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     NextText(sync.label.ifBlank{"本机缓存可离线查看"})
     if(savedAt>0)Text("毕业要求最近成功同步："+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(savedAt)),style=MaterialTheme.typography.bodySmall)
     SystemPrimaryButton(if(sync.busy)"正在同步…"else"刷新成绩与学分",{
      ScvtcRuntime.refreshService("credits");ScvtcRuntime.refreshService("grades")
     },enabled=!sync.busy && account.isNotBlank(),modifier=Modifier.fillMaxWidth())
     NextRow("查看完整成绩","按学期查找、筛选与导出"){CampusNavigation.request.value="app.grades";creditsOpen=false}
     NextRow("学校官方学分页面","查看学校毕业要求与审核说明"){open("credits")}
    }}}
   }
  }
 }else LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,bottom),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.statusBarsPadding()){
  item{Row{Text("服务",Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);CampusQuickActions()}}
  item{GlassTextField(query,{query=it},Modifier.fillMaxWidth(),"搜索学校服务",leadingIcon=Icons.Outlined.Search)}
  val search=query.trim()
  val primary=School.services.filter{it.id in School.visiblePrimaryServiceIds && (it.title.contains(search,true)||it.aliases.any{a->a.contains(search,true)})}
  if(primary.isNotEmpty())item{NextGroup{primary.forEach{service->NextRow(service.title,when(service.id){
   "schedule"->"日周课表 · 离线查看";"grades"->"成绩卡片 · 学期汇总 · 筛选导出";else->"已获学分与学校毕业要求"
  }){when(service.id){"schedule"->official();"grades"->CampusNavigation.request.value="app.grades";"credits"->creditsOpen=true}}}}}
  else item{SystemEmptyState("没有匹配的服务","试试“课表”“成绩”或“学分”。",action={SystemSecondaryButton("清空搜索",{query=""})})}
  item{NextGroup{NextRow("学校官方教务系统","选课、评教、论文与学籍等服务"){open("official")}};NextText("其他业务使用学校实时页面，最终确认与提交由你完成。",Modifier.padding(16.dp))}
 }
}
@Composable fun NextProfile(revision:Int,onLogin:()->Unit,onTools:()->Unit,onSchedule:()->Unit,onBack:()->Unit){
 val user=UserManager.getInstance();val status by ScvtcRuntime.status.collectAsState()
 val snapshot by produceState<ScvtcSnapshot?>(null,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.snapshot()}}
 val scope=rememberCoroutineScope()
 val sync by ScvtcRuntime.syncState.collectAsState()
 var confirmLogout by remember{mutableStateOf(false)}
 var donation by remember{mutableStateOf(false)}
 cn.scvtc.campus.CampusCompanion.initialize(androidx.compose.ui.platform.LocalContext.current)

 if(confirmLogout)SystemDialog(onDismissRequest={confirmLogout=false},title={Text("退出学校账号？")},
  confirmButton={SystemDestructiveButton("退出账号",{confirmLogout=false;ScvtcRuntime.logout();onBack()})},
  dismissButton={SystemSecondaryButton("继续使用",{confirmLogout=false})}){
  NextText("网页登录状态会清除，账号隔离的离线课表和成绩仍保留在本机。再次联网同步需要恢复学校认证。")
 }
 if(donation)com.tyust.course.ui.system.GlassSubpage(onDismiss={donation=false}){close->
  Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   NextButton("返回",onClick=close);NextText("Epiphany 的赞赏码")
   cn.scvtc.campus.CampusDonationImage()
   NextText("自愿支持个人维护，不属于学校收费服务。第三方项目署名和许可仍在本项目保留。")
  }
 }
 Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,topBar={SystemTopBar(title="教务同步",navigationIcon={SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack,"返回",onBack)},actions={CampusQuickActions()})}){padding->
 LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=padding.calculateTopPadding()+12.dp,bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{NextGroup{NextRow(if(user.isLoggedIn)"学校账号 ${ScvtcRuntime.account.takeLast(4).padStart(8,'•')}"else"登录学校账号",ScvtcRuntime.semester,onLogin);NextRow("课表与作息设置","周/日视图、课程编辑、调课、作息、提醒、小部件",onSchedule);NextRow("CSV / ICS / HTML","原生解析、预览、保存与导出",onTools)}}
  item{NextGroup{NextRow(if(sync.busy)"同步中…"else"刷新课表",if(sync.busy)sync.label else status){if(!sync.busy)ScvtcRuntime.startNativeSync()};snapshot?.let{NextText("最近成功取得：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.fetchedAt))}",Modifier.padding(16.dp))}}}
  item{NextGroup{cn.scvtc.campus.CloudBackupSettings(ScvtcRuntime.account,ScvtcRuntime.semester,NextAppearance.theme.ui==UiSystem.MIUIX){revision->scope.launch{cn.scvtc.campus.CloudBackup.get(ScvtcRuntime.context).restore(ScvtcRuntime.account,ScvtcRuntime.semester,revision)?.let{ScvtcRuntime.save(it)}}}}}
  item{CurrentWeekPreference()}
  item{NextGroup{NextRow("Epiphany 的赞赏码","自愿支持 · 查看与保存原图"){donation=true};NextRow("安全退出","清理网页登录态，保留账号隔离的离线课表"){confirmLogout=true};NextText("川职·知学 ${com.tyust.course.BuildConfig.VERSION_NAME}\n维护者：zwwd1\n非学校官方客户端\n致谢项目：zhengfang-apk、OpenWakeUp 和 Miuix。许可及源码见本项目仓库。",Modifier.padding(16.dp))}}
 }
 }
}
