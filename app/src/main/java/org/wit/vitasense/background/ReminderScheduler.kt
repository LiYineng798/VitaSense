package org.wit.vitasense.background

import android.content.Context
import androidx.work.*
import org.wit.vitasense.db.entity.ReminderScheduleEntity
import org.wit.vitasense.domain.ReminderTimeCalculator
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(context:Context) {
    private val work=WorkManager.getInstance(context.applicationContext)
    fun schedule(s:ReminderScheduleEntity, successor:Boolean=false): Operation? {
        val name=workName(s.kind)
        if(!s.enabled) return work.cancelUniqueWork(name)
        val now=ZonedDateTime.now(); val next=ReminderTimeCalculator.next(s,now) ?: return null
        val request=OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now,next).toMillis().coerceAtLeast(0),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("kind" to s.kind,"version" to s.updatedAt,"occurrence" to next.toInstant().toEpochMilli())).addTag(name).build()
        return work.enqueueUniqueWork(name, if(successor) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,request)
    }
    companion object { fun workName(kind:String)="vitasense-reminder-$kind" }
}
