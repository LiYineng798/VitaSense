package org.wit.vitasense

import android.app.Application

class VitaSenseApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        org.wit.vitasense.widget.WidgetUpdates.enqueue(this)
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork("vitasense-reminder-reschedule", androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<org.wit.vitasense.background.ReminderRescheduleWorker>().build())
    }
}
