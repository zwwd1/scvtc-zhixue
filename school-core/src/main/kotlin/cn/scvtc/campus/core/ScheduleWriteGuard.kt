package cn.scvtc.campus.core

/** Fail closed before any database, projection, reminder or success timestamp write. */
object ScheduleWriteGuard {
  fun validate(old: Extraction?, next: Extraction) {
    if (next.module != "schedule") return
    require(next.warnings.isEmpty()) { next.warnings.joinToString("；") }
    require(next.meetings.all { it.name.isNotBlank() && it.day in 1..7 &&
      it.startNode >= 1 && it.endNode >= it.startNode && it.weeks.isNotEmpty() &&
      it.weeks.all { w -> w in 1..100 } }) { "课程数据不完整，原课表保留" }
    require(next.meetings.isNotEmpty() || next.emptyConfirmed) { "没有有效课程，原课表保留" }
    require(next.meetings.isNotEmpty() || (next.full && next.coverageWeeks.size==1 && next.coverageWeeks.single() in 1..100)) {
      "空课表缺少已确认的单周查询范围，原课表及成功时间保留"
    }
    require(!(old?.meetings?.isNotEmpty() == true && next.meetings.isEmpty() && next.full &&
      next.coverageWeeks.size > 1)) { "学校返回整段空课表，已阻止覆盖原课表；请确认官方页的学期与登录状态" }
  }
}
