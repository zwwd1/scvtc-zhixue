package cn.scvtc.campus.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class OfficialSemesterTest {
    private val body = """{"code":"200","data":{"xndm":"2026-2027","xqdm":"1","ksrq":"2026-09-10","jsrq":"2027-01-21","semester":"2026-2027-1"}}"""
    @Test fun actualTermShapeResolvesTheTeachingMonday() {
        val term = OfficialSemester.parse(body)!!
        assertEquals("2026-2027-1", term.semester)
        assertEquals(LocalDate.of(2026,9,7), term.firstMonday)
    }
    @Test fun unsuccessfulResponsesCannotCalibrateAnExistingTerm() {
        assertNull(OfficialSemester.parse(body.replace("200", "403")))
        assertNull(OfficialSemester.parse(body.replace("\"data\"", "\"success\":false,\"data\"")))
    }
    @Test fun malformedOrContradictoryDatesCannotCalibrate() {
        assertNull(OfficialSemester.parse(body.replace("2026-09-10", "2025-09-10")))
        assertNull(OfficialSemester.parse(body.replace("2027-01-21", "2026-01-21")))
        assertNull(OfficialSemester.parse(body.replace("2026-2027-1", "2026-2029-1")))
    }
    @Test fun loginHtmlAndMissingMetadataAreRejected() {
        assertNull(OfficialSemester.parse("<html>登录</html>"))
        assertNull(OfficialSemester.parse("""{"code":200,"data":{}}"""))
    }
}
