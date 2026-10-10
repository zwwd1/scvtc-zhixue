package com.tyust.course.ui.route

import com.tyust.course.ui.system.GlassToaster
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tyust.course.ui.system.BindingManagementContent
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemConfirmDialog
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.system.SystemPrimaryButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tyust.course.LoginActivity
import com.tyust.course.login.PasswordLoginCallback
import com.tyust.course.login.PasswordLoginGatewayFactory
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.StartupPagePreferences
import com.tyust.course.manager.UserManager
import com.tyust.course.network.CourseApiClient
import com.tyust.course.ui.screen.SettingsScreen
import com.tyust.course.ui.screen.SchoolAdaptationFlow
import com.tyust.course.update.UpdateManager
import com.tyust.course.update.UpdateDialog
import com.tyust.course.activation.ActivationManager
import com.tyust.course.manager.StudentLimitManager
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LinearProgressIndicator
import com.tyust.course.ui.system.SystemDivider
import com.tyust.course.ui.system.SystemStatusBadge
import com.tyust.course.ui.system.SystemTone
import com.tyust.course.ui.theme.NeuPrimary
import com.tyust.course.ui.theme.Neutral500
import com.tyust.course.ui.theme.SemanticSuccess
import com.tyust.course.ui.theme.SemanticWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onAccountChanged: () -> Unit = {},
    onSurveyCenter: () -> Unit = {},
    surveyUnreadCount: Int = 0
) {
    val context = LocalContext.current
    val isDemoMode = remember { UserManager.getInstance().isDemoMode }

    var studentName by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }

    // UI States
    var showSchoolDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAcademicSupport by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showAccountManagerDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var pendingPasswordDelete by remember { mutableStateOf<UserManager.AccountRecord?>(null) }
    var pendingAccountDelete by remember { mutableStateOf<UserManager.AccountRecord?>(null) }
    var showSchoolAdaptation by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showThemeDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showStartupPageDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val startupPagePreferences = remember(context) { StartupPagePreferences.from(context) }
    var startupPage by remember(startupPagePreferences) { mutableStateOf(startupPagePreferences.read()) }
    val currentWallpaperName = com.tyust.course.manager.AppearanceSettingsManager.currentWallpaperName

    // Quota States
    var isSuper by remember { mutableStateOf(false) }
    var quotaInfo by remember { mutableStateOf("") }
    var quotaUsedCount by remember { mutableIntStateOf(0) }
    var quotaMaxCount by remember { mutableIntStateOf(0) }
    var quotaBoundNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var quotaAccounts by remember { mutableStateOf<List<UserManager.AccountRecord>>(emptyList()) }
    // 账号管理走全量列表（跨学校）：这里是"管理"，不该被当前学校过滤掉
    var allAccounts by remember { mutableStateOf<List<UserManager.AccountRecord>>(emptyList()) }
    var accountsWithPassword by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentAccountKey by remember { mutableStateOf("") }
    var canRefreshCookie by remember { mutableStateOf(false) }
    val session by UserManager.getInstance().sessionState.state.collectAsState()
    var cookieUpdateFeedback by remember {
        mutableStateOf<Pair<com.tyust.course.manager.SessionToken, com.tyust.course.ui.system.SymbolResult>?>(null)
    }
    val cookieUpdateResult = cookieUpdateFeedback?.takeIf { it.first == session.token }?.second
        ?: com.tyust.course.ui.system.SymbolResult.None
    val recovery by com.tyust.course.utils.SessionRenewer.state.collectAsState()
    val isRefreshingCookie = recovery.token == session.token &&
        recovery.phase == com.tyust.course.utils.RecoveryPhase.Restoring
    val relogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Update States
    val updateManager = remember { UpdateManager.getInstance(context) }
    val updateSnapshot by updateManager.state.collectAsState()
    val isCheckingUpdate = updateSnapshot.check == UpdateManager.Check.CHECKING
    val currentVersion = remember { updateManager.getCurrentVersionName() }


    fun refreshAccountUiState() {
        val userManager = UserManager.getInstance()
        val name = userManager.studentName
        val school = userManager.currentSchool

        if (isDemoMode) {
            studentName = name ?: "演示用户"
            deviceId = "LOCAL-DEMO"
            schoolName = school?.name ?: "正方演示大学（演示数据）"
            isSuper = true
            quotaUsedCount = 0
            quotaMaxCount = 0
            quotaBoundNames = emptyList()
            quotaAccounts = emptyList()
            allAccounts = emptyList()
            accountsWithPassword = emptySet()
            currentAccountKey = userManager.currentAccountKey
            canRefreshCookie = false
            quotaInfo = "本地演示"
            return
        }

        studentName = name ?: "同学"
        deviceId = ActivationManager.getSavedDeviceId(context)
        schoolName = school?.name ?: "未选择"

        val maxStudents = ActivationManager.getMaxStudents(context)
        val usedNames = StudentLimitManager.getUsedStudentNames(context)
        val usedCount = StudentLimitManager.getUsedCount(context)
        isSuper = maxStudents <= 0
        quotaUsedCount = usedCount
        quotaMaxCount = maxStudents
        quotaBoundNames = usedNames.toList()
        quotaAccounts = userManager.accountsForCurrentSchool
        allAccounts = userManager.savedAccounts
        accountsWithPassword = allAccounts
            .filter { userManager.hasSavedPassword(it.key) }
            .map { it.key }
            .toSet()
        currentAccountKey = userManager.currentAccountKey
        canRefreshCookie = userManager.loginMode == "password"
        quotaInfo = if (isSuper) {
            "无限制"
        } else {
            "$usedCount / $maxStudents"
        }
    }

    LaunchedEffect(session.token) {
        refreshAccountUiState()
    }

    fun performLogout() {
        UserManager.getInstance().clearLoginState()
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        // If context is not activity, clean task might need validation but usually safe
    }

    fun switchAccount(accountKey: String, onSwitched: () -> Unit) {
        if (accountKey == currentAccountKey) return
        if (UserManager.getInstance().switchToAccount(accountKey)) {
            refreshAccountUiState()
            onSwitched()
            onAccountChanged()
            GlassToaster.show("已切换账号")
        } else {
            val record = UserManager.getInstance().savedAccounts.firstOrNull { it.key == accountKey }
            val check = record?.let { StudentLimitManager.checkCanUseStudent(context, it.schoolId, it.studentName.orEmpty(), it.studentId.orEmpty()) }
            GlassToaster.show(if (check != null && !check.allowed) check.reason + "，请在配额管理中解绑" else "账号切换失败，请重新登录")
        }
    }

    /** 只删密码：账号还在，但不再自动续期，下次失效需要手动登录。 */
    fun deleteAccountPassword(record: UserManager.AccountRecord) {
        UserManager.getInstance().deletePassword(record.key)
        refreshAccountUiState()
        GlassToaster.show("已删除该账号保存的密码")
    }

    /** 彻底删号：账号记录 + 已存密码 + 运行期 Cookie + 本地课程缓存。 */
    fun deleteAccountEntirely(record: UserManager.AccountRecord) {
        val userManager = UserManager.getInstance()
        val isCurrent = record.key == userManager.currentAccountKey
        val storageKey = userManager.deleteAccount(record.key)
        if (storageKey.isNotEmpty()) {
            com.tyust.course.manager.CourseCacheManager.clearAccountCache(context, storageKey)
        }
        refreshAccountUiState()
        if (isCurrent) {
            // 当前账号被删掉，会话已经没有依据了，直接回登录页
            GlassToaster.show("账号已删除，请重新登录")
            performLogout()
        } else {
            GlassToaster.show("账号已删除")
        }
    }

    fun checkForUpdate() {
        if (isDemoMode) { GlassToaster.show("本地演示模式不执行更新检查"); return }
        updateManager.checkForUpdate(manual = true)
    }

    fun refreshCookieManually() {
        if (isDemoMode || isRefreshingCookie) return
        cookieUpdateFeedback = null
        val user = UserManager.getInstance()
        val expected = user.sessionState.token
        com.tyust.course.utils.SessionRenewer.request(expected, manual = true) { result ->
            when (result) {
                is com.tyust.course.utils.SessionRecoveryResult.Recovered -> {
                    if (user.sessionState.isCurrent(result.token)) {
                        cookieUpdateFeedback = result.token to com.tyust.course.ui.system.SymbolResult.Success
                        refreshAccountUiState()
                        GlassToaster.show("登录状态已更新")
                    }
                }
                is com.tyust.course.utils.SessionRecoveryResult.NeedsLogin -> {
                    if (user.sessionState.isCurrent(expected)) {
                        cookieUpdateFeedback = expected to com.tyust.course.ui.system.SymbolResult.Failure
                        GlassToaster.show(
                        result.message.ifBlank { when (result.reason) {
                            com.tyust.course.utils.RecoveryFailure.Network -> "暂时无法连接，请稍后重试"
                            com.tyust.course.utils.RecoveryFailure.Login -> "登录处理失败，请重试或使用网页登录"
                            com.tyust.course.utils.RecoveryFailure.Storage -> "保存失败，请重试"
                            else -> "需要重新登录以更新登录状态"
                        } }
                        )
                    }
                }
                com.tyust.course.utils.SessionRecoveryResult.Superseded -> Unit
            }
        }
    }

    if (showSchoolAdaptation) {
        com.tyust.course.ui.system.GlassSubpage(onDismiss = { showSchoolAdaptation = false }) { close ->
            SchoolAdaptationFlow(onNavigateBack = close)
        }
    }

    val usagePreferences by com.tyust.course.usage.UsageStatsManager.preferences.collectAsState()
    SettingsScreen(
        studentName = studentName,
        studentId = deviceId,
        schoolName = schoolName,
        currentVersion = currentVersion,
        onSchoolSelect = {
            if (isDemoMode) GlassToaster.show("演示学校固定为本地数据源") else showSchoolDialog = true
        },
        onCookieConfig = {
            relogin.launch(Intent(context, LoginActivity::class.java).apply {
                putExtra("force_relogin", true)
                putExtra(LoginActivity.EXTRA_RETURN_TO_CALLER, true)
            })
        },
        onAccountManage = {
            if (isDemoMode) GlassToaster.show("本地演示模式不读取真实账号") else showAccountManagerDialog = true
        },
        savedAccountCount = allAccounts.size,
        onClearCache = { showClearCacheDialog = true },
        onCheckUpdate = { checkForUpdate() },
        onAbout = { showAboutDialog = true },
        onCredits = { showCreditsDialog = true },
        isDemoMode = isDemoMode,
        onLogout = { if (isDemoMode) performLogout() else showLogoutDialog = true },
        onQuotaClick = { showQuotaDialog = true },
        onRefreshCookieClick = { refreshCookieManually() },
        onLogExport = { com.tyust.course.utils.LogUtils.exportLogs(context) },
        onErrorReport = {
            val report = com.tyust.course.diagnostics.AppDiagnostics.latest(context)
            if (report != null) com.tyust.course.diagnostics.AppDiagnostics.showReport(context, report)
            else android.widget.Toast.makeText(context, "暂未记录错误", android.widget.Toast.LENGTH_SHORT).show()
        },
        onSchoolAdaptation = {
            if (isDemoMode) GlassToaster.show("本地演示模式不连接学校适配服务") else showSchoolAdaptation = true
        },
        onSurveyCenter = onSurveyCenter,
        surveyUnreadCount = surveyUnreadCount,
        onWallpaperSelect = { showWallpaperDialog = true },
        wallpaperName = currentWallpaperName,
        themeName = AppearanceSettingsManager.themeMode.label,
        onThemeSelect = { showThemeDialog = true },
        startupPageName = startupPage.label,
        onStartupPageSelect = { showStartupPageDialog = true },
        glassEffectEnabled = AppearanceSettingsManager.glassEffectEnabled,
        onGlassEffectChange = { AppearanceSettingsManager.updateGlassEffect(it) },
        navBarAutoCollapseEnabled = AppearanceSettingsManager.navBarAutoCollapseEnabled,
        onNavBarAutoCollapseChange = { AppearanceSettingsManager.updateNavBarAutoCollapse(it) },
        usageEnabled = usagePreferences.enabled,
        onUsageEnabledChange = com.tyust.course.usage.UsageStatsManager::setEnabled,
        isSuper = isSuper,
        quotaInfo = quotaInfo,
        canRefreshCookie = canRefreshCookie,
        isRefreshingCookie = isRefreshingCookie,
        academicSystemName = com.tyust.course.academic.AcademicCapabilities.name(UserManager.getInstance().currentSchool?.academicSystem),
        onAcademicSupport = { showAcademicSupport = true },
        onAcademicPlugins = { context.startActivity(Intent(context, com.tyust.course.academic.plugin.PluginCenterActivity::class.java)) },
        onCampusServices = { com.tyust.course.scvtc.CampusNavigation.request.value = "app.services" },
        onQuickFeedback = { com.tyust.course.academic.plugin.PluginFeedback.open(context) }
    )
    if (showAcademicSupport) com.tyust.course.ui.screen.AcademicSupportDialog(
        UserManager.getInstance().currentSchool?.academicSystem, onDismiss = { showAcademicSupport = false })

    if (showThemeDialog) {
        com.tyust.course.ui.screen.AppThemeSettingsDialog { showThemeDialog = false }
    }
    if (showStartupPageDialog) {
        com.tyust.course.ui.screen.StartupPageSettingsDialog(
            page = startupPage,
            onPageChange = {
                startupPagePreferences.write(it)
                startupPage = it
            },
            onDismiss = { showStartupPageDialog = false }
        )
    }
    if (showWallpaperDialog) {
        com.tyust.course.ui.screen.WallpaperSettingsDialog(
            onDismiss = { showWallpaperDialog = false }
        )
    }

    // Dialogs
    if (showLogoutDialog) {
        SimpleConfirmDialog(
            title = "退出登录",
            text = "确定要退出登录吗？",
            onConfirm = {
                performLogout()
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showClearCacheDialog) {
        SimpleConfirmDialog(
            title = "清除缓存",
            text = "确定要清除所有本地缓存数据吗？",
            onConfirm = {
                GlassToaster.show("缓存已清除")
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showAboutDialog) {
        SystemDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "更新历史",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "关闭",
                    onClick = { showAboutDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val updates = listOf(
                    "1.2.2 · 2026-10-10" to "填写学号和密码后自动连接学校，身份核验通过就开始后台同步。会话过期自动恢复；切换账号保留各自的加密凭据，退出登录仍保留离线记录。重做课程首页和个人页，成绩、学分与刷新进度衔接更清楚，失败可以继续用缓存。壁纸、底栏和完整玻璃动效继续保留。",
                    "1.2.1 · 2026-10-10" to "统一首页与服务中心的成绩入口，完善成绩搜索、课程性质筛选、排序及按筛选导出。优先显示加密缓存，刷新失败保留成绩，完善学分概况、同步任务和账号退出确认。AI 支持停止与重试上一条、清除对话确认和配置保存状态。保留既有壁纸、玻璃动效与用户数据，云服务仍暂停。",
                    "1.2.0 · 2026-10-10" to "统一顶栏按钮、壁纸与外观入口。液态玻璃关闭后全应用使用高斯模糊，移除重复材质与底栏选择。修复同步子页遮挡和成绩页底栏，精简课程首页，移除问卷、插件和统一登录适配入口，反馈改为本项目 GitHub。加入可配置的 AI 助手，密钥与对话在本机加密，课表上下文需单独授权。使用原创川职月光壁纸与小澄五种状态，保留完整动效与已有用户数据，云服务继续暂停。",
                    "1.1.0 · 2026-10-09" to "精简本校服务中心为课表、成绩、学分三个入口，其他业务进入独立官网浏览器。修复官网新窗口、页面标题与返回历史，增加真实同步进度和右上角液态玻璃刷新。完善学期筛选、学分去重、本机加密查询缓存、壁纸裁剪和独立卡片材质。更新二次元头像图标、小澄多状态互动与赞赏原图，保留原生能力和动效，云服务继续暂停。",
                    "1.0.0 · 第一版" to "川职·知学：保留原生课表、成绩、考试和插件能力；本校接入真实 JSON 接口，自动同步课表、学生信息、成绩、考试安排、等级考试查询与毕业学分要求。完善本机加密登录和教务会话恢复；保留三套外观和液态玻璃交互。云服务暂停，本机数据与本地备份继续使用。"
                )

                updates.forEach { (date, desc) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(com.tyust.course.ui.theme.NeuPrimary, CircleShape)
                            )
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    if (showCreditsDialog) {
        SystemDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = {
                Text(
                    text = "致谢与关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "我知道了",
                    onClick = { showCreditsDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "特别致谢",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "本应用基于多项优秀的开源技术构建，衷心感谢以下开源项目及社区的支持：\n" +
                            "• Jetpack Compose & Kotlin\n" +
                            "• OkHttp3 & Gson\n" +
                            "• Jsoup (HTML 解析库)\n" +
                            "• Material Design 3\n" +
                            "• AndroidLiquidGlass 动效库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                com.tyust.course.ui.system.SystemDivider()

                Text(
                    text = "项目维护与协作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "项目维护：zwwd1\n开发协作：ChatGPT / Codex\n" +
                            "致谢项目：zhengfang-apk · znjhahaha\nhttps://github.com/znjhahaha/zhengfang-apk\nSleepDown-Schedule · xiaomanjun233（视觉与动效参考）\nhttps://github.com/xiaomanjun233/SleepDown-Schedule\n本应用为非官方川职适配项目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                com.tyust.course.ui.system.SystemDivider()

                Text(
                    text = "本软件为开源免费项目，仅供个人学习与技术交流使用，严禁用于任何商业目的与倒卖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }
        }
    }

    if (showQuotaDialog) {
        QuotaStatusDialog(
            deviceId = deviceId,
            isSuper = isSuper,
            usedCount = quotaUsedCount,
            maxCount = quotaMaxCount,
            boundNames = quotaBoundNames,
            onBindingsChanged = ::refreshAccountUiState,
            accounts = quotaAccounts,
            currentAccountKey = currentAccountKey,
            onSwitchAccount = { accountKey ->
                switchAccount(accountKey) { showQuotaDialog = false }
            },
            onDismiss = { showQuotaDialog = false }
        )
    }

    if (showAccountManagerDialog) {
        AccountManagerDialog(
            accounts = allAccounts,
            currentAccountKey = currentAccountKey,
            accountsWithPassword = accountsWithPassword,
            onSwitchAccount = { accountKey ->
                switchAccount(accountKey) { showAccountManagerDialog = false }
            },
            onDeletePassword = { pendingPasswordDelete = it },
            onDeleteAccount = { pendingAccountDelete = it },
            onRelogin = { showAccountManagerDialog = false; relogin.launch(Intent(context, LoginActivity::class.java).apply {
                putExtra("force_relogin", true); putExtra(LoginActivity.EXTRA_RETURN_TO_CALLER, true)
            }) },
            onDismiss = { showAccountManagerDialog = false }
        )
    }

    pendingPasswordDelete?.let { record ->
        SimpleConfirmDialog(
            title = "删除已保存的密码",
            text = "删除后「${record.displayName}」将无法在登录状态失效时自动续期，" +
                "需要你手动重新登录。账号本身与本地数据不会被删除。",
            confirmText = "删除密码",
            onConfirm = {
                deleteAccountPassword(record)
                pendingPasswordDelete = null
            },
            onDismiss = { pendingPasswordDelete = null }
        )
    }

    pendingAccountDelete?.let { record ->
        SimpleConfirmDialog(
            title = "删除账号",
            text = "将删除「${record.displayName}」的账号记录、已保存的密码、登录状态与本地课程缓存，" +
                "此操作不可恢复。绑定名额不会自动释放，可在配额管理中解绑。",
            confirmText = "删除账号",
            onConfirm = {
                deleteAccountEntirely(record)
                pendingAccountDelete = null
                showAccountManagerDialog = false
            },
            onDismiss = { pendingAccountDelete = null }
        )
    }



    if (showSchoolDialog) {
        var animateTrigger by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { animateTrigger = true }

        fun dismiss() {
            animateTrigger = false
        }

        if (!animateTrigger) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300)
                showSchoolDialog = false
            }
        }

        SystemDialog(
            onDismissRequest = { dismiss() },
            title = {
                Text(
                    text = "学校与教务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            dismissButton = {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = { dismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            TextButton(onClick = { showAcademicSupport = true }) { Text("当前适配与支持限制") }
            val schools = remember { UserManager.getInstance().supportedSchools }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp) // 限制最大高度，防止学校列表过多时把 Dialog 挤出屏幕外
            ) {
                items(schools) { school ->
                    Text(
                        text = school.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                UserManager.getInstance().clearLoginState()
                                UserManager.getInstance().currentSchool = school
                                GlassToaster.show("已切换到：${school.name}")
                                performLogout()
                                dismiss()
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun QuotaStatusDialog(
    deviceId: String,
    isSuper: Boolean,
    usedCount: Int,
    maxCount: Int,
    boundNames: List<String>,
    onBindingsChanged: () -> Unit,
    accounts: List<UserManager.AccountRecord>,
    currentAccountKey: String,
    onSwitchAccount: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val safeMax = maxCount.coerceAtLeast(0)
    val safeUsed = usedCount.coerceAtLeast(0)
    val usageRatio = if (!isSuper && safeMax > 0) {
        (safeUsed.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val statusText = if (isSuper) "超级用户" else "普通用户"
    val quotaText = if (isSuper) "无限制" else "$safeUsed / $safeMax"

    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "当前账号配额",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "设备绑定与名额使用情况",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "知道了",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuotaInfoRow(label = "身份", value = statusText)
                    QuotaInfoRow(label = "配额", value = quotaText)
                    if (!isSuper) {
                        LinearProgressIndicator(
                            progress = { usageRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = if (usageRatio >= 1f) SemanticWarning else NeuPrimary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            SystemDivider(alpha = 0.5f)

            Text("设备 ID：${deviceId.ifBlank { "未获取" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "已绑定账号",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BindingManagementContent(onChanged = onBindingsChanged)
            }

            if (accounts.isNotEmpty()) {
                SystemDivider(alpha = 0.5f)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "切换账号",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    accounts.forEach { account ->
                        val isCurrent = account.key == currentAccountKey
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrent) { onSwitchAccount(account.key) },
                            color = if (isCurrent) NeuPrimary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${account.accountIdText} · ${if (account.loginMode == "password") "密码登录" else "Cookie 登录"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SystemStatusBadge(
                                    text = if (isCurrent) "当前" else "切换",
                                    tone = if (isCurrent) SystemTone.Info else SystemTone.Neutral
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "说明：同一设备可绑定不同学校的学生账号，所有学校合计最多 3 个；切换账号会同步切换 Cookie 与本地账号上下文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AccountManagerDialog(
    accounts: List<UserManager.AccountRecord>,
    currentAccountKey: String,
    accountsWithPassword: Set<String>,
    onSwitchAccount: (String) -> Unit,
    onDeletePassword: (UserManager.AccountRecord) -> Unit,
    onDeleteAccount: (UserManager.AccountRecord) -> Unit,
    onRelogin: () -> Unit,
    onDismiss: () -> Unit
) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "账号管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "切换账号、管理已保存的密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onRelogin) { Text("重新登录 · 保留已存账号") }
            if (accounts.isEmpty()) {
                Text(
                    text = "本机还没有保存任何账号。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                accounts.forEach { account ->
                    val isCurrent = account.key == currentAccountKey
                    val hasPassword = accountsWithPassword.contains(account.key)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isCurrent) NeuPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${account.accountIdText} · " +
                                            if (account.loginMode == "password") "密码登录" else "Cookie 登录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = account.schoolName.ifBlank { "未记录学校" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SystemStatusBadge(
                                    text = if (hasPassword) "已存密码" else "未存密码",
                                    tone = if (hasPassword) SystemTone.Success else SystemTone.Neutral
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    SystemStatusBadge(text = "当前账号", tone = SystemTone.Info)
                                } else {
                                    AccountActionButton(
                                        text = "切换",
                                        onClick = { onSwitchAccount(account.key) }
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (hasPassword) {
                                    AccountActionButton(
                                        text = "删除密码",
                                        onClick = { onDeletePassword(account) }
                                    )
                                }
                                AccountActionButton(
                                    text = "删除账号",
                                    tint = com.tyust.course.ui.theme.SemanticDanger,
                                    onClick = { onDeleteAccount(account) }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "密码经系统密钥库加密后仅保存在本机，用于登录状态失效时自动续期；" +
                    "退出登录不会删除它。删除账号不会自动释放绑定名额，可在配额管理中解绑。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/** 账号卡里的小动作按钮：轻量胶囊，避免三个实心按钮在一行里互相抢注意力。 */
@Composable
private fun AccountActionButton(
    text: String,
    tint: Color = NeuPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

@Composable
private fun QuotaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SimpleConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    showCancel: Boolean = true
) {
    SystemConfirmDialog(
        title = title,
        text = text,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = confirmText,
        showCancel = showCancel
    )
}
