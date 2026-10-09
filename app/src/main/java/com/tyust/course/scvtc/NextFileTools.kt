package com.tyust.course.scvtc

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.scvtc.campus.core.*
import com.openwakeup.parser.CoursePreview
import com.openwakeup.parser.ParserInput
import com.openwakeup.parser.csv.CsvParser
import com.openwakeup.parser.ics.*
import com.tyust.course.manager.UserManager
import com.tyust.course.manager.ScheduleSettingsManager
import com.tyust.course.schedule.ScheduleRepository
import kotlinx.coroutines.*
import java.time.*

@Composable fun NextFileTools(official:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var format by remember{mutableIntStateOf(0)};var account by remember{mutableStateOf(ScvtcRuntime.account)};var term by remember{mutableStateOf(ScvtcRuntime.semester)}
 var preview by remember{mutableStateOf<Extraction?>(null)};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var export by remember{mutableStateOf("")}
 val settings=remember{ScheduleSettingsManager.getInstance().apply{init(context)}}
 fun base()=ScheduleRepository(context).timeBase(UserManager.getInstance().currentAccountStorageKey,term,term)
 fun clocks():List<CourseFormats.Clock>{val b=base();return settings.getPeriodTimes().mapNotNull{runCatching{CourseFormats.Clock(it.period,LocalTime.parse(b?.periodStarts?.get(it.period)?:it.startTime),LocalTime.parse(b?.periodEnds?.get(it.period)?:it.endTime))}.getOrNull()}}
 fun monday():LocalDate?=base()?.firstWeekDate?.takeIf{it.isNotBlank()}?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
 val input=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
  busy=true;preview=null
  try{
   val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:error("文件无法读取")}
   val slots=clocks();val date=monday()
   val meetings=withContext(Dispatchers.Default){when(format){
    0->fromPreviews(CsvParser().parse(ParserInput(text,"csv")))
    1->fromPreviews(IcsParser(slots.map{IcsTimeSlot(it.node,it.start,it.end)},ZoneId.of("Asia/Shanghai")).parse(text,date,maxWeek=100).courses)
    else->ScvtcParser.html(text,School.SCHEDULE,"schedule").also{require(it.warnings.isEmpty()){it.warnings.joinToString("；")}}.meetings
   }}
   require(meetings.isNotEmpty()){"文件没有可导入课程"};preview=Extraction("schedule",semester=term,source="file:/${listOf("csv","ics","html")[format]}",meetings=meetings,coverageWeeks=meetings.flatMap{it.weeks}.distinct().sorted());message="已解析 ${meetings.size} 个安排，确认后保存"
  }catch(e:Exception){message=e.message.orEmpty()}finally{busy=false}
 }}
 val output=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null)scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(export.toByteArray(Charsets.UTF_8))}?:error("无法写入文件")};message="文件已导出"}catch(e:Exception){message=e.message.orEmpty()}}}
 LazyColumn(contentPadding=PaddingValues(20.dp,8.dp,20.dp,40.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{NextGroup{NextRow("从官方课表同步","使用当前手机的网页登录态",official)}}
  item{NextGroup{NextChoice("文件格式",listOf("CSV","ICS","HTML"),format){format=it;preview=null};NextRow("选择文件并解析","UTF-8 · 保存前预览"){if(!busy)input.launch(arrayOf("*/*"))}}}
  item{NextGroup{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(account,{account=it},label={Text("当前账号学号")},singleLine=true)
   OutlinedTextField(term,{term=it},label={Text("文件所属学期")},singleLine=true)
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   Text(message)
   preview?.let{p->p.meetings.take(5).forEach{m->Text("${m.name} · 周${m.day} · ${m.startNode}–${m.endNode}节 · ${m.weeks.joinToString("、")}周")}
    NextButton("确认保存 ${p.meetings.size} 个安排"){if(!busy){busy=true;scope.launch{try{
     ScvtcRuntime.confirm(account.trim(),term.trim());val user=UserManager.getInstance();if(!user.isLoggedIn ||user.studentId!=account)user.saveOfflineProfile(account.trim())
     withContext(Dispatchers.IO){ScvtcRuntime.save(p.copy(account=account.trim(),semester=term.trim()))};preview=null;message="已保存，返回原生课表即可查看"
    }catch(e:Exception){message=e.message.orEmpty()}finally{busy=false}}}}
   }
  }}}
  item{NextGroup{NextRow("导出当前课表","CSV / HTML 保留周次节次；ICS 使用已配置的开学日期与作息"){
   scope.launch{try{val meetings=withContext(Dispatchers.IO){ScvtcRuntime.extraction()?.meetings.orEmpty()};require(meetings.isNotEmpty()){"尚无真实课程"};val date=monday();val slots=clocks();export=withContext(Dispatchers.Default){when(format){0->CourseFormats.csv(meetings);1->CourseFormats.ics(meetings,date?:error("请先在课表设置里填写第一周周一"),slots);else->CourseFormats.html(meetings,ScvtcRuntime.semester)}};output.launch("川职课表-${ScvtcRuntime.semester}.${listOf("csv","ics","html")[format]}")}catch(e:Exception){message=e.message.orEmpty()}}
  }}}
 }
}
private fun fromPreviews(items:List<CoursePreview>)=items.map{m->Meeting(name=m.name,teacher=m.teacher,room=m.room,day=m.day,startNode=m.startNode,endNode=m.startNode+m.step-1,weeks=(m.startWeek..m.endWeek).filter{m.type==0 ||m.type==1&&it%2==1 ||m.type==2&&it%2==0})}.groupBy{listOf(it.name,it.teacher,it.room,it.day,it.startNode,it.endNode)}.values.map{group->group.last().copy(weeks=group.flatMap{it.weeks}.distinct().sorted())}
