package cn.scvtc.campus

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.tyust.course.R

/** Generated original art, with low-frequency idle changes inside a reserved slot. */
internal object CampusCompanion {
    private var preferences: android.content.SharedPreferences? = null
    var visible by mutableStateOf(true); private set
    var scale by mutableFloatStateOf(.85f); private set
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)

    fun initialize(context: Context) {
        if (preferences != null) return
        val p = context.applicationContext.getSharedPreferences("campus_companion", 0)
        preferences = p
        visible = p.getBoolean("visible", true)
        scale = p.getFloat("scale", .85f).coerceIn(.65f, 1f)
        x = p.getFloat("x", 0f).coerceIn(-10f, 10f)
        y = p.getFloat("y", 0f).coerceIn(-3f, 3f)
    }
    fun show(value: Boolean) {
        visible = value
        preferences?.edit()?.putBoolean("visible", value)?.apply()
    }
    fun resize(value: Float) {
        scale = value.coerceIn(.65f, 1f)
        preferences?.edit()?.putFloat("scale", scale)?.apply()
    }
    fun savePosition() {
        preferences?.edit()?.putFloat("x", x)?.putFloat("y", y)?.apply()
    }
}

private enum class CompanionPose(val resource: Int, val description: String) {
    Idle(R.drawable.campus_companion_idle, "微笑待机"),
    Peek(R.drawable.campus_companion_peek, "侧目待机"),
    Rest(R.drawable.campus_companion_rest, "眨眼休息"),
    Thinking(R.drawable.campus_companion_thinking, "正在思考"),
    Greeting(R.drawable.campus_companion_greeting, "笑脸回应")
}

/** Decode off the UI thread and retain at most 2 MiB, including all five poses. */
private object CompanionImages {
    private val cache = object : LruCache<Int, ImageBitmap>(2048) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }
    suspend fun image(context: Context, resource: Int): ImageBitmap = withContext(Dispatchers.IO) {
        cache[resource] ?: run {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(context.resources, resource, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 384) {
                options.inSampleSize *= 2
            }
            requireNotNull(BitmapFactory.decodeResource(context.resources, resource, options))
                .asImageBitmap().also { cache.put(resource, it) }
        }
    }
}

@Composable
internal fun CampusCompanionSlot(
    reduceMotion: Boolean = false,
    working: Boolean = false,
    preview: Boolean = false
) {
    val context = LocalContext.current
    CampusCompanion.initialize(context)
    if (!preview && !CampusCompanion.visible) return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val motion = active && !reduceMotion
    var greeting by remember { mutableStateOf(false) }
    var idle by remember { mutableStateOf(CompanionPose.Idle) }
    var inhale by remember { mutableStateOf(false) }
    val breathe = animateFloatAsState(
        if (motion && inhale) 1.018f else 1f,
        if (motion) tween(1800) else snap(), label = "companion-breath"
    )
    val response = animateFloatAsState(
        if (motion && greeting) .96f else 1f,
        if (motion) tween(160) else snap(), label = "companion-greeting"
    )
    LaunchedEffect(active, greeting) {
        if (greeting) { delay(1000); greeting = false }
    }
    LaunchedEffect(motion) {
        inhale = false
        if (motion) while (true) { delay(1900); inhale = !inhale }
    }
    LaunchedEffect(active, reduceMotion, working) {
        idle = CompanionPose.Idle
        if (active && !reduceMotion && !working) while (true) {
            delay(6500); idle = CompanionPose.Rest
            delay(180); idle = CompanionPose.Idle
            delay(5000); idle = CompanionPose.Peek
            delay(2300); idle = CompanionPose.Idle
            delay(6500); idle = CompanionPose.Rest
            delay(750); idle = CompanionPose.Idle
        }
    }
    val pose = when {
        greeting -> CompanionPose.Greeting
        working -> CompanionPose.Thinking
        else -> idle
    }
    val sprite by produceState<ImageBitmap?>(null, pose, context) {
        value = CompanionImages.image(context.applicationContext, pose.resource)
    }
    val slotWidth = if (preview) 180.dp else 50.dp
    val slotHeight = if (preview) 184.dp else 54.dp
    Box(Modifier.width(slotWidth).height(slotHeight), contentAlignment = Alignment.Center) {
        sprite?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(slotWidth * CampusCompanion.scale, slotHeight * CampusCompanion.scale)
                    .offset(CampusCompanion.x.dp, CampusCompanion.y.dp)
                    .graphicsLayer {
                        scaleX = response.value
                        scaleY = response.value * breathe.value
                    }
                    .semantics {
                        contentDescription = "校园助手小澄，点击打招呼，可在标题区拖动"
                        stateDescription = pose.description
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { CampusCompanion.savePosition() }) { change, drag ->
                            change.consume()
                            CampusCompanion.x = (CampusCompanion.x + drag.x / density).coerceIn(-10f, 10f)
                            CampusCompanion.y = (CampusCompanion.y + drag.y / density).coerceIn(-3f, 3f)
                        }
                    }
                    .clickable { greeting = true }
            )
        }
    }
}
