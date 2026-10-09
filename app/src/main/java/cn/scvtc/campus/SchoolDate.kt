package cn.scvtc.campus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.scvtc.campus.core.TeachingCalendar
import kotlinx.coroutines.delay
import java.time.*

/** Civil school date refreshes on resume, clock changes and the next Shanghai midnight. */
@Composable fun rememberSchoolDate(): LocalDate {
    var date by remember { mutableStateOf(TeachingCalendar.today()) }
    var clockRevision by remember { mutableIntStateOf(0) }
    val context=LocalContext.current.applicationContext
    val owner=LocalLifecycleOwner.current
    DisposableEffect(context,owner) {
        val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){date=TeachingCalendar.today();clockRevision++}}
        context.registerReceiver(receiver,IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED)
        },Context.RECEIVER_NOT_EXPORTED)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){date=TeachingCalendar.today();clockRevision++}}
        owner.lifecycle.addObserver(observer)
        onDispose{context.unregisterReceiver(receiver);owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(date,clockRevision) {
        val now=ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        delay(Duration.between(now,now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1000))
        date=TeachingCalendar.today()
    }
    return date
}
