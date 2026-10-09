package com.tyust.course.scvtc

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import cn.scvtc.campus.core.School

class ScvtcWebActivity:FragmentActivity(){
 override fun attachBaseContext(base:android.content.Context){super.attachBaseContext(com.tyust.course.manager.AppThemeCoordinator.wrapContext(base))}
 override fun onCreate(state:Bundle?){super.onCreate(state);enableEdgeToEdge()
  val module=intent.getStringExtra("module")?:"schedule"
  val c=ScvtcWebSession.obtain(this).let{ScvtcWebSession.controller}
  c?.module=module;c?.begin()

  setContent {NextTheme {
   val status by ScvtcRuntime.status.collectAsState();var ask by remember{mutableStateOf(false)}
   var account by remember{mutableStateOf(ScvtcRuntime.account)};var term by remember{mutableStateOf(ScvtcRuntime.semester)}
   var menu by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
   var login by remember{mutableStateOf(false)}
   DisposableEffect(c){c?.onScopeRequired={account=c.detectedAccount.ifBlank{ScvtcRuntime.account};term=c.detectedTerm.ifBlank{ScvtcRuntime.semester};ask=true};onDispose{c?.onScopeRequired=null}}
   BackHandler{val w=ScvtcWebSession.web;if(w?.canGoBack()==true)w.goBack()else finish()}
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)){
     NextButton("返回",onClick={finish()});Column(Modifier.weight(1f).padding(8.dp)){Text(School.service(module)?.title?:"川职官方教务");Text(status,style=MaterialTheme.typography.labelSmall,maxLines=2)}
     if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX){
      val entries=listOf("一网通办" to School.PORTAL,"教务首页" to School.HOME,"个人课表" to School.SCHEDULE,"学校首页" to School.WEBSITE).map{(title,url)->top.yukonga.miuix.kmp.basic.DropdownItem(title,onClick={ScvtcWebSession.web?.loadUrl(url)})}+
       listOf(top.yukonga.miuix.kmp.basic.DropdownItem("刷新官方页",onClick={ScvtcWebSession.web?.reload()}),top.yukonga.miuix.kmp.basic.DropdownItem("本机自动登录",onClick={login=true}),top.yukonga.miuix.kmp.basic.DropdownItem("确认账号与学期",onClick={ask=true}))
      top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu(entry=top.yukonga.miuix.kmp.basic.DropdownEntry(entries)){NextText("⋮")}
     }else Box{NextButton("⋮",onClick={menu=true});DropdownMenu(menu,{menu=false}){
      listOf("一网通办" to School.PORTAL,"教务首页" to School.HOME,"个人课表" to School.SCHEDULE,"学校首页" to School.WEBSITE).forEach{(t,u)->DropdownMenuItem(text={Text(t)},onClick={menu=false;ScvtcWebSession.web?.loadUrl(u)})}
      DropdownMenuItem(text={Text("刷新官方页")},onClick={menu=false;ScvtcWebSession.web?.reload()})
      DropdownMenuItem(text={Text("确认账号与学期")},onClick={menu=false;ask=true})
      DropdownMenuItem(text={Text("本机自动登录")},onClick={menu=false;login=true})
     }}
    }
    AndroidView(factory={FrameLayout(it).also{container->ScvtcWebSession.attachVisible(this@ScvtcWebActivity,container);ScvtcWebSession.web?.loadUrl(School.HOME);intent.getStringExtra("url")?.let{url->ScvtcWebSession.web?.loadUrl(url)}}},modifier=Modifier.weight(1f).fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     if(module=="schedule"||module in cn.scvtc.campus.JwxtServices.endpoints)NextButton(if(module=="schedule")"整段同步"else"刷新本校数据",Modifier.weight(1f)){if(ScvtcRuntime.account.isBlank())ask=true else if(module=="schedule")c?.start("range")else ScvtcRuntime.refreshService(module)}
     if(module=="schedule")NextButton("逐周补齐"){if(ScvtcRuntime.account.isBlank())ask=true else c?.start("weekly")}
     if(module=="schedule")NextButton("停止"){c?.stop();ScvtcRuntime.stopNativeSync()}
    }
   }
   if(ask){
    val content:@Composable ()->Unit={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
     NextText("只用于数据隔离。请与当前已登录的学校页面一致。")
     if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX){top.yukonga.miuix.kmp.basic.TextField(account,{account=it},label="学号");top.yukonga.miuix.kmp.basic.TextField(term,{term=it},label="学期，如 2026-2027-1")}
     else{OutlinedTextField(account,{account=it},label={Text("学号")},singleLine=true);OutlinedTextField(term,{term=it},label={Text("学期，如 2026-2027-1")},singleLine=true)}
     if(error.isNotBlank())NextText(error)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){NextButton("取消"){ask=false};NextButton("确认并同步"){runCatching{ScvtcRuntime.confirm(account.trim(),term.trim())}.onSuccess{ask=false;error="";if(module=="schedule")c?.start()else if(module in cn.scvtc.campus.JwxtServices.endpoints)ScvtcRuntime.refreshService(module)}.onFailure{error=it.message.orEmpty()}}}
    }}
    if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX)top.yukonga.miuix.kmp.window.WindowDialog(true,title="确认当前官方账号与学期",onDismissRequest={ask=false},content=content)
    else AlertDialog(onDismissRequest={ask=false},title={Text("确认当前官方账号与学期")},text=content,confirmButton={})
   }
   if(login)ScvtcWebSession.web?.let{w->c?.loginMemory?.let{memory->cn.scvtc.campus.OfficialLoginDialog(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX,w,memory,ScvtcRuntime.account,{login=false},{login=false;ScvtcRuntime.status.value="已填入官方页，请完成验证码等认证提示；成功后自动同步"})}}
  }}
 }
 override fun onDestroy(){ScvtcWebSession.detachVisible(this);super.onDestroy()}
}
