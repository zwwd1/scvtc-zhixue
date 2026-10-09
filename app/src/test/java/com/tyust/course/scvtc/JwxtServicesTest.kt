package com.tyust.course.scvtc

import cn.scvtc.campus.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class JwxtServicesTest {
 private fun api(reply:(Request)->String)=JwxtApi(OkHttpClient.Builder().addInterceptor { c->
  Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
   .body(reply(c.request()).toResponseBody("application/json".toMediaType())).build()
 }.build())
 private val grade="""{"courseName":"Fixture Course","courseCode":"F1","studentCode":"2026000001","studentId":"2026000001","semesterId":"2026-2027-1","studentScoreFinalValue":"88","courseCredit":2,"gradePoint":3.8}"""
 @Test fun completePagesBecomeOneNativeSnapshot()=runBlocking {
  var calls=0
  val result=api{r->
   assertEquals(JwxtServices.endpoints["grades"],r.url.toString().substringBefore('?'))
   val buffer=okio.Buffer();r.body!!.writeTo(buffer);val b=Json.parseToJsonElement(buffer.readUtf8()).jsonObject
   calls++;assertEquals(calls,b["pageNo"]!!.jsonPrimitive.int)
   fun unique(i:Int)=grade.replace("{","{\"studentScoreSummaryId\":\"G$i\",",ignoreCase=false)
   val rows=if(calls==1)(1..100).joinToString(","){unique(it)}else unique(101)
   """{"code":200,"data":{"total":101,"rows":[$rows]}}"""
  }.service("grades","2026000001","2026-2027-1")
  assertEquals(101,result.records.size);assertTrue(result.full);assertEquals(2,calls)
  assertEquals("88",result.records.first().fields["成绩"])
  assertFalse(result.records.first().fields.keys.any{it.contains("student")})
 }
 @Test fun zeroTotalIsAConfirmedEmptyResult()=runBlocking {
  val e=api{"""{"code":200,"data":{"total":0,"rows":[]}}"""}.service("levelExamResults","2026000001","2026-2027-1")
  assertTrue(e.emptyConfirmed);assertTrue(e.records.isEmpty());assertTrue(e.full)
 }
 @Test fun repeatedPageCannotBecomeACompleteReplacement()=runBlocking {
  assertTrue(runCatching{api{"""{"code":200,"data":{"total":2,"rows":[$grade]}}"""}.service("grades","2026000001","2026-2027-1")}.isFailure)
 }
 @Test fun officialSemesterListUsesTheObservedStringArray()=runBlocking {
  val terms=api{"""{"code":200,"data":["2026-2027-1","2025-2026-2"]}"""}.semesters()
  assertEquals(listOf("2026-2027-1","2025-2026-2"),terms)
 }
 @Test fun changedTotalOrMissingPageNeverReturnsPartialRecords()=runBlocking {
  var count=0
  val result=runCatching{api{
   count++;if(count==1)"""{"code":200,"data":{"total":2,"rows":[$grade]}}"""else"""{"code":200,"data":{"total":2,"rows":[]}}"""
  }.service("grades","2026000001","2026-2027-1")}
  assertTrue(result.isFailure)
 }
 @Test fun otherStudentsDataIsRejected()=runBlocking {
  assertTrue(runCatching{api{"""{"code":200,"data":{"total":1,"rows":[$grade]}}"""}.service("grades","2026000002","2026-2027-1")}.isFailure)
 }
 @Test fun examsUseTheObservedTimeAndSeatFields() {
  val row=Json.parseToJsonElement("""{"studentCode":"2026000001","courseName":"Fixture Exam","positiveExamPaperDate":"2026-10-10","positiveExamTime":"09:00-11:00","studentSetNumber":"12","classroomName":"Fixture Room"}""").jsonObject
  val r=JwxtServices.record("exams",row,"2026000001")
  assertEquals("2026-10-10 09:00-11:00",r.fields["考试时间"]);assertEquals("12",r.fields["座位号"])
 }
 @Test fun htmlServerFailureDoesNotSubmitCredentials()=runBlocking {
  var restores=0
  val client=OkHttpClient.Builder().addInterceptor{c->Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(500).message("fixture").body("<html>Server failed</html>".toResponseBody("text/html".toMediaType())).build()}.build()
  assertTrue(runCatching{JwxtSessionManager(JwxtApi(client)){restores++}.verifiedStudent("2026000001")}.isFailure)
  assertEquals(0,restores)
 }
}
