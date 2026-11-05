package org.wit.vitasense.domain

import android.app.Application
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.wit.vitasense.background.ReminderScheduler
import org.wit.vitasense.db.entity.ReminderScheduleEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ReminderSchedulingTest {
    @Test fun replaceChangeAndCancelUniqueSchedule() {
        val context=RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,Configuration.Builder().setExecutor(java.util.concurrent.Executor { it.run() }).build())
        val scheduler=ReminderScheduler(context); val work=WorkManager.getInstance(context)
        val schedule=ReminderScheduleEntity(kind="mood",enabled=true,minuteOfDay=1439,description="Log mood",updatedAt=1)
        try {
            scheduler.schedule(schedule)!!.result.get()
            scheduler.schedule(schedule.copy(minuteOfDay=1430,updatedAt=2))!!.result.get()
            val infos=work.getWorkInfosForUniqueWork(ReminderScheduler.workName("mood")).get()
            assertEquals(1,infos.count { !it.state.isFinished })
            scheduler.schedule(schedule.copy(enabled=false))!!.result.get()
            assertTrue(work.getWorkInfosForUniqueWork(ReminderScheduler.workName("mood")).get().all { it.state.isFinished })
        } finally { WorkManagerTestInitHelper.closeWorkDatabase() }
    }
}
