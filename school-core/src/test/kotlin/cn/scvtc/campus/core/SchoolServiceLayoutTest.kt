package cn.scvtc.campus.core

import org.junit.Assert.*
import org.junit.Test

class SchoolServiceLayoutTest {
 @Test fun splitAntHeaderMapsGradesAndIgnoresEmptyPlaceholder(){
  val html="""<div class="ant-table"><div class="ant-table-header"><table><thead><tr><th>课程号</th><th>课程名称</th><th>成绩</th></tr></thead></table></div><div class="ant-table-body"><table><tbody><tr><td>C1</td><td>课程甲</td><td>90</td></tr><tr class="ant-table-placeholder"><td colspan="3">暂无数据</td></tr></tbody></table></div></div>"""
  val rows=ScvtcParser.html(html,School.HOME,"grades").records
  assertEquals(1,rows.size);assertEquals("课程甲",rows.single().fields["课程名称"]);assertEquals("90",rows.single().fields["成绩"])
 }
 @Test fun verifiedCreditLabelsKeepZeroAndRejectAmbiguousValues(){
  val rows=ScvtcParser.html("<span>已完成毕业学分0分</span><span>未完成毕业学分137分</span>",School.HOME,"credits").records
  assertEquals("0",rows.single().fields["已完成毕业学分"]);assertEquals("137",rows.single().fields["未完成毕业学分"])
  assertTrue(ScvtcParser.html("已完成毕业学分0分 已完成毕业学分12分 未完成毕业学分137分",School.HOME,"credits").records.isEmpty())
 }
 @Test(expected=IllegalArgumentException::class) fun navigationLinksCannotPretendToBeGradeData(){
  ServicePages().offer(Extraction("grades","account","term",links=listOf(ServiceLink("主页",School.HOME))),"run","epoch",1,false)
 }
}
