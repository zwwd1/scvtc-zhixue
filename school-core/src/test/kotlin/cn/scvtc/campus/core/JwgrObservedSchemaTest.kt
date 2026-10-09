package cn.scvtc.campus.core

import org.junit.Assert.*
import org.junit.Test

/** Field topology from the live JWGR response; all account/course values are synthetic. */
class JwgrObservedSchemaTest {
    private val source=School.ORIGIN+"/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule"
    private val cell="""{"time":{"timeCode":"1-2","timeName":"第1-2节","sort":"1"},"week":{"weekCode":"2","weekName":"星期一","sort":"1"},"courseList":[{"dayOfWeek":"2","dayOfWeekName":"星期一","time":"1,2","courseName":"测试课程","weeks":"5-5","teacherName":"测试教师","classroomName":"测试楼A101","semesters":"2026-2027-1","courseCode":"TEST01","teachingClassName":"测试班"}]}"""
    @Test fun actualWeekCodeTwoMeansMondayAndNestedTimeIsRetained(){
        val result=ScvtcParser.api("""{"code":"200","data":[$cell]}""",source,"schedule","2026123456","2026-2027-1")
        val course=result.meetings.single()
        assertEquals(1,course.day);assertEquals(1,course.startNode);assertEquals(2,course.endNode)
        assertEquals(listOf(5),course.weeks);assertEquals("测试楼A101",course.room);assertEquals("测试教师",course.teacher)
        assertTrue(result.warnings.isEmpty());assertFalse(result.full)
    }
    @Test fun confirmedEmptyCellsWithoutKnownWeekCannotCommit(){
        val body="""{"code":"200","data":[{"time":{"timeName":"第1-2节"},"week":{"weekName":"星期一"},"courseList":[]}]}"""
        val result=ScvtcParser.api(body,source,"schedule")
        assertTrue(result.emptyConfirmed)
        assertThrows(IllegalArgumentException::class.java){ScheduleWriteGuard.validate(null,result)}
    }
    @Test fun provenSingleEmptyWeekPreservesOtherWeeks(){
        val previous=ScvtcParser.api("""{"code":"200","data":[$cell]}""",source,"schedule","2026123456","2026-2027-1")
        val old=previous.copy(meetings=previous.meetings.map{it.copy(weeks=listOf(4,5,6))})
        val empty=ScvtcParser.api("""{"code":"200","data":[]}""",source,"schedule",old.account,old.semester,listOf(5)).copy(full=true)
        assertEquals(listOf(4,6),MergeRules.merge(old,empty).meetings.single().weeks)
    }
    @Test fun actualIdentityRowsRequireBusinessSuccess(){
        val rows="""{"total":1,"rows":[{"xh":"2026123456","xm":"测试学生"}]}"""
        assertEquals("2026123456",OfficialIdentity.account("""{"code":"200","data":$rows}"""))
        assertEquals("",OfficialIdentity.account("""{"code":"500","data":$rows}"""))
    }
}
