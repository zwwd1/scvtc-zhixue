package cn.scvtc.campus.core

/** A query may replace only its proven week coverage. DOM pages are partial by default. */
object MergeRules {
  fun merge(old: Extraction?, next: Extraction): Extraction {
    ScheduleWriteGuard.validate(old, next)
    if (old == null) return next
    require(
      old.account == next.account && old.semester == next.semester && old.module == next.module
    ) {
      "数据范围不同"
    }
    val retained =
      if (next.full && next.coverageWeeks.isNotEmpty())
        old.meetings.mapNotNull { m ->
          val weeks = m.weeks - next.coverageWeeks.toSet()
          if (weeks.isEmpty()) null else m.copy(weeks = weeks)
        }
      else old.meetings
    fun key(m: Meeting) =
      listOf(m.courseCode, m.name, m.teacher, m.room, m.className, m.day, m.startNode, m.endNode)
        .joinToString("|")
    val meetings =
      (retained + next.meetings).groupBy(::key).values.map { items ->
        items.last().copy(weeks = items.flatMap { it.weeks }.distinct().sorted())
      }
    val records = if (next.full) next.records else (old.records + next.records).distinct()
    return next.copy(
      meetings = meetings,
      records = records,
      links = (old.links + next.links).distinct(),
      coverageWeeks = (old.coverageWeeks + next.coverageWeeks).distinct().sorted(),
    )
  }
}
