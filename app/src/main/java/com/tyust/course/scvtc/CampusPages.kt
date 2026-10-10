package com.tyust.course.scvtc

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.scvtc.campus.CampusCompanion
import cn.scvtc.campus.CampusDonationImage
import cn.scvtc.campus.CasAuthManager
import cn.scvtc.campus.CloudBackup
import cn.scvtc.campus.CloudBackupSettings
import cn.scvtc.campus.OfficialLoginMemory
import cn.scvtc.campus.UiSystem
import cn.scvtc.campus.core.CreditSummary
import cn.scvtc.campus.core.Extraction
import cn.scvtc.campus.core.School
import com.tyust.course.manager.UserManager
import com.tyust.course.schedule.*
import com.tyust.course.ui.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

private fun openSchoolAuthentication(context: Context) {
    context.startActivity(Intent(context, com.tyust.course.LoginActivity::class.java)
        .putExtra(com.tyust.course.LoginActivity.EXTRA_RETURN_TO_CALLER,true)
        .putExtra("force_relogin",true))
}

private fun savedTime(time: Long): String =
    if (time > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time)) else "尚未同步"

@Composable
fun NextHome(revision: Int, onSchedule: () -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val user = UserManager.getInstance()
    val account = ScvtcRuntime.account
    val term = ScvtcRuntime.semester
    val storageKey = user.currentAccountStorageKey
    val schoolDate = cn.scvtc.campus.rememberSchoolDate()
    val cache by produceState<Extraction?>(null, account, term, revision) {
        value = null
        if (account.isNotBlank() && term.isNotBlank()) value = withContext(Dispatchers.IO) { ScvtcRuntime.extraction() }
    }
    val gradeCache by produceState<Extraction?>(null, account, revision) {
        value = null
        if (account.isNotBlank()) value = withContext(Dispatchers.IO) { ScvtcRuntime.allGrades() }
    }
    val local by produceState<ScheduleSnapshot?>(null, storageKey, term, revision) {
        value = null
        if (account.isNotBlank() && term.isNotBlank())
            value = withContext(Dispatchers.IO) { ScheduleRepository(context).snapshot(storageKey, "scvtc", term) }
    }
    val schedule = local?.takeIf { it.account == storageKey && it.term == term }
    val schoolCache = cache?.takeIf { it.account == account && it.semester == term }
    val grades = gradeCache?.takeIf { it.account == account }
    val now by produceState(System.currentTimeMillis(), account) {
        while (isActive) { value = System.currentTimeMillis(); delay(60_000) }
    }
    val running by ScvtcRuntime.taskRunning.collectAsState()
    val agenda = remember(schedule, now) {
        ScheduleAgenda.calculate(schedule?.courses.orEmpty(), schedule?.timeBase, now)
    }
    val today = remember(schedule, agenda.week, schoolDate) {
        schedule?.courses.orEmpty().filter {
            it.day == schoolDate.dayOfWeek.value && agenda.week?.let { week -> week in ScheduleWeeks.parse(it.weeks).weeks } == true
        }.sortedBy { it.startPeriod }
    }
    val confirmedWeek = agenda.week?.let { it in schoolCache?.coverageWeeks.orEmpty() } == true
    fun connect() { if (account.isBlank()) onLogin() else openSchoolAuthentication(context) }
    fun openCourse(course: ScheduleCourseRecord) {
        ScheduleWidgetNavigation.openCourse(storageKey, "scvtc", term, course.id)
        onSchedule()
    }
    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, LocalAppOverlayBottomInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CampusPageHeading("今天", "${schoolDate.monthValue}月${schoolDate.dayOfMonth}日 · " +
                (agenda.week?.let { "第 $it 周" } ?: "开学日期待确认"))
        }
        item {
            NextGroup {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val current = agenda.current.firstOrNull()
                    val upcoming = agenda.next
                    when {
                        account.isBlank() -> {
                            Text("先连接学校教务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            NextText("完成一次学校登录后，课表和成绩会保存在本机。")
                            SystemPrimaryButton("连接学校教务", ::connect, modifier = Modifier.fillMaxWidth())
                        }
                        schedule == null || !schedule.hasCache -> {
                            Text("课表还没有同步", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            NextText("点击刷新即可尝试恢复学校认证并读取课表。")
                            SystemPrimaryButton(if(running) "正在同步…" else "同步课表", { ScvtcRuntime.startNativeSync() },
                                enabled=!running, modifier = Modifier.fillMaxWidth())
                        }
                        agenda.needsCalendar -> {
                            Text("确认开学日期", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            NextText("课表已保存。设置第一周周一后，今天和下一节课才能准确对应。")
                            SystemPrimaryButton("设置开学日期", { CampusNavigation.request.value = "schedule-settings" }, modifier = Modifier.fillMaxWidth())
                        }
                        current != null || upcoming != null -> {
                            val occurrence = current ?: upcoming!!
                            val course = occurrence.course
                            Text(if (current != null) "正在上课" else "下一节课", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            NextText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(occurrence.startsAt)) +
                                " · ${course.startPeriod}–${course.endPeriod} 节")
                            NextText(listOf(course.location, course.teacher).filter(String::isNotBlank).joinToString(" · "))
                            SystemSecondaryButton("查看课程", { openCourse(course) }, modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            Text(if (today.isEmpty() && confirmedWeek) "今天没有课程" else "查看今天的安排",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            NextText(if (today.isNotEmpty()) "共 ${today.size} 个安排，课程详情在下方。"
                                else if (confirmedWeek) "课表已同步，可以查看后续教学周。"
                                else "本周尚未确认完整数据，可刷新课表。")
                            SystemSecondaryButton("打开课表", onSchedule, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CampusActionCard("成绩与考试", grades?.let { "${it.records.size} 条成绩记录" } ?: "查看成绩与考试安排",
                    Icons.Outlined.School, Modifier.weight(1f)) { CampusNavigation.request.value = "app.grades" }
                CampusActionCard("学分概况", grades?.let { "${CreditSummary.from(it.records).display} 学分已确认" } ?: "查看学校学分要求",
                    Icons.Outlined.PieChart, Modifier.weight(1f)) { CampusNavigation.request.value = "credits" }
            }
        }
        if (schedule?.hasCache == true && !agenda.needsCalendar) {
            item {
                Text("今日安排 · ${today.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (!confirmedWeek) Text("本周学校数据尚未完整同步；这里先显示本机已有安排。", style = MaterialTheme.typography.bodySmall)
            }
            items(today, key = { it.id }) { course ->
                val times = schedule?.timeBase
                val clock = listOfNotNull(times?.periodStarts?.get(course.startPeriod), times?.periodEnds?.get(course.endPeriod))
                    .takeIf { it.size == 2 }?.joinToString("–").orEmpty()
                NextGroup {
                    NextRow(course.name, listOf("${course.startPeriod}–${course.endPeriod} 节", clock, course.location, course.teacher)
                        .filter(String::isNotBlank).joinToString(" · ")) { openCourse(course) }
                }
            }
        }
        item {
            CampusSyncPanel(account, term, schedule?.cachedAt ?: 0, onConnect = ::connect) { ScvtcRuntime.startNativeSync() }
        }
        item {
            NextGroup {
                NextRow("小澄 · AI 助手", "学习答疑，可选择附带本机课表") {
                    context.startActivity(Intent(context, CampusAiActivity::class.java))
                }
                NextRow("教务同步与备份", "查看账号、同步状态和本机备份") { CampusNavigation.request.value = "profile" }
            }
        }
    }
}

@Composable
private fun CampusPageHeading(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        CampusQuickActions()
    }
}

@Composable
private fun CampusActionCard(title: String, summary: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        NextGroup {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CampusSyncPanel(
    account: String, term: String, cachedAt: Long,
    refreshTitle: String = "刷新课表", onConnect: () -> Unit, onRefresh: () -> Unit,
) {
    val progress by ScvtcRuntime.syncState.collectAsState()
    val running by ScvtcRuntime.taskRunning.collectAsState()
    val state = progress.takeIf { it.account == account && it.term == term }
    val busy = running || progress.busy
    NextGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(when (state?.stage) {
                    NativeSyncStage.AUTH_REQUIRED -> Icons.Outlined.Lock
                    NativeSyncStage.OFFLINE -> Icons.Outlined.CloudOff
                    NativeSyncStage.SUCCESS -> Icons.Outlined.CheckCircle
                    else -> Icons.Outlined.Sync
                }, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (busy) state?.label ?: "等待同步…" else state?.label ?: "本机数据", style = MaterialTheme.typography.titleSmall)
            }
            if (cachedAt > 0) Text("最近成功同步：${savedTime(cachedAt)}", style = MaterialTheme.typography.bodySmall)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("离开此页仍会继续保存。", style = MaterialTheme.typography.bodySmall)
                if (running) SystemSecondaryButton("停止本次同步", { ScvtcRuntime.stopNativeSync() })
            } else {
                val needsAuth = account.isBlank() || state?.stage == NativeSyncStage.AUTH_REQUIRED
                SystemPrimaryButton(if (needsAuth) "连接学校认证" else refreshTitle,
                    if (needsAuth) onConnect else onRefresh, modifier = Modifier.fillMaxWidth())
                if (state?.stage in setOf(NativeSyncStage.AUTH_REQUIRED, NativeSyncStage.FAILED, NativeSyncStage.OFFLINE))
                    Text("上次成功保存的课表和成绩仍可查看。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun NextServices(official: () -> Unit, creditsOpen: Boolean, onCreditsOpenChange: (Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val revision by ScvtcRuntime.revision.collectAsState()
    val account = ScvtcRuntime.account
    val term = ScvtcRuntime.semester
    val bottom = LocalAppOverlayBottomInset.current + 24.dp
    fun open(id: String) { context.startActivity(Intent(context, ScvtcWebActivity::class.java).putExtra("module", id)) }
    if (creditsOpen) {
        BackHandler { onCreditsOpenChange(false) }
        val requirement by produceState<Extraction?>(null, account, term, revision) {
            value = null
            if (account.isNotBlank()) value = withContext(Dispatchers.IO) { ScvtcRuntime.service("credits") }
        }
        val savedGrades by produceState<Extraction?>(null, account, revision) {
            value = null
            if (account.isNotBlank()) value = withContext(Dispatchers.IO) { ScvtcRuntime.allGrades() }
        }
        val cachedAt by produceState<Pair<String, Long>>(account to 0L, account, revision) {
            value = account to 0L
            value = account to withContext(Dispatchers.IO) { ScvtcRuntime.serviceSavedAt("grades") }
        }
        val grades = savedGrades?.takeIf { it.account == account }
        val schoolRequirement = requirement?.takeIf { it.account == account && it.semester == term }
        val summary = remember(grades) { CreditSummary.from(grades?.records.orEmpty()) }
        val target = schoolRequirement?.records?.firstOrNull()?.fields?.get("毕业学分要求").orEmpty()
        val required = target.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val terms = grades?.records.orEmpty().mapNotNull { it.fields["学期"]?.takeIf(String::isNotBlank) }.distinct().sortedDescending()
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
            SystemTopBar("学分概况", navigationIcon = {
                SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回服务中心", { onCreditsOpenChange(false) })
            }, actions = { CampusQuickActions("刷新成绩与学分") { ScvtcRuntime.refreshAcademics() } })
        }) { padding ->
            LazyColumn(contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 12.dp, 20.dp, bottom),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    NextGroup {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("成绩中已确认获得", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (grades == null) "--" else summary.display, style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text("学分", Modifier.padding(bottom = 4.dp))
                            }
                            if (grades != null && required != null) {
                                val ratio = summary.confirmed.divide(required, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                                CreditMeter(ratio)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("学校要求 $target")
                                    Text("还差 ${(required - summary.confirmed).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()}")
                                }
                            } else NextText("学校毕业学分要求：" + target.ifBlank { "尚未取得" })
                            Text("${summary.courses} 门课程已按课程代码合并重修记录。", style = MaterialTheme.typography.bodySmall)
                            if (summary.uncounted > 0) Text("${summary.uncounted} 条缺少学校获得学分或课程代码，未计入。", style = MaterialTheme.typography.bodySmall)
                            Text("这里只比较学分数值，毕业资格以学校审核为准。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (terms.isNotEmpty()) {
                    item { Text("按学期查看", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    item {
                        NextGroup {
                            terms.forEach { id ->
                                val records = grades!!.records.filter { it.fields["学期"] == id }
                                NextRow(id, "${records.size} 条成绩 · ${CreditSummary.from(records).display} 学分已确认") {
                                    CampusNavigation.gradeTerm.value = id
                                    CampusNavigation.request.value = "app.grades"
                                }
                            }
                        }
                        Text("各学期单独汇总，重修课程不能跨学期直接相加。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    CampusSyncPanel(account, term, cachedAt.second.takeIf { cachedAt.first == account } ?: 0,
                        refreshTitle = "刷新成绩与学分", onConnect = { openSchoolAuthentication(context) }) { ScvtcRuntime.refreshAcademics() }
                }
                item {
                    NextGroup {
                        NextRow("完整成绩与考试", "搜索、筛选、查看详情和导出") { CampusNavigation.request.value = "app.grades" }
                        NextRow("学校官方学分页面", "查看毕业要求与审核说明") { open("credits") }
                    }
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.statusBarsPadding(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, bottom),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { CampusPageHeading("服务", "常用查询在这里，其他业务可前往学校教务。") }
            item { GlassTextField(query, { query = it }, Modifier.fillMaxWidth(), "搜索课表、成绩或学分", leadingIcon = Icons.Outlined.Search) }
            val search = query.trim()
            val primary = School.services.filter {
                it.id in School.visiblePrimaryServiceIds && (it.title.contains(search, true) || it.aliases.any { alias -> alias.contains(search, true) })
            }
            items(primary, key = { it.id }) { service ->
                NextGroup {
                    NextRow(service.title, when (service.id) {
                        "schedule" -> "日周课表、课程详情、提醒与桌面组件"
                        "grades" -> "成绩卡片、学期汇总与考试安排"
                        else -> "已获学分、学校要求与学期明细"
                    }) {
                        when (service.id) {
                            "schedule" -> official()
                            "grades" -> CampusNavigation.request.value = "app.grades"
                            "credits" -> onCreditsOpenChange(true)
                        }
                    }
                }
            }
            if (primary.isEmpty()) item {
                SystemEmptyState("没有匹配的服务", "试试“课表”“成绩”或“学分”。",
                    action = { SystemSecondaryButton("清空搜索", { query = "" }) })
            }
            item {
                NextGroup { NextRow("学校官方教务系统", "选课、评教、论文与学籍等业务") { open("official") } }
                Text("官方页面中的提交操作由你确认。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CreditMeter(progress: Float) {
    Box(Modifier.fillMaxWidth().height(12.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
        .background(MaterialTheme.colorScheme.primary.copy(alpha = .16f), RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
    }
}

@Composable
fun NextProfile(revision: Int, onLogin: () -> Unit, onTools: () -> Unit, onSchedule: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val user = UserManager.getInstance()
    val account = ScvtcRuntime.account
    val term = ScvtcRuntime.semester
    val snapshot by produceState<ScvtcSnapshot?>(null, account, term, revision) {
        value = null
        if (account.isNotBlank()) value = withContext(Dispatchers.IO) { ScvtcRuntime.snapshot() }
    }
    val automatic by produceState(false, account, revision) {
        value = false
        if (account.isNotBlank()) value = withContext(Dispatchers.IO) { OfficialLoginMemory(context).configured(account) }
    }
    val scope = rememberCoroutineScope()
    var confirmLogout by rememberSaveable(account) { mutableStateOf(false) }
    var donation by rememberSaveable(account) { mutableStateOf(false) }
    CampusCompanion.initialize(context)
    fun authenticate() { if (account.isBlank()) onLogin() else openSchoolAuthentication(context) }

    if (confirmLogout) SystemDialog(onDismissRequest = { confirmLogout = false }, title = { Text("退出学校账号？") },
        confirmButton = { SystemDestructiveButton("退出账号", { confirmLogout = false; ScvtcRuntime.logout(); onBack() }) },
        dismissButton = { SystemSecondaryButton("继续使用", { confirmLogout = false }) }) {
        NextText("本机认证凭据和网页登录状态会清除。离线课表与成绩仍按账号保留；以后联网同步需要重新登录。")
    }
    if (donation) GlassSubpage(onDismiss = { donation = false }) { close ->
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NextButton("返回", onClick = close)
            NextText("Epiphany 的赞赏码")
            CampusDonationImage()
            NextText("自愿支持个人维护，不属于学校收费服务。")
        }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        SystemTopBar("教务同步", navigationIcon = { SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack) },
            actions = { CampusQuickActions() })
    }) { padding ->
        LazyColumn(contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 12.dp, 20.dp,
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                NextGroup {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (account.isBlank()) "尚未连接学校" else user.studentName.takeIf { user.studentId == account && it.isNotBlank() } ?: "学校账号",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (account.isNotBlank()) {
                            NextText("学号 ${account.takeLast(4).padStart(8, '•')} · $term")
                            Text(if (automatic) "自动登录已保存；会话到期会先尝试恢复。"
                                else "本机尚未保存密码。官方账号密码登录成功后可自动记住。", style = MaterialTheme.typography.bodyMedium)
                        }
                        SystemSecondaryButton(if (account.isBlank()) "连接学校教务" else "打开学校认证页", ::authenticate,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                CampusSyncPanel(account, term, snapshot?.takeIf { it.account == account && it.semester == term }?.fetchedAt ?: 0,
                    onConnect = ::authenticate) { ScvtcRuntime.startNativeSync() }
            }
            item { Text("课表与文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                NextGroup {
                    NextRow("课表与作息设置", "开学日期、节次时间、提醒与显示规则", onSchedule)
                    NextRow("导入与导出", "CSV、ICS、HTML · 先预览再保存", onTools)
                }
            }
            item { CurrentWeekPreference() }
            item {
                NextGroup {
                    CloudBackupSettings(account, term, NextAppearance.theme.ui == UiSystem.MIUIX) { backupRevision ->
                        scope.launch {
                            CloudBackup.get(ScvtcRuntime.context).restore(account, term, backupRevision)?.let { ScvtcRuntime.save(it) }
                        }
                    }
                }
            }
            if (account.isNotBlank()) item {
                NextGroup { NextRow("退出学校账号", "清除本机认证，保留离线记录") { confirmLogout = true } }
            }
            item {
                NextGroup {
                    NextRow("Epiphany 的赞赏码", "自愿支持维护") { donation = true }
                    NextText("川职·知学 ${com.tyust.course.BuildConfig.VERSION_NAME}\n维护者：zwwd1\n非学校官方客户端", Modifier.padding(16.dp))
                }
            }
        }
    }
}
