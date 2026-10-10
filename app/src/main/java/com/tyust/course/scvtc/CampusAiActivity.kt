package com.tyust.course.scvtc

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cn.scvtc.campus.CampusCompanionSlot
import com.tyust.course.manager.UserManager
import com.tyust.course.ui.system.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CampusAiActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { NextTheme { GlassWindowHost { CampusAssistant(onBack = ::finish) } } }
    }
}

internal data class AssistantMessage(val role: String, val content: String)
internal data class AssistantConfig(val endpoint: String = "", val model: String = "", val apiKey: String = "") {
    fun json() = JSONObject().put("endpoint", endpoint).put("model", model).put("key", apiKey)
}

/** No school cookies, logging interceptor, redirect or built-in provider key is attached. */
private object AssistantApi {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS).build()
    fun endpoint(value: String): HttpUrl {
        val base = value.trim().toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) { "请填写不带账号、查询参数或片段的 HTTPS 服务地址" }
        return if (base.encodedPath.endsWith("/chat/completions")) base
        else base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/chat/completions").build()
    }
    suspend fun answer(config: AssistantConfig, history: List<AssistantMessage>, courseContext: String): String {
        require(config.model.isNotBlank() && config.apiKey.isNotBlank()) { "请先配置模型与 API Key" }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "你是川职校园学习助手小澄。回答简洁准确，不能编造学校记录、成绩或通知；你无权修改教务数据。" + courseContext))
        history.takeLast(30).forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", config.model.trim()).put("messages", messages).put("stream", false)
        val request = Request.Builder().url(endpoint(config.endpoint)).header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("AI 服务连接失败，请检查地址或网络")) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw IOException(when (response.code) {
                                401, 403 -> "AI 服务拒绝认证，请检查密钥或权限"
                                429 -> "AI 服务额度不足或请求过多，请稍后重试"
                                in 300..399 -> "服务地址发生跳转，请直接填写 HTTPS 接口地址"
                                else -> "AI 服务暂不可用（HTTP ${response.code}）"
                            })
                            val input = response.body?.byteStream() ?: throw IOException("AI 服务没有返回内容")
                            val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192)
                            while (true) {
                                val count = input.read(chunk); if (count < 0) break
                                if (buffer.size() + count > 2 * 1024 * 1024) throw IOException("AI 回答超出读取范围")
                                buffer.write(chunk, 0, count)
                            }
                            val text = JSONObject(buffer.toString("UTF-8")).getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim()
                            if (text.isEmpty()) throw IOException("AI 服务返回了空回答")
                            if (continuation.isActive) continuation.resume(text)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(IOException(
                                if (error is IOException) error.message else "AI 服务返回格式与 Chat Completions 不兼容"))
                        }
                    }
                }
            })
        }
    }
}

