package cn.scvtc.campus

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import java.io.IOException

/** The original PNG is supplied privately at build time, never regenerated. */
@Composable
internal fun CampusDonationImage() {
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null){value=withContext(Dispatchers.IO){
        try{context.assets.open("epiphany-donation.png").use{BitmapFactory.decodeStream(it)}}catch(_:IOException){null}
    }}
    var expanded by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    bitmap?.let{image->
        Image(image.asImageBitmap(),"Epiphany 的赞赏码原图",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).clickable{expanded=true},contentScale=ContentScale.Fit)
        androidx.compose.material3.TextButton(onClick={scope.launch{
            message=withContext(Dispatchers.IO){
                val resolver=context.contentResolver
                val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{
                    put(MediaStore.Images.Media.DISPLAY_NAME,"Epiphany-赞赏码.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/川职");put(MediaStore.Images.Media.IS_PENDING,1)
                })
                if(uri==null)"未能保存原图" else try{
                    context.assets.open("epiphany-donation.png").use{input->resolver.openOutputStream(uri)?.use{input.copyTo(it)}?:throw IOException()}
                    resolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
                    "原图已保存，可在微信扫一扫中从相册选择"
                }catch(_:IOException){resolver.delete(uri,null,null);"保存未完成，请重试"}
            }
        }}){Text("保存原图到相册")}
    }?:Text("此构建未提供赞赏码。自愿赞赏不影响任何功能。",Modifier.padding(16.dp))
    if(message.isNotBlank())Text(message,Modifier.padding(8.dp))
    if(expanded)Dialog({expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        bitmap?.let{Image(it.asImageBitmap(),"赞赏码原图，点按关闭",Modifier.fillMaxWidth().padding(16.dp).clickable{expanded=false},contentScale=ContentScale.Fit)}
    }
}
