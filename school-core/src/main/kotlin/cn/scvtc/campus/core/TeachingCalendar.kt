package cn.scvtc.campus.core
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
object TeachingCalendar {
 fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))
 fun firstMonday(date: LocalDate, teachingWeek: Int): LocalDate {
  require(teachingWeek in 1..100)
  return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((teachingWeek-1).toLong())
 }
 fun weekAt(monday: LocalDate, date: LocalDate = today()): Int =
  Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(monday,date),7L).toInt()+1
}
