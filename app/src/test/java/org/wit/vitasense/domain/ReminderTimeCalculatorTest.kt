package org.wit.vitasense.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import org.wit.vitasense.db.entity.ReminderScheduleEntity
class ReminderTimeCalculatorTest {
    private fun schedule()=ReminderScheduleEntity(kind="mood",enabled=true,minuteOfDay=20*60,description="Log mood")
    @Test fun nextTimeAndDisabled() {
        val now=ZonedDateTime.parse("2026-11-05T21:00:00+08:00[Asia/Shanghai]")
        assertEquals(6,ReminderTimeCalculator.next(schedule(),now)!!.dayOfMonth)
        assertNull(ReminderTimeCalculator.next(schedule().copy(enabled=false),now))
    }
    @Test fun repeatDay() {
        val now=ZonedDateTime.parse("2026-11-05T10:00:00+08:00[Asia/Shanghai]")
        assertEquals(DayOfWeek.SUNDAY,ReminderTimeCalculator.next(schedule().copy(repeatDays=64),now)!!.dayOfWeek)
    }
    @Test fun overnightQuietHours() {
        val now=ZonedDateTime.parse("2026-11-05T10:00:00+08:00[Asia/Shanghai]")
        val next=ReminderTimeCalculator.next(schedule().copy(minuteOfDay=23*60,quietStart=22*60,quietEnd=7*60),now)!!
        assertEquals(6,next.dayOfMonth); assertEquals(7,next.hour)
    }
    @Test fun weeklyQuietRolloverSurvivesRebootAfterMidnight() {
        val now=ZonedDateTime.parse("2026-11-09T01:00:00+08:00[Asia/Shanghai]")
        val next=ReminderTimeCalculator.next(schedule().copy(minuteOfDay=23*60,repeatDays=64,quietStart=22*60,quietEnd=7*60),now)!!
        assertEquals(9,next.dayOfMonth); assertEquals(7,next.hour)
    }
    @Test fun dstGapAndOverlap() {
        val spring=ZonedDateTime.of(2026,3,8,0,0,0,0,ZoneId.of("America/New_York"))
        assertEquals(3,ReminderTimeCalculator.next(schedule().copy(minuteOfDay=150),spring)!!.hour)
        val autumn=ZonedDateTime.of(2026,11,1,0,0,0,0,ZoneId.of("America/New_York"))
        val first=ReminderTimeCalculator.next(schedule().copy(minuteOfDay=90),autumn)!!
        assertEquals(ZoneOffset.ofHours(-4),first.offset)
        assertEquals(2,ReminderTimeCalculator.next(schedule().copy(minuteOfDay=90),first.plusMinutes(1))!!.dayOfMonth)
    }
    @Test fun invalidDaysAndQuietHours() { assertFalse(ReminderTimeCalculator.valid(schedule().copy(repeatDays=0))); assertFalse(ReminderTimeCalculator.valid(schedule().copy(quietStart=1,quietEnd=1))) }
}
