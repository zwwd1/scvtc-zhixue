package cn.scvtc.campus.core
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
class SyncSafetyTest {
 private val course=Meeting(name="课",day=2,startNode=1,endNode=2,weeks=listOf(5,6,9))
 private val old=Extraction("schedule","a","t",meetings=listOf(course),coverageWeeks=(1..28).toList())
 @Test(expected=IllegalArgumentException::class) fun wholeEmptyCannotErase(){MergeRules.merge(old,old.copy(meetings=emptyList(),emptyConfirmed=true,full=true))}
 @Test fun emptyWeekPreservesOthers(){val next=old.copy(meetings=emptyList(),emptyConfirmed=true,full=true,coverageWeeks=listOf(6));assertEquals(listOf(5,9),MergeRules.merge(old,next).meetings.single().weeks)}
 @Test(expected=IllegalArgumentException::class) fun malformedCourseCannotReplace(){MergeRules.merge(old,old.copy(meetings=listOf(course.copy(weeks=emptyList())),full=true))}
 @Test fun anonymousEmptyIsNotSuccess(){assertFalse(ScvtcParser.api("{\"data\":[]}",School.SCHEDULE,"schedule").emptyConfirmed)}
 @Test(expected=IllegalArgumentException::class) fun expiryNeverBecomesEmpty(){ScvtcParser.api("{\"code\":401,\"data\":[]}",School.SCHEDULE,"schedule")}
 @Test fun inferMondayFromActualWeek(){assertEquals(LocalDate.of(2026,9,7),TeachingCalendar.firstMonday(LocalDate.of(2026,10,6),5));assertEquals(5,TeachingCalendar.weekAt(LocalDate.of(2026,9,7),LocalDate.of(2026,10,7)))}
 @Test(expected=IllegalArgumentException::class) fun partialEmptyCannotClaimSuccess(){MergeRules.merge(old,old.copy(meetings=emptyList(),emptyConfirmed=true,full=false))}
 @Test fun splitTableHeaderKeepsServiceFields(){val h="<div class='el-table'><div class='el-table__header-wrapper'><table><thead><tr><th>课程名称</th><th>成绩</th></tr></thead></table></div><div class='el-table__body-wrapper'><table><tbody><tr><td>数学</td><td>93</td></tr></tbody></table></div></div>";val e=ScvtcParser.html(h,School.HOME,"grades");assertEquals("93",e.records.single().fields["成绩"])}
 @Test fun calendarUsesActualAttachment(){val e=ScvtcParser.html("<img alt='校历' src='/official-calendar.png'>",School.HOME,"calendar");assertEquals(School.ORIGIN+"/official-calendar.png",e.links.single().url)}
}
