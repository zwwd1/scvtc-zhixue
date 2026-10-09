package cn.scvtc.campus.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.*

data class OfficialTerm(val semester: String, val firstMonday: LocalDate)

/** Actual JWGR selectCurrentXnXq shape; never replace an existing user calibration. */
object OfficialSemester {
    fun parse(body: String): OfficialTerm? = runCatching {
        require(body.length <= 100_000)
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["code"]?.jsonPrimitive?.content in setOf("200", "0"))
        require(root["success"]?.jsonPrimitive?.booleanOrNull != false)
        val data = root["data"]!!.jsonObject
        val semester = data["semester"]!!.jsonPrimitive.content
        val match = Regex("(20[0-9]{2})-(20[0-9]{2})-([12])").matchEntire(semester)!!
        val year = match.groupValues[1].toInt()
        require(match.groupValues[2].toInt() == year + 1)
        val start = LocalDate.parse(data["ksrq"]!!.jsonPrimitive.content)
        val end = LocalDate.parse(data["jsrq"]!!.jsonPrimitive.content)
        require(start.year in year..year+1 && end >= start && end <= start.plusDays(370))
        OfficialTerm(semester, start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }.getOrNull()
}
