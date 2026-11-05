package org.wit.vitasense.domain

import org.wit.vitasense.db.entity.ReminderScheduleEntity
import java.time.*

object ReminderTimeCalculator {
    fun valid(s: ReminderScheduleEntity): Boolean = s.kind in setOf("mood","sleep","weekly","family") && s.minuteOfDay in 0..1439 && s.repeatDays in 1..127 && s.description.isNotBlank() && s.description.length<=200 &&
        ((s.quietStart==null && s.quietEnd==null) || (s.quietStart in 0..1439 && s.quietEnd in 0..1439 && s.quietStart != s.quietEnd))
    fun quiet(s: ReminderScheduleEntity, time: LocalTime): Boolean {
        val start=s.quietStart ?: return false; val end=s.quietEnd ?: return false; val minute=time.hour*60+time.minute
        return if(start < end) minute in start until end else minute>=start || minute<end
    }
    /** DST gaps move forward using java.time; overlaps select the first occurrence once. */
    fun next(s: ReminderScheduleEntity, now: ZonedDateTime): ZonedDateTime? {
        if(!s.enabled || !valid(s)) return null
        for(offset in -1L..14L) {
            val date=now.toLocalDate().plusDays(offset)
            if(s.repeatDays and (1 shl (date.dayOfWeek.value-1))==0) continue
            var candidate=date.atTime(s.minuteOfDay/60,s.minuteOfDay%60).atZone(now.zone)
            if(quiet(s,candidate.toLocalTime())) {
                val end=s.quietEnd!!
                val roll=if(s.quietStart!!>end && candidate.hour*60+candidate.minute>=s.quietStart) 1L else 0L
                candidate=date.plusDays(roll).atTime(end/60,end%60).atZone(now.zone)
            }
            // Repeat days select the preferred date; quiet hours can defer its delivery past midnight.
            if(candidate.isAfter(now) && !quiet(s,candidate.toLocalTime())) return candidate
        }
        return null
    }
}
