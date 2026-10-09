package com.openwakeup.parser.web

// Modified from OpenWakeUp v0.0.3; AGPL-3.0. School-specific semantic parser replaces fixed child
// offsets.
import cn.scvtc.campus.core.*
import com.openwakeup.parser.*

object YlParser : Parser {
  override fun parse(input: ParserInput): List<CoursePreview> {
    val parsed = ScvtcParser.html(input.text, School.SCHEDULE, "schedule")
    if (parsed.warnings.isNotEmpty()) throw ParserException.parse(parsed.warnings.joinToString("；"))
    if (parsed.meetings.isEmpty()) throw ParserException.empty()
    return parsed.meetings.flatMap { m ->
      WeekRules.runs(m.weeks).map { weeks ->
        CoursePreview(
          name = m.name,
          teacher = m.teacher,
          room = m.room,
          day = m.day,
          startNode = m.startNode,
          step = m.endNode - m.startNode + 1,
          startWeek = weeks.first,
          endWeek = weeks.last,
        )
      }
    }
  }
}
