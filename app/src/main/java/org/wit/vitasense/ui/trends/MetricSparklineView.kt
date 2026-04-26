package org.wit.vitasense.ui.trends

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.LinearGradient
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class MetricSparklineView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        var seriesModel: MiniTrendSeriesModel? = null
            set(value) {
                field = value
                invalidate()
            }

        var accentColor: Int = Color.BLACK
            set(value) {
                field = value
                invalidate()
            }

        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val dotPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val values = seriesModel?.values.orEmpty()
            if (values.size < 2) return

            val left = dp(2f)
            val top = dp(6f)
            val right = width - dp(2f)
            val bottom = height - dp(6f)
            val stepX = (right - left) / (values.size - 1)
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 1f
            val range = (max - min).takeIf { it > 0f } ?: 1f

            val linePath = Path()
            val fillPath = Path()

            values.forEachIndexed { index, value ->
                val x = left + stepX * index
                val normalized = (value - min) / range
                val y = bottom - normalized * (bottom - top)

                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, bottom)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == values.lastIndex) {
                    fillPath.lineTo(x, bottom)
                    fillPath.close()
                }
            }

            fillPaint.shader =
                LinearGradient(
                    0f,
                    top,
                    0f,
                    bottom,
                    withAlpha(accentColor, 90),
                    withAlpha(accentColor, 0),
                    Shader.TileMode.CLAMP,
                )

            linePaint.color = accentColor
            dotPaint.color = accentColor

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)

            val firstY = bottom - ((values.first() - min) / range) * (bottom - top)
            val lastY = bottom - ((values.last() - min) / range) * (bottom - top)
            canvas.drawCircle(left, firstY, dp(2.4f), dotPaint)
            canvas.drawCircle(right, lastY, dp(2.8f), dotPaint)
        }

        private fun withAlpha(
            color: Int,
            alpha: Int,
        ): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                resources.displayMetrics,
            )
    }
