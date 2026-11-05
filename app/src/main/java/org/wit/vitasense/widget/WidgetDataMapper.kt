package org.wit.vitasense.widget

import org.wit.vitasense.data.repository.display

object WidgetDataMapper {
    fun text(showDetails:Boolean, score:Int?,sleepMinutes:Int?,hrv:Double?,mood:String?, dates:String):String {
        if(!showDetails) return "Your health stays private. Tap to open VitaSense."
        if(score==null && sleepMinutes==null && hrv==null && mood==null) return "No health details available yet. Open VitaSense to add data."
        return "Today's assessment: ${score?.let { "$it/100" } ?: "—"}\nLatest sleep: ${sleepMinutes?.let { "${it/60}h ${it%60}m" } ?: "—"}  ·  HRV: ${hrv.display(" ms")}\nMood: ${mood ?: "—"}\n$dates"
    }
}
