package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.*

@Stable
internal class GradeBrowserState(query: String = "", sort: Int = 0, type: String = "") {
    var query by mutableStateOf(query)
    var sort by mutableIntStateOf(sort)
    var type by mutableStateOf(type)
    fun clear() { query = ""; sort = 0; type = "" }
    fun filter(grades: List<GradeItemUi>): List<GradeItemUi> {
        val search = query.trim()
        val matching = grades.filter {
            (type.isBlank() || it.courseType == type) &&
                (it.courseName.contains(search, true) || it.courseCode.contains(search, true))
        }
        fun number(value: String) = value.toDoubleOrNull()?.takeIf(Double::isFinite)
        return when (sort) {
            1 -> matching.sortedWith(compareByDescending<GradeItemUi> { number(it.grade) }.thenBy { it.courseName })
            2 -> matching.sortedWith(compareByDescending<GradeItemUi> { number(it.credits) }.thenBy { it.courseName })
            3 -> matching.sortedBy { it.courseName }
            else -> matching
        }
    }
    companion object {
        val Saver = listSaver<GradeBrowserState, Any>(
            save = { listOf(it.query, it.sort, it.type) },
            restore = { GradeBrowserState(it[0] as String, it[1] as Int, it[2] as String) }
        )
    }
}

@Composable
internal fun GradeBrowserControls(state: GradeBrowserState, grades: List<GradeItemUi>, visibleCount: Int) {
    val types = remember(grades, state.type) {
        listOf("") + (grades.map { it.courseType } + state.type).filter(String::isNotBlank).distinct().sorted()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassTextField(state.query, { state.query = it }, Modifier.fillMaxWidth(),
            placeholder = "搜索课程名称或代码", leadingIcon = Icons.Outlined.Search)
        SystemPicker(listOf("学校顺序", "成绩从高到低", "学分从高到低", "课程名称"), state.sort,
            { state.sort = it }, Modifier.fillMaxWidth(), label = "排序")
        if (types.size > 1) SystemPicker(types.map { it.ifBlank { "全部性质" } }, types.indexOf(state.type),
            { state.type = types[it] }, Modifier.fillMaxWidth(), label = "课程性质")
        Text("显示 $visibleCount / ${grades.size} 条成绩 · 汇总按完整范围计算，导出按当前筛选结果",
            style = MaterialTheme.typography.bodySmall)
        if (state.query.isNotBlank() || state.type.isNotBlank() || state.sort != 0)
            SystemSecondaryButton("清除筛选与排序", { state.clear() })
    }
}
