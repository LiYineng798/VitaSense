package org.wit.vitasense.data.repository

import androidx.room.withTransaction
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.entity.*
import org.wit.vitasense.domain.ReminderTimeCalculator

class RemindersRepository(private val db:AppDatabase) {
    val schedules get()=db.reminderScheduleDao().observeAll()
    suspend fun initialize() {
        db.withTransaction {
            listOf("mood" to "Take a moment to log how you feel.", "sleep" to "Make time to wind down and review sleep trends.", "weekly" to "Review your weekly health report.", "family" to "Check in with your family today.").forEach { (kind,text) ->
                if(db.reminderScheduleDao().get(kind)==null) db.reminderScheduleDao().upsert(ReminderScheduleEntity(kind=kind,description=text,repeatDays=if(kind=="weekly") 64 else 127))
            }
        }
    }
    suspend fun save(s:ReminderScheduleEntity): ReminderScheduleEntity {
        require(ReminderTimeCalculator.valid(s)) { "Choose a time, at least one repeat day, and different quiet-hour start/end times. Description must contain 1–200 characters." }
        val updated=s.copy(updatedAt=maxOf(System.currentTimeMillis(),(db.reminderScheduleDao().get(s.kind)?.updatedAt ?: 0)+1))
        db.reminderScheduleDao().upsert(updated); return updated
    }
    suspend fun all()=db.reminderScheduleDao().all()
    suspend fun get(kind:String)=db.reminderScheduleDao().get(kind)
    suspend fun widgetDetails()=db.appSettingDao().get("widget_health_details")?.value=="true"
    suspend fun setWidgetDetails(show:Boolean) { db.appSettingDao().upsert(AppSettingEntity("widget_health_details",show.toString())) }
    suspend fun claimNotification(kind:String,occurrence:Long):Boolean=db.withTransaction {
        val key="reminder_last_occurrence_$kind"; val previous=db.appSettingDao().get(key)?.value?.toLongOrNull() ?: 0L
        if(occurrence<=previous) false else { db.appSettingDao().upsert(AppSettingEntity(key,occurrence.toString())); true }
    }
}
