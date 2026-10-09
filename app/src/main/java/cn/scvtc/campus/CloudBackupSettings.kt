package cn.scvtc.campus
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import top.yukonga.miuix.kmp.basic.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.VisualTransformation
private val LocalBackupMiuix=staticCompositionLocalOf{true}
@Composable private fun Text(text:String){if(LocalBackupMiuix.current)top.yukonga.miuix.kmp.basic.Text(text)else androidx.compose.material3.Text(text)}
@Composable private fun Button(onClick:()->Unit,content:@Composable RowScope.()->Unit){if(LocalBackupMiuix.current)top.yukonga.miuix.kmp.basic.Button(onClick=onClick){Row(content=content)}else androidx.compose.material3.Button(onClick=onClick,content=content)}
@Composable private fun TextField(value:String,onValueChange:(String)->Unit,label:String,visualTransformation:VisualTransformation=VisualTransformation.None){if(LocalBackupMiuix.current)top.yukonga.miuix.kmp.basic.TextField(value,onValueChange,label=label,visualTransformation=visualTransformation)else androidx.compose.material3.OutlinedTextField(value,onValueChange,label={Text(label)},visualTransformation=visualTransformation,modifier=Modifier.fillMaxWidth())}
@Composable fun CloudBackupSettings(account:String,semester:String,mi:Boolean,onRestore:(String?)->Unit){
 if(CloudBackup.PAUSED){CompositionLocalProvider(LocalBackupMiuix provides mi){Column(Modifier.fillMaxWidth().padding(16.dp)){Text("云服务已暂停");Text("课表与设置仅保存在本机，可导出本地备份。")}};return}
 val c=LocalContext.current;val backup=remember(c){CloudBackup.get(c)};val scope=rememberCoroutineScope()
 var revisions by remember(account,semester){mutableStateOf(emptyList<CloudBackup.Revision>())}
 val status by backup.status.collectAsState();var expanded by remember{mutableStateOf(false)}
 val config=remember{backup.config()};var url by remember{mutableStateOf(config?.endpoint.orEmpty())};var token by remember{mutableStateOf(config?.token.orEmpty())};var phrase by remember{mutableStateOf(config?.phrase.orEmpty())};var error by remember{mutableStateOf("")}
 CompositionLocalProvider(LocalBackupMiuix provides mi){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("自动加密云备份");Text(status)
  Button(onClick={expanded=!expanded}){Text(if(expanded)"收起配置"else"配置自己的云服务")}
  if(expanded){
   Text("成功取得课表后自动加密上传，失败保留本地并后台重试。学校密码和 Cookie 不上传；云服务只需配置一次。")
   TextField(value=url,onValueChange={url=it},label="HTTPS 云服务地址")
   TextField(value=token,onValueChange={token=it},label="云备份令牌",visualTransformation=PasswordVisualTransformation())
   TextField(value=phrase,onValueChange={phrase=it},label="恢复密钥（两版填相同值）",visualTransformation=PasswordVisualTransformation())
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(onClick={phrase=backup.newPhrase()}){Text("生成恢复密钥")}
    Button(onClick={(c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("云备份恢复密钥",phrase));android.widget.Toast.makeText(c,"恢复密钥已复制",android.widget.Toast.LENGTH_SHORT).show()}){Text("复制密钥")}
   }
   Button(onClick={runCatching{backup.configure(url,token,phrase)}.onSuccess{error=""}.onFailure{error=it.message.orEmpty()}}){Text("保存配置")}
   Text("恢复密钥须自行保存；更换手机或重装后需要相同密钥才能解密。")
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={onRestore(null)}){Text("恢复最新")};Button(onClick={scope.launch{runCatching{backup.history(account,semester)}.onSuccess{revisions=it;error=if(it.isEmpty())"当前账号/学期尚无历史版本"else""}.onFailure{error=it.message.orEmpty()}}}){Text("历史版本")}}
   revisions.forEach{revision->val date=java.time.Instant.ofEpochMilli(revision.created).atZone(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));Button(onClick={onRestore(revision.etag)}){Text("恢复 $date")}}
   Button(onClick={backup.disable();revisions=emptyList()}){Text("关闭备份")}
   if(error.isNotBlank())Text(error)
  }
 }}
}
