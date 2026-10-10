package com.tyust.course.scvtc

import android.os.Bundle
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import cn.scvtc.campus.core.School

class ScvtcWebActivity:FragmentActivity(){
 private var pendingFile:ValueCallback<Array<Uri>>?=null
 private val filePicker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  pendingFile?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data));pendingFile=null
 }
 override fun attachBaseContext(base:android.content.Context){super.attachBaseContext(com.tyust.course.manager.AppThemeCoordinator.wrapContext(base))}
 override fun onCreate(state:Bundle?){
  super.onCreate(state);enableEdgeToEdge()
  val module=intent.getStringExtra("module")?:"official"
  val c=ScvtcWebSession.obtain(this,intent.getStringExtra("url")).let{ScvtcWebSession.controller}
  c?.authenticationAccount=intent.getStringExtra("expected_account").orEmpty()
  c?.module=module;c?.begin()
  ScvtcWebSession.fileChooser={callback,parameters->
   pendingFile?.onReceiveValue(null);pendingFile=callback
   try{filePicker.launch(parameters.createIntent())}catch(_:android.content.ActivityNotFoundException){
    pendingFile?.onReceiveValue(null);pendingFile=null
    ScvtcWebSession.page.value=ScvtcWebSession.page.value.copy(error="系统没有可用的文件选择器")
   }
   true
  }
  setContent{NextTheme{
   LaunchedEffect(c) {
    if(intent.getBooleanExtra("verification_only",false)) c?.authenticationVerified?.collect { verified ->
     if(verified) { setResult(RESULT_OK);finish() }
    }
   }
   val page by ScvtcWebSession.page.collectAsState()
   var full by rememberSaveable{mutableStateOf(false)}
   var ask by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)};var login by remember{mutableStateOf(false)}
   var account by remember{mutableStateOf(ScvtcRuntime.account)};var term by remember{mutableStateOf(ScvtcRuntime.semester)};var error by remember{mutableStateOf("")}
   fun back(){val w=ScvtcWebSession.web;if(w?.canGoBack()==true)w.goBack()else finish()}
   fun openBrowser(){ScvtcWebSession.web?.let{ScvtcWebSession.external(it,it.url?:School.HOME)}}
   DisposableEffect(c){c?.onScopeRequired={account=c.detectedAccount.ifBlank{ScvtcRuntime.account};term=c.detectedTerm.ifBlank{ScvtcRuntime.semester};ask=true};onDispose{c?.onScopeRequired=null}}
   BackHandler{if(full)full=false else back()}
   Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
    Column(Modifier.fillMaxSize()){
     if(!full){
      Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
       NextButton("返回"){back()}
       Column(Modifier.weight(1f).padding(8.dp)){NextText(page.title);Text(if(page.error.isNotBlank())page.error else if(page.loading)"正在打开官方页面…"else "学校官方页面",style=MaterialTheme.typography.labelSmall,maxLines=2)}
       val actions=listOf<Pair<String,()->Unit>>(
        "返回教务首页" to {ScvtcWebSession.navigate(School.HOME)},
        "一网通办" to {ScvtcWebSession.navigate(School.PORTAL)},
        "刷新当前页面" to {ScvtcWebSession.web?.reload()},
        "重新认证" to {ScvtcWebSession.navigate(cn.scvtc.campus.CasAuthManager.JWXT_SSO_ENTRY)},
        "本机自动登录" to {login=true},
        "全屏浏览" to {full=true},
        "在系统浏览器打开" to {openBrowser()}
       )
       if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX){
        val entries=actions.map{(title,action)->top.yukonga.miuix.kmp.basic.DropdownItem(title,onClick=action)}
        top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu(entry=top.yukonga.miuix.kmp.basic.DropdownEntry(entries)){NextText("⋮")}
       }else Box{NextButton("⋮"){menu=true};DropdownMenu(menu,{menu=false}){actions.forEach{(title,action)->DropdownMenuItem(text={Text(title)},onClick={menu=false;action()})}}}
      }
     }
     // Attach the existing document once. Recomposition and returning never reload HOME.
     AndroidView(factory={FrameLayout(it).also{container->ScvtcWebSession.attachVisible(this@ScvtcWebActivity,container)}},modifier=Modifier.weight(1f).fillMaxWidth())
     if(!full&&module=="schedule"){
      Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
       NextButton("同步课表",Modifier.weight(1f)){if(ScvtcRuntime.account.isBlank())ask=true else c?.start("range")}
       NextButton("停止"){c?.stop();ScvtcRuntime.stopNativeSync()}
      }
     }
    }
    if(full)NextButton("退出全屏",Modifier.align(Alignment.TopEnd).padding(8.dp)){full=false}
   }
   if(ask){
    val content:@Composable ()->Unit={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
     NextText("只用于账号和学期隔离，请与已登录的学校账号一致。")
     if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX){top.yukonga.miuix.kmp.basic.TextField(account,{account=it},label="学号");top.yukonga.miuix.kmp.basic.TextField(term,{term=it},label="学期")}
     else{OutlinedTextField(account,{account=it},label={Text("学号")},singleLine=true);OutlinedTextField(term,{term=it},label={Text("学期")},singleLine=true)}
     if(error.isNotBlank())NextText(error)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){NextButton("取消"){ask=false};NextButton("确认并同步"){runCatching{ScvtcRuntime.confirm(account.trim(),term.trim())}.onSuccess{ask=false;error="";c?.start()}.onFailure{error=it.message.orEmpty()}}}
    }}
    if(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX)top.yukonga.miuix.kmp.window.WindowDialog(true,title="确认学校账号与学期",onDismissRequest={ask=false},content=content)
    else AlertDialog(onDismissRequest={ask=false},title={Text("确认学校账号与学期")},text=content,confirmButton={})
   }
   if(login)ScvtcWebSession.web?.let{w->c?.loginMemory?.let{memory->cn.scvtc.campus.OfficialLoginDialog(NextAppearance.theme.ui==cn.scvtc.campus.UiSystem.MIUIX,w,memory,ScvtcRuntime.account,{login=false},{login=false})}}
  }}
 }
 override fun onDestroy(){pendingFile?.onReceiveValue(null);pendingFile=null;ScvtcWebSession.detachVisible(this);super.onDestroy()}
}