@Composable
private fun CampusAssistant(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val account = UserManager.getInstance().currentAccountStorageKey
    val store = remember(context) { AssistantStore(context) }; val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(AssistantConfig()) }
    var settings by remember { mutableStateOf(false) }; var loaded by remember { mutableStateOf(false) }
    var messages by remember(account) { mutableStateOf(emptyList<AssistantMessage>()) }
    var input by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    var includeCourses by remember(account) { mutableStateOf(false) }; var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }; val list = rememberLazyListState()
    var settingsError by remember { mutableStateOf("") }
    DisposableEffect(account) { onDispose { job?.cancel() } }
    LaunchedEffect(account) {
        try {
            val saved = withContext(Dispatchers.IO) { store.read("configuration") }
            config = AssistantConfig(saved.optString("endpoint"), saved.optString("model"), saved.optString("key"))
            val history = withContext(Dispatchers.IO) { store.read("history:$account").optJSONArray("messages") }
            messages = (0 until (history?.length() ?: 0)).map { index -> history!!.getJSONObject(index).let { AssistantMessage(it.getString("role"), it.getString("content")) } }
        } catch (e: Exception) { if (e is CancellationException) throw e; error = "AI 本机记录无法解密，请重新配置；原记录仍保留" }
        loaded = true
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex + 1) }
    fun send() {
        if (busy || !loaded || input.isBlank()) return
        if (config.apiKey.isBlank() || config.model.isBlank() || config.endpoint.isBlank()) { settings = true; return }
        val next = messages + AssistantMessage("user", input.trim().take(12000)); input = ""; messages = next; error = ""
        busy = true
        job = scope.launch {
            try {
                fun historyJson(values: List<AssistantMessage>) = JSONObject().put("messages", JSONArray().also { array ->
                    values.takeLast(100).forEach { array.put(JSONObject().put("role", it.role).put("content", it.content)) }
                })
                withContext(Dispatchers.IO) { store.write("history:$account", historyJson(next)) }
                check(UserManager.getInstance().currentAccountStorageKey == account) { "账号已切换，请重新打开 AI 助手" }
                val courses = if (includeCourses) withContext(Dispatchers.IO) {
                    ScvtcRuntime.extraction()?.meetings.orEmpty().take(120).joinToString("\n") { "${it.name}：星期${it.day}，${it.startNode}-${it.endNode}节，周次${it.weeks.joinToString(",")}，${it.room}" }
                } else ""
                val answer = AssistantApi.answer(config, next, if (includeCourses) "\n用户同意参考以下本机课表（不代表实时教务状态）：\n$courses" else "")
                messages = (next + AssistantMessage("assistant", answer)).takeLast(100)
                withContext(Dispatchers.IO) { store.write("history:$account", historyJson(messages)) }
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "AI 请求失败" }
            finally { busy = false }
        }
    }
    if (settings) AssistantSettings(config, settingsError, onDismiss = { settings = false; settingsError = "" }) { value ->
        scope.launch {
            try { withContext(Dispatchers.IO) { AssistantApi.endpoint(value.endpoint); require(value.model.isNotBlank() && value.apiKey.isNotBlank()) { "请填写模型和密钥" }; store.write("configuration", value.json()) }; config = value; settings = false; error = "" }
            catch (e: Exception) { if (e is CancellationException) throw e; settingsError = e.message ?: "配置保存失败" }
        }
    }
    Scaffold(containerColor = Color.Transparent, topBar = { SystemTopBar("AI 助手小澄", navigationIcon = {
        SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
    }, actions = { SystemIconButton(Icons.Outlined.Settings, "AI 配置", { settings = true }) }) }, bottomBar = {
        NextGroup { Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NextSwitch("本次对话允许参考本机课表", includeCourses) { includeCourses = it }
            GlassTextField(input, { input = it }, Modifier.fillMaxWidth().heightIn(max = 160.dp), "输入学习问题", singleLine = false, enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NextButton(if (busy) "停止" else "发送") { if (busy) job?.cancel() else send() }
                NextButton("清除本机对话") { if (!busy) scope.launch { withContext(Dispatchers.IO) { store.clearHistory(account) }; messages = emptyList() } }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { NextGroup { Column(Modifier.padding(16.dp)) {
                CampusCompanionSlot(preview = true, working = busy, reduceMotion = NextAppearance.theme.reduceMotion)
                NextText("使用你配置的 HTTPS 模型服务。密钥和对话在本机加密保存。学校密码、Cookie、学号与成绩不会发送给 AI；课表需打开下方授权开关。")
                if (config.apiKey.isBlank()) NextButton("配置 AI 服务") { settings = true }
            } } }
            itemsIndexed(messages) { _, message -> NextGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NextText(if (message.role == "user") "我" else "小澄")
                SelectionContainer { Text(message.content) }
            } } }
            if (busy) item { NextText("小澄正在思考…") }
        }
    }
}

@Composable
private fun AssistantSettings(current: AssistantConfig, error: String, onDismiss: () -> Unit, onSave: (AssistantConfig) -> Unit) {
    var endpoint by remember { mutableStateOf(current.endpoint) }; var model by remember { mutableStateOf(current.model) }
    var key by remember { mutableStateOf(current.apiKey) }
    SystemDialog(onDismissRequest = onDismiss, title = { Text("AI 服务配置") }, confirmButton = {
        NextButton("加密保存") { onSave(AssistantConfig(endpoint.trim(), model.trim(), key.trim())) }
    }, dismissButton = { NextButton("取消", onClick = onDismiss) }) {
        NextText("支持 Chat Completions 协议的 HTTPS 服务；地址可填写到 /v1 或完整 /chat/completions。")
        GlassTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), "HTTPS 服务地址")
        GlassTextField(model, { model = it }, Modifier.fillMaxWidth(), "模型名称")
        GlassTextField(key, { key = it }, Modifier.fillMaxWidth(), "API Key", visualTransformation = PasswordVisualTransformation())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
