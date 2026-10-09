package com.openwakeup.parser

import cn.scvtc.campus.core.*
import com.openwakeup.parser.csv.CsvParser
import com.openwakeup.parser.ics.*
import java.time.*
import kotlin.test.*

class CourseFormatsTest {
  private val meeting = Meeting(name = "电气,CAD\"实验\"\n设计", teacher = "教师甲", room = "教室206",
    day = 2, startNode = 1, endNode = 2, weeks = listOf(1, 3, 9, 28))
  private fun weeks(p: List<CoursePreview>) = p.flatMap { c -> (c.startWeek..c.endWeek).filter { c.type == 0 || it % 2 == if (c.type == 1) 1 else 0 } }.toSet()

  @Test fun csvKeepsQuotedNameAndDiscreteWeek28() {
    val parsed = CsvParser().parse(ParserInput("\uFEFF" + CourseFormats.csv(listOf(meeting)), "csv"))
    assertEquals(meeting.name, parsed.first().name)
    assertEquals(meeting.weeks.toSet(), weeks(parsed))
    assertEquals(2, parsed.first().day)
  }

  @Test fun htmlKeepsSeparateArrangementsOfSameCourse() {
    val a = meeting.copy(name = "电气 CAD", teacher = "教师甲")
    val b = a.copy(day = 5, room = "教室101", startNode = 5, endNode = 6)
    val parsed = ScvtcParser.html(CourseFormats.html(listOf(a, b), "2026-2027-1"), School.SCHEDULE, "schedule")
    assertTrue(parsed.warnings.isEmpty(), parsed.warnings.toString())
    assertEquals(setOf(a, b), parsed.meetings.toSet())
  }

  @Test fun icsRoundTripsShanghaiDatesAndExactNodes() {
    val slots = listOf(CourseFormats.Clock(1, LocalTime.of(8,30), LocalTime.of(9,15)), CourseFormats.Clock(2, LocalTime.of(9,20), LocalTime.of(10,5)))
    val monday = LocalDate.of(2026,10,5)
    val encoded = CourseFormats.ics(listOf(meeting), monday, slots, Instant.parse("2026-10-06T00:00:00Z"))
    val decoded = IcsParser(slots.map { IcsTimeSlot(it.node, it.start, it.end) }, ZoneId.of("Asia/Shanghai"))
      .parse(encoded, monday, maxWeek = 28)
    assertEquals(meeting.weeks.toSet(), weeks(decoded.courses))
    assertEquals(meeting.name, decoded.courses.first().name)
    assertEquals(meeting.teacher, decoded.courses.first().teacher)
    assertEquals(meeting.room, decoded.courses.first().room)
    assertTrue(encoded.lineSequence().all { it.toByteArray(Charsets.UTF_8).size <= 75 })
  }

  @Test fun icsRequiresRealClock() {
    assertFailsWith<IllegalStateException> { CourseFormats.ics(listOf(meeting), LocalDate.of(2026,10,5), emptyList()) }
  }

  @Test fun observedRecipeMayHaveTimestampQuery() {
    assertTrue(ScvtcSchoolAdapter().validateReadRecipe(QueryRecipe(School.ORIGIN + "/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule?_t=1", "POST", "{}")))
  }

  @Test fun jsonUsesNamedWeekdayInsteadOfAssumingMondayIsCodeOne() {
    val payload = """{"code":200,"data":[{"week":{"weekCode":"2","weekName":"星期一"},"time":{"timeName":"第1-2节"},"courseList":[{"courseName":"课一","weeks":"5;9;11;15;17;28","classroomName":"教室206"}]}]}"""
    val parsed = ScvtcParser.api(payload, School.ORIGIN + "/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule", "schedule")
    assertEquals(1, parsed.meetings.single().day)
    assertEquals(listOf(5, 9, 11, 15, 17, 28), parsed.meetings.single().weeks)
  }
}
