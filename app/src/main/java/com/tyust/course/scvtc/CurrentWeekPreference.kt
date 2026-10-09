package com.tyust.course.scvtc
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import cn.scvtc.campus.core.TeachingCalendar
import com.tyust.course.manager.UserManager
import com.tyust.course.schedule.*
import java.time.LocalDate
@Composable fun CurrentWeekPreference(){
 var open by remember{mutableStateOf(false)};var week by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 val date=TeachingCalendar.today();val monday=week.toIntOrNull()?.takeIf{it in 1..100}?.let{TeachingCalendar.firstMonday(date,it)}
 NextGroup{NextRow("自动匹配首周日期","按今天 $date 与实际教学周倒推"){open=true}}
 if(open)com.tyust.course.ui.system.SystemDialog(onDismissRequest={open=false},title={Text("匹配教学日期")},content={Column{
  NextText("请输入学校显示的今天教学周；查看其他周次不会改变今天所属周。")
  if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX)top.yukonga.miuix.kmp.basic.TextField(week,{week=it.filter(Char::isDigit)},label="今天第几周")else OutlinedTextField(week,{week=it.filter(Char::isDigit)},label={Text("今天第几周")})
  if(monday!=null)NextText("第一周周一：$monday")
  if(week.isNotBlank() && monday==null)NextText("请输入 1–100 之间的教学周次")
  if(error.isNotBlank())NextText(error)
 }},confirmButton={NextButton("确认"){if(monday!=null){runCatching{ScvtcRuntime.setFirstMonday(monday)}.onSuccess{open=false;error=""}.onFailure{error=it.message.orEmpty()}}}},dismissButton={NextButton("取消"){open=false}})
}
