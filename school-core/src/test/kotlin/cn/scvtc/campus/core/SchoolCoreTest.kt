package cn.scvtc.campus.core

import org.junit.Test
import org.junit.Assert.*

/** Synthetic test fixtures live only in the test source set. */
class SchoolCoreTest {
 @Test fun weeksAboveTwentyAndDiscrete(){assertEquals(listOf(1,3,5,21,23,25,27,28),WeekRules.parse("1,3,5,21,23,25,27-28周"))}
 @Test fun oddWeeks(){assertEquals((1..27 step 2).toList(),WeekRules.parse("1-28周（单）"))}
 @Test fun evenWeeks(){assertEquals((2..28 step 2).toList(),WeekRules.parse("1—28（双）周"))}
 @Test(expected=IllegalArgumentException::class) fun invalidClassNotWeeks(){WeekRules.parse("25电气5（45人）")}
 @Test(expected=IllegalArgumentException::class) fun conflictingParity(){WeekRules.parse("1-20单双周")}
 @Test fun splitNonConsecutiveNodes(){assertEquals(listOf(1..2,5..6),WeekRules.runs(WeekRules.nodes("第1,2,5,6节")))}
 private fun meeting(weeks:List<Int>,name:String="测试课程",room:String="测试楼A101")=Meeting(name=name,day=1,startNode=1,endNode=2,weeks=weeks,room=room)
 private fun ex(m:List<Meeting>,weeks:List<Int> = emptyList(),full:Boolean=false)=Extraction("schedule","test-account","test-semester",School.SCHEDULE,m,coverageWeeks=weeks,full=full)
 @Test fun partialQueryPreservesOtherWeeks(){
  val result=MergeRules.merge(ex(listOf(meeting(listOf(1,2,24)))),ex(listOf(meeting(listOf(28)))))
  assertEquals(listOf(1,2,24,28),result.meetings.single().weeks)
 }
 @Test fun emptyWeekDoesNotEraseOtherWeeks(){
  val next=ex(emptyList(),listOf(7),true).copy(emptyConfirmed=true)
  val merged=MergeRules.merge(ex(listOf(meeting(listOf(6,7,8,28)))),next)
  assertEquals(listOf(6,8,28),merged.meetings.single().weeks)
 }
 @Test(expected=IllegalArgumentException::class) fun emptyWeekWithUnknownRangeCannotCommit(){
  MergeRules.merge(ex(listOf(meeting(listOf(7,28)))),ex(emptyList()).copy(emptyConfirmed=true))
 }
 @Test fun successfulEmptyApiIsDifferentFromUnknownShape(){
  val empty=ScvtcParser.api("""{"code":200,"data":[]}""",School.SCHEDULE,"schedule")
  assertTrue(empty.emptyConfirmed);assertTrue(empty.warnings.isEmpty())
  val unknown=ScvtcParser.api("""{"code":200,"data":{"unrecognized":[]}}""",School.SCHEDULE,"schedule")
  assertFalse(unknown.emptyConfirmed);assertTrue(unknown.warnings.isNotEmpty())
 }
 @Test fun fullRangeReplacesOnlyThatRange(){
  val result=MergeRules.merge(ex(listOf(meeting(listOf(1,2,24)))),ex(listOf(meeting(listOf(2),"测试调课")),listOf(1,2),true))
  assertEquals(listOf(24),result.meetings.first{it.name=="测试课程"}.weeks)
 }
 @Test(expected=IllegalArgumentException::class) fun accountsNeverMerge(){
  MergeRules.merge(ex(listOf(meeting(listOf(1)))),ex(emptyList()).copy(account="other-test-account"))
 }
 @Test fun sameNameDifferentPlacesAreSeparate(){
  assertEquals(2,MergeRules.merge(ex(listOf(meeting(listOf(1)))),ex(listOf(meeting(listOf(1),room="另一测试楼")))).meetings.size)
 }
 @Test fun loginDiagnosis(){assertTrue(ScvtcParser.html("<input type='password'>",School.HOME,"schedule").warnings.any{it.contains("登录")})}
 @Test fun htmlUsesSemanticWeekAndNodes(){
  val html="""<select><option selected>2026-2027-1</option></select><table><thead><tr><th>节次</th><th>星期一</th></tr></thead><tbody><tr><td>第1-2节</td><td><div data-course-name="测试课"><div>测试课</div><div>1,3,21-28周（第1,2节）</div><div data-label="教室">教室：测试楼A101</div><div>测试班（45人）</div><div data-label="教师">教师：测试教师</div></div></td></tr></tbody></table>"""
  val result=ScvtcParser.html(html,School.SCHEDULE,"schedule")
  assertEquals(1,result.meetings.size);val m=result.meetings.single()
  assertEquals(listOf(1,3)+(21..28),m.weeks)
  assertEquals("测试楼A101",m.room);assertEquals("测试教师",m.teacher);assertEquals(1,m.day)
  assertFalse(result.full)
 }
 @Test fun nestedJsonRetainsContext(){
  val json="""{"code":200,"data":[{"week":{"weekCode":"3","weekName":"星期二"},"time":{"timeName":"第7,8节"},"courses":[{"courseName":"测试课","weeks":"1-28双周","classroomName":"测试室","teacherName":"测试师"}]}]}"""
  val r=ScvtcParser.api(json,School.ORIGIN+"/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule","schedule")
  assertEquals(2,r.meetings.single().day);assertEquals(7,r.meetings.single().startNode);assertEquals(28,r.meetings.single().weeks.last())
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsLoginResponseCode(){ScvtcParser.api("""{"code":401,"data":[]}""",School.SCHEDULE,"schedule")}
 @Test(expected=IllegalArgumentException::class) fun rejectsForeignOrigin(){ScvtcParser.html("<table/>","https://example.com","schedule")}
 @Test fun trustedOriginHasNoLookalikes(){assertFalse(School.trusted("https://jwxt.scvtc.edu.cn.attacker.example/"));assertFalse(School.trusted("http://jwxt.scvtc.edu.cn/"));assertFalse(School.trusted("https://user@jwxt.scvtc.edu.cn/"))}
}
