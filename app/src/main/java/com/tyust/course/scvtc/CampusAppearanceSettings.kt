package com.tyust.course.scvtc

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.scvtc.campus.CampusCompanion
import cn.scvtc.campus.CampusCompanionSlot
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.ui.system.*

/** Settings is the only appearance entrance; the sync page owns no appearance preferences. */
@Composable
fun CampusAppearanceSettings(onDismiss: () -> Unit) {
    GlassSubpage(onDismiss) { close ->
        val t = NextAppearance.theme
        Scaffold(containerColor = Color.Transparent, topBar = {
            SystemTopBar("外观参数", navigationIcon = { SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", close) })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { NextText("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n${if (AppearanceSettingsManager.glassEffectEnabled) "液态玻璃" else "高斯模糊"} · 外观开关统一影响按钮、弹窗、课表与底栏") }
                item { NextGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NextText("课程卡片")
                    NextText("模糊 · ${t.courseCardBlur.toInt()}"); NextSlider(t.courseCardBlur, { NextAppearance.update(t.copy(courseCardBlur = it)) }, 0f..24f)
                    NextText("不透明度 · ${(t.courseCardOpacity * 100).toInt()}%"); NextSlider(t.courseCardOpacity, { NextAppearance.update(t.copy(courseCardOpacity = it)) }, .55f..1f)
                    NextText("圆角 · ${t.courseRadius.toInt()}"); NextSlider(t.courseRadius, { NextAppearance.update(t.copy(courseRadius = it)) }, 4f..24f)
                    NextText("高光 · ${(t.courseHighlight * 100).toInt()}%"); NextSlider(t.courseHighlight, { NextAppearance.update(t.copy(courseHighlight = it)) }, 0f.. .6f)
                    NextText("按压动效 · ${(t.courseMotion * 100).toInt()}%"); NextSlider(t.courseMotion, { NextAppearance.update(t.copy(courseMotion = it)) }, 0f..1f)
                    NextButton("恢复卡片默认值") { NextAppearance.update(t.copy(courseCardBlur = 6f, courseCardOpacity = .78f, courseRadius = 12f, courseHighlight = .35f, courseMotion = 1f)) }
                } } }
                item { NextGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val blur = t.navigationBlurDp ?: if (AppearanceSettingsManager.glassEffectEnabled) 10f else 8f
                    NextText("底栏玻璃与回弹")
                    NextText("模糊 · ${blur.toInt()}${if (t.navigationBlurDp == null) "（原版默认）" else ""}")
                    NextSlider(blur, { NextAppearance.update(t.copy(navigationBlurDp = it)) }, 0f..24f)
                    NextText("折射强度 · ${(t.navigationRefractionScale * 100).toInt()}%")
                    NextSlider(t.navigationRefractionScale, { NextAppearance.update(t.copy(navigationRefractionScale = it)) }, 0f..1.5f)
                    NextSwitch("选中滑块色散", t.navigationDispersion) { NextAppearance.update(t.copy(navigationDispersion = it)) }
                    NextText("高光 · ${(t.navigationHighlightScale * 100).toInt()}%")
                    NextSlider(t.navigationHighlightScale, { NextAppearance.update(t.copy(navigationHighlightScale = it)) }, 0f..1.5f)
                    NextText("投影 · ${(t.navigationShadowScale * 100).toInt()}%")
                    NextSlider(t.navigationShadowScale, { NextAppearance.update(t.copy(navigationShadowScale = it)) }, 0f..1.5f)
                    NextText("弹簧力度 · ${(t.navigationSpringScale * 100).toInt()}%")
                    NextSlider(t.navigationSpringScale, { NextAppearance.update(t.copy(navigationSpringScale = it)) }, .5f..1.5f)
                    NextText("默认沿用原版配方；高斯模式不启用折射或色散。参数由你调整，不按机型降低效果。")
                    NextButton("恢复底栏原版参数") { NextAppearance.update(t.copy(navigationBlurDp = null, navigationRefractionScale = 1f, navigationDispersion = true, navigationHighlightScale = 1f, navigationShadowScale = 1f, navigationSpringScale = 1f)) }
                } } }
                item { NextGroup {
                    NextSwitch("减少动态效果", t.reduceMotion) { NextAppearance.update(t.copy(reduceMotion = it)) }
                    NextSwitch("触觉反馈", t.haptic) { NextAppearance.update(t.copy(haptic = it)) }
                    NextSwitch("显示校园助手小澄", CampusCompanion.visible) { CampusCompanion.show(it) }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NextText("角色大小 · ${(CampusCompanion.scale * 100).toInt()}%")
                        NextSlider(CampusCompanion.scale, CampusCompanion::resize, .65f..1f)
                        CampusCompanionSlot(preview = true, reduceMotion = t.reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled())
                        NextText("微笑、侧目与眨眼待机；同步时思考，点击打招呼。角色只在标题预留区域出现。")
                    }
                } }
            }
        }
    }
}

