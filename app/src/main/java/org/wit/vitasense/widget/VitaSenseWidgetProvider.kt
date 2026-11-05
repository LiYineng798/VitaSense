package org.wit.vitasense.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.widget.RemoteViews
import androidx.room.InvalidationTracker
import androidx.work.*
import org.wit.vitasense.R
import org.wit.vitasense.MainActivity
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver
import org.wit.vitasense.ui.theme.ThemeFamilyStyleResolver
import org.wit.vitasense.model.ThemeMode
import kotlinx.coroutines.flow.first
import java.time.*

class VitaSenseWidgetProvider:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        ids.forEach { manager.updateAppWidget(it,RemoteViews(context.packageName,R.layout.widget_vitasense).apply { setTextViewText(R.id.widgetDetails,"Loading…") }) }
        WidgetUpdates.enqueue(context)
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { WidgetUpdates.enqueue(context) }
    override fun onReceive(context:Context,intent:Intent) { super.onReceive(context,intent); if(intent.action==REFRESH) WidgetUpdates.enqueue(context) }
    companion object { const val REFRESH="org.wit.vitasense.WIDGET_REFRESH" }
}
object WidgetUpdates {
    fun enqueue(context:Context) {
        val ids=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,VitaSenseWidgetProvider::class.java))
        if(ids.isNotEmpty()) WorkManager.getInstance(context).enqueueUniqueWork("vitasense-widget-refresh",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
    fun observe(context:Context,database:AppDatabase, onChange: () -> Unit = { enqueue(context) }) {
        database.invalidationTracker.addObserver(object:InvalidationTracker.Observer("daily_physiology_summary","risk_assessment_records","mood_records","sleep_records","app_settings") {
            override fun onInvalidated(tables:Set<String>) { onChange() }
        })
    }
}
class WidgetRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext; val app=(c as VitaSenseApplication).appContainer
        val manager=AppWidgetManager.getInstance(c); val ids=manager.getAppWidgetIds(ComponentName(c,VitaSenseWidgetProvider::class.java))
        if(ids.isEmpty()) return Result.success()
        val show=app.remindersRepository.widgetDetails()
        val data=if(show) app.widgetSnapshot() else WidgetSnapshot(null,null,null,null,"")
        val config=android.content.res.Configuration(c.resources.configuration)
        config.uiMode=(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or if(app.settingsRepository.getThemeMode()==ThemeMode.DARK) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        val theme=android.view.ContextThemeWrapper(c.createConfigurationContext(config),ThemeFamilyStyleResolver.styleFor(app.settingsRepository.getThemeFamily()))
        val views=RemoteViews(c.packageName,R.layout.widget_vitasense)
        views.setInt(R.id.widgetRoot,"setBackgroundColor",ThemeAttrColorResolver.color(theme,com.google.android.material.R.attr.colorSurface))
        listOf(R.id.widgetBrand,R.id.widgetDetails,R.id.widgetUpdated,R.id.widgetRefresh).forEach { views.setTextColor(it,ThemeAttrColorResolver.color(theme,android.R.attr.textColorPrimary)) }
        views.setTextViewText(R.id.widgetDetails,WidgetDataMapper.text(show,data.score,data.sleep,data.hrv,data.mood,data.dates))
        views.setTextViewText(R.id.widgetUpdated,"Updated ${LocalTime.now().withSecond(0).withNano(0)} · Tap to open")
        val open=PendingIntent.getActivity(c,88,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widgetRoot,open)
        views.setOnClickPendingIntent(R.id.widgetRefresh,PendingIntent.getBroadcast(c,89,Intent(c,VitaSenseWidgetProvider::class.java).setAction(VitaSenseWidgetProvider.REFRESH),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        manager.updateAppWidget(ids,views)
        return Result.success()
    }
}
data class WidgetSnapshot(val score:Int?,val sleep:Int?,val hrv:Double?,val mood:String?,val dates:String)
