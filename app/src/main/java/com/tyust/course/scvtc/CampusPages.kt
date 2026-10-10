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
 LazyColumn(contentPadding=PaddingValues(20.dp,12.dp,20.dp,126.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.statusBarsPadding()){
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
@Composable fun NextProfile(revision:Int,onLogin:()->Unit,onTools:()->Unit,onSchedule:()->Unit,onBack:()->Unit){
 val user=UserManager.getInstance();val status by ScvtcRuntime.status.collectAsState()
 val snapshot by produceState<ScvtcSnapshot?>(null,revision){value=withContext(Dispatchers.IO){ScvtcRuntime.snapshot()}}
 val scope=rememberCoroutineScope();var syncing by remember{mutableStateOf(false)}
 var donation by remember{mutableStateOf(false)}
 cn.scvtc.campus.CampusCompanion.initialize(androidx.compose.ui.platform.LocalContext.current)

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
  item{NextGroup{NextRow(if(syncing)"同步中…"else"刷新课表",status){if(!syncing){syncing=true;scope.launch{try{ScvtcRuntime.synchronize()}catch(e:Exception){if(e is CancellationException)throw e}finally{syncing=false}}}};snapshot?.let{NextText("最近成功取得：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.fetchedAt))}",Modifier.padding(16.dp))}}}
  item{NextGroup{cn.scvtc.campus.CloudBackupSettings(ScvtcRuntime.account,ScvtcRuntime.semester,NextAppearance.theme.ui==UiSystem.MIUIX){revision->scope.launch{cn.scvtc.campus.CloudBackup.get(ScvtcRuntime.context).restore(ScvtcRuntime.account,ScvtcRuntime.semester,revision)?.let{ScvtcRuntime.save(it)}}}}}
  item{CurrentWeekPreference()}
  item{NextGroup{NextRow("Epiphany 的赞赏码","自愿支持 · 查看与保存原图"){donation=true};NextRow("安全退出","清理网页登录态，保留账号隔离的离线课表"){ScvtcRuntime.logout()};NextText("川职·知学 ${com.tyust.course.BuildConfig.VERSION_NAME}\n维护者：zwwd1\n非学校官方客户端\n致谢项目：zhengfang-apk、OpenWakeUp 和 Miuix。许可及源码见本项目仓库。",Modifier.padding(16.dp))}}
 }
 }
}
