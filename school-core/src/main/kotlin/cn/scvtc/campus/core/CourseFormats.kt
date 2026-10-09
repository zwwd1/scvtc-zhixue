package cn.scvtc.campus.core

import java.time.*
import java.time.format.DateTimeFormatter

/** Portable formats share the same parsed meetings used by the native timetable. */
object CourseFormats {
  data class Clock(val node: Int, val start: LocalTime, val end: LocalTime)

  fun csv(meetings: List<Meeting>): String {
    fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    return "课程名称,星期,开始节数,结束节数,老师,地点,周数\r\n" +
      meetings.joinToString("\r\n", postfix = "\r\n") {
        listOf(it.name, it.day.toString(), it.startNode.toString(), it.endNode.toString(),
          it.teacher, it.room, it.weeks.distinct().sorted().joinToString("、"))
          .joinToString(",", transform = ::quote)
      }
  }

  /** Script-free, self-contained HTML; round-trips through the school DOM parser. */
  fun html(meetings: List<Meeting>, semester: String): String {
    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
      .replace(">", "&gt;").replace("\"", "&quot;")
    val nodes = meetings.maxOfOrNull { it.endNode } ?: 0
    return buildString {
      append("<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\"><title>个人课表</title>")
      append("<style>body{font-family:system-ui;margin:24px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:8px;vertical-align:top}.courseBox{background:#eef4ff;padding:8px;margin:4px;border-radius:8px}</style><body>")
      append("<h1>").append(escape(semester)).append(" 个人课表</h1><table><thead><tr><th>节次</th>")
      listOf("一", "二", "三", "四", "五", "六", "日").forEach { append("<th>星期$it</th>") }
      append("</tr></thead><tbody>")
      for (node in 1..nodes) {
        append("<tr><td>第${node}节</td>")
        for (day in 1..7) {
          append("<td>")
          meetings.filter { it.day == day && it.startNode == node }.forEach { m ->
            append("<div class=\"courseBox\" data-course-name=\"").append(escape(m.name)).append("\">")
            append("<div>").append(escape(m.name)).append("</div><div>")
            append(m.weeks.distinct().sorted().joinToString(";")).append(" 周（第${m.startNode}-${m.endNode}节）</div>")
            append("<div data-label=\"地点\">").append(escape(m.room)).append("</div>")
            append("<div data-label=\"教师\">").append(escape(m.teacher)).append("</div></div>")
          }
          append("</td>")
        }
        append("</tr>")
      }
      append("</tbody></table></body></html>")
    }
  }

  /** Every actual teaching date is an event, preserving arbitrary weeks without guessed RRULEs. */
  fun ics(meetings: List<Meeting>, monday: LocalDate, clocks: List<Clock>, now: Instant = Instant.now()): String {
    require(monday.dayOfWeek == DayOfWeek.MONDAY) { "请先设置准确的第一周周一" }
    val times = clocks.associateBy { it.node }
    val zone = ZoneId.of("Asia/Shanghai")
    val format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    fun text(s: String) = s.replace("\\", "\\\\").replace("\r\n", "\n")
      .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
    val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Scvtc Campus//Schedule//ZH",
      "CALSCALE:GREGORIAN", "X-OPENWAKEUP-SEMESTER-START:${monday.format(DateTimeFormatter.BASIC_ISO_DATE)}",
      "X-OPENWAKEUP-NODE-COUNT:${clocks.maxOfOrNull { it.node } ?: 0}")
    for (m in meetings) {
      val start = times[m.startNode]?.start ?: error("缺少第 ${m.startNode} 节的真实作息")
      val end = times[m.endNode]?.end ?: error("缺少第 ${m.endNode} 节的真实作息")
      require(end.isAfter(start)) { "课程作息尚未设置，不能生成准确 ICS" }
      for (week in m.weeks.distinct().sorted()) {
        val date = monday.plusWeeks((week - 1).toLong()).plusDays((m.day - 1).toLong())
        lines += listOf("BEGIN:VEVENT", "UID:${m.stableId()}-$week@campus.scvtc.local",
          "DTSTAMP:${format.format(now)}", "DTSTART:${format.format(date.atTime(start).atZone(zone))}",
          "DTEND:${format.format(date.atTime(end).atZone(zone))}", "SUMMARY:${text(m.name)}",
          "LOCATION:${text(m.room)}", "CATEGORIES:OpenWakeUp ICS Formatter,${text(m.teacher)}",
          "X-OPENWAKEUP-DAY:${m.day}", "X-OPENWAKEUP-START-NODE:${m.startNode}",
          "X-OPENWAKEUP-STEP:${m.endNode - m.startNode + 1}", "X-OPENWAKEUP-WEEKS:$week", "END:VEVENT")
      }
    }
    lines += "END:VCALENDAR"
    return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
  }

  private fun fold(line: String): String {
    val out = StringBuilder(); var bytes = 0
    line.codePoints().forEach { cp ->
      val s = String(Character.toChars(cp)); val n = s.toByteArray(Charsets.UTF_8).size
      if (bytes + n > 75) { out.append("\r\n "); bytes = 1 }
      out.append(s); bytes += n
    }
    return out.toString()
  }
}
