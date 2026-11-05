package org.wit.vitasense.background

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.wit.vitasense.MainActivity
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.domain.ReminderTimeCalculator
import java.time.ZonedDateTime

class ReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=(applicationContext as VitaSenseApplication).appContainer
        val kind=inputData.getString("kind") ?: return Result.failure()
        val s=app.remindersRepository.get(kind) ?: return Result.success()
        if(!s.enabled || s.updatedAt!=inputData.getLong("version",-1)) return Result.success()
        val now=ZonedDateTime.now()
        val occurrence=inputData.getLong("occurrence",0)
        // OS may delay inexact work. Never deliver during quiet hours or on a different calendar day.
        val sameDay=java.time.Instant.ofEpochMilli(occurrence).atZone(now.zone).toLocalDate()==now.toLocalDate()
        if(sameDay && !ReminderTimeCalculator.quiet(s,now.toLocalTime()) && allowed(applicationContext)) {
            channels(applicationContext)
            if(app.remindersRepository.claimNotification(kind,occurrence)) {
                val title=when(kind) { "mood"->"Daily Mood Reminder"; "sleep"->"Sleep Reminder"; "weekly"->"Weekly Health Report"; else->"Family Check-in" }
                val target=when(kind) { "mood"->"mood"; "sleep"->"trends"; "weekly"->"reports"; else->"family" }
                val action=when(kind) { "mood"->"Log Mood"; "sleep"->"Open Sleep Trends"; "weekly"->"Generate Weekly Report"; else->"I'm OK" }
                val notification=NotificationCompat.Builder(applicationContext,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(title).setContentText(s.description).setStyle(NotificationCompat.BigTextStyle().bigText(s.description))
                    .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(open(applicationContext,target,false))
                    .addAction(0,action,open(applicationContext,target,kind=="family"))
                    .addAction(0,if(kind=="family") "Open Family" else "Open VitaSense",open(applicationContext,if(kind=="family") "family" else "home",false)).build()
                try { NotificationManagerCompat.from(applicationContext).notify(kind.hashCode(),notification) } catch(_:SecurityException) { /* Permission may be revoked between check and delivery. */ }
            }
        }
        app.reminderScheduler.schedule(s,successor=true)
        return Result.success()
    }
    companion object {
        const val CHANNEL="vitasense_local_reminders"
        fun allowed(context:Context)= (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled() && (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
        fun channels(context:Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"VitaSense Reminders",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Optional local mood, sleep, report and family reminders." }) }
        private fun open(context:Context,target:String,confirm:Boolean)=PendingIntent.getActivity(context,(target+confirm).hashCode(),Intent(context,MainActivity::class.java).setAction("vitasense.$target.$confirm").putExtra("destination",target).putExtra("confirm_ok",confirm).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderRescheduleWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=(applicationContext as VitaSenseApplication).appContainer
        app.remindersRepository.initialize(); app.remindersRepository.all().forEach { app.reminderScheduler.schedule(it) }
        return Result.success()
    }
}
class ReminderTimeChangeReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        org.wit.vitasense.widget.WidgetUpdates.enqueue(context)
        WorkManager.getInstance(context).enqueueUniqueWork("vitasense-reminder-reschedule",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderRescheduleWorker>().build())
    }
}
