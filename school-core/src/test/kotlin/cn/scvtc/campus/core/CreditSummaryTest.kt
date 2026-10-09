package cn.scvtc.campus.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditSummaryTest {
    private fun grade(code:String,acquired:String?,credits:String="9")=NativeRecord("测试记录",buildMap{
        put("课程代码",code);put("学分",credits);acquired?.let{put("获得学分",it)}
    })
    @Test fun `retakes count the highest official acquired credits once per course code`(){
        val summary=CreditSummary.from(listOf(grade("A","0"),grade("A","1.5"),grade("A","1.5"),grade("B","2.25")))
        assertEquals("3.75",summary.display);assertEquals(2,summary.courses)
    }
    @Test fun `missing or invalid acquired credits cannot be inferred from course credits or scores`(){
        val summary=CreditSummary.from(listOf(grade("A",null),grade("B","待核验"),grade("C","-1"),grade("","3"),grade("D","0")))
        assertEquals("0",summary.display);assertEquals(1,summary.courses);assertEquals(4,summary.uncounted)
    }
}
