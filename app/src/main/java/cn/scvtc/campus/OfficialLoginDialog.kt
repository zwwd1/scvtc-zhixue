package cn.scvtc.campus

import android.app.Activity
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** One-time local configuration; the official page still owns the login protocol. */
@Composable
fun OfficialLoginDialog(mi:Boolean, web:WebView, memory:OfficialLoginMemory, initialAccount:String,
    onDismiss:()->Unit, onStarted:()->Unit) {
    var account by remember { mutableStateOf(initialAccount) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val activity=LocalContext.current as? Activity
    DisposableEffect(activity) {
        val secure=activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { password="";if(!secure)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val content:@Composable ()->Unit={
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val explanation="只需配置一次。登录成功后加密保存在本机，会话到期自动尝试恢复并继续同步。请先打开官方登录页；验证码或二次认证仍需本人完成。"
            if(mi) {
                top.yukonga.miuix.kmp.basic.Text(explanation)
                top.yukonga.miuix.kmp.basic.TextField(account,{account=it},label="学号")
                top.yukonga.miuix.kmp.basic.TextField(password,{password=it},label="学校密码",visualTransformation=PasswordVisualTransformation())
            } else {
                Text(explanation)
                OutlinedTextField(account,{account=it},label={Text("学号")},singleLine=true)
                OutlinedTextField(password,{password=it},label={Text("学校密码")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            }
            if(error.isNotBlank()){if(mi)top.yukonga.miuix.kmp.basic.Text(error)else Text(error)}
            val submit={
                if(!busy){busy=true;error="";memory.configure(web,account.trim(),password){started->
                    busy=false
                    if(started){password="";onStarted()}else error="当前官方表单未安全识别。请先打开官方账号密码登录页，再配置本机自动登录。"
                }}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(mi){
                    top.yukonga.miuix.kmp.basic.Button(onClick={password="";onDismiss()},enabled=!busy){top.yukonga.miuix.kmp.basic.Text("取消")}
                    top.yukonga.miuix.kmp.basic.Button(onClick=submit,enabled=!busy&&password.isNotBlank()&&account.matches(Regex("[0-9]{6,20}"))){top.yukonga.miuix.kmp.basic.Text(if(busy)"正在登录"else"登录并自动同步")}
                }else{
                    TextButton(onClick={password="";onDismiss()},enabled=!busy){Text("取消")}
                    Button(onClick=submit,enabled=!busy&&password.isNotBlank()&&account.matches(Regex("[0-9]{6,20}"))){Text(if(busy)"正在登录"else"登录并自动同步")}
                }
            }
        }
    }
    if(mi)top.yukonga.miuix.kmp.window.WindowDialog(true,title="本机自动登录",onDismissRequest={if(!busy)onDismiss()},content=content)
    else AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("本机自动登录")},text=content,confirmButton={},properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn))
}
