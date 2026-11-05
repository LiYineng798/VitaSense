package org.wit.vitasense.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.wit.vitasense.R
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

/** Null observations are gaps, never zeroes or interpolated points. */
class ReportTrendView(context: Context, private val values: List<Pair<String,Double?>>) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription=values.joinToString("; ") { "${it.first}: ${it.second ?: "missing"}" }; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d=resources.displayMetrics.density; val p=24*d; val bottom=height-32*d
        paint.color=ThemeAttrColorResolver.color(context,R.attr.vsColorPrimaryStrong); paint.strokeWidth=2*d
        val numbers=values.mapNotNull { it.second }; val low=numbers.minOrNull() ?: 0.0; val high=numbers.maxOrNull() ?: 1.0; val span=(high-low).coerceAtLeast(1.0)
        var previous: Pair<Float,Float>?=null
        values.forEachIndexed { index, (_,v) ->
            val x=p+(width-2*p)*index/(values.size-1).coerceAtLeast(1)
            if(v == null) { previous=null } else {
                val y=bottom-((v-low)/span*(bottom-p)).toFloat()
                previous?.let { canvas.drawLine(it.first,it.second,x,y,paint) }; canvas.drawCircle(x,y,3*d,paint); previous=x to y
            }
        }
        paint.textSize=11*resources.displayMetrics.scaledDensity; paint.color=ThemeAttrColorResolver.color(context,android.R.attr.textColorSecondary)
        canvas.drawText(if(numbers.isEmpty()) "No measurements available" else "${values.first().first} — ${values.last().first} · gaps = missing",p,height-8*d,paint)
    }
}
