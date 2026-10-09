package com.tyust.course.schedule

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class SchoolDateRegressionTest {
    private val monday="2026-09-07"
    @Test fun confirmedWednesdayIsWeekFive(){val now=Instant.parse("2026-10-07T04:00:00Z").toEpochMilli();assertEquals(5,ScheduleDates.weekIndexAt(monday,now));assertEquals(3,ScheduleDates.dayAt(now))}
    @Test fun shanghaiSundayRollsIntoMondayWeekSix(){val sunday=Instant.parse("2026-10-11T15:59:59Z").toEpochMilli();assertEquals(5,ScheduleDates.weekIndexAt(monday,sunday));assertEquals(7,ScheduleDates.dayAt(sunday));assertEquals(6,ScheduleDates.weekIndexAt(monday,sunday+1000));assertEquals(1,ScheduleDates.dayAt(sunday+1000))}
    @Test fun changingPhoneZoneDoesNotChangeSchoolDate(){val previous=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));val now=Instant.parse("2026-10-11T16:00:00Z").toEpochMilli();assertEquals(6,ScheduleDates.weekIndexAt(monday,now));assertEquals(1,ScheduleDates.dayAt(now))}finally{TimeZone.setDefault(previous)}}
    @Test fun noCalendarDoesNotInventAWeek(){assertNull(ScheduleDates.weekIndexAt("",Instant.now().toEpochMilli()));assertNull(ScheduleDates.weekIndexAt("2026-09-08",Instant.now().toEpochMilli()))}
}
