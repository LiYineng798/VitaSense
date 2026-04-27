package org.wit.vitasense.ui.common.chart

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.wit.vitasense.R
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

class SimpleLineChartView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        var chartModel: TrendChartModel = TrendChartModel.Empty
            set(value) {
                field = value
                selectedIndex = value.selectionIndex
                restartRevealAnimation()
                invalidate()
            }

        private var selectedIndex: Int = chartModel.selectionIndex
        private var revealProgress: Float = 1f
        private var revealAnimator: ValueAnimator? = null

        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val panelStrokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
            }
        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
            }
        private val guidePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.2f)
            }
        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = dp(2.5f)
            }
        private val pointHaloPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val pointOuterPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val pointInnerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val chipPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val axisTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = sp(9f)
            }
        private val chipTitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(9.5f)
            }
        private val chipValuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(12f)
                isFakeBoldText = true
            }
        private val badgeTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = sp(9f)
            }

        private val linePath = Path()
        private val fillPath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val palette = resolvePalette(chartModel.tone)
            val panelRect = buildPanelRect()
            drawPanel(canvas, panelRect, palette)

            if (chartModel !is TrendChartModel.Line) {
                drawEmptyState(canvas, panelRect, palette)
                return
            }

            val model = chartModel as TrendChartModel.Line
            if (model.entries.isEmpty()) {
                drawEmptyState(canvas, panelRect, palette)
                return
            }

            val headerRect = buildHeaderRect(panelRect)
            val plotRect = buildPlotRect(panelRect, headerRect)
            val centers = slotCenters(model.entries.size, plotRect)

            drawGrid(canvas, plotRect, palette)

            canvas.save()
            canvas.clipRect(
                plotRect.left,
                plotRect.top,
                plotRect.left + plotRect.width() * revealProgress,
                panelRect.bottom,
            )
            drawGuideLine(canvas, centers[selectedIndex.coerceIn(0, centers.lastIndex)], plotRect, palette)
            drawLineChart(canvas, plotRect, model, centers, palette)
            canvas.restore()

            drawHeader(canvas, headerRect, model, palette)
            drawAxisLabels(canvas, panelRect, plotRect, model, centers, palette)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (chartModel !is TrendChartModel.Line) {
                return super.onTouchEvent(event)
            }

            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    updateSelection(event.x)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        performClick()
                    }
                    true
                }

                else -> super.onTouchEvent(event)
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDetachedFromWindow() {
            revealAnimator?.cancel()
            revealAnimator = null
            super.onDetachedFromWindow()
        }

        private fun drawPanel(
            canvas: Canvas,
            panelRect: RectF,
            palette: ChartPalette,
        ) {
            panelPaint.shader =
                LinearGradient(
                    panelRect.left,
                    panelRect.top,
                    panelRect.right,
                    panelRect.bottom,
                    palette.panelStart,
                    palette.panelEnd,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRoundRect(panelRect, dp(22f), dp(22f), panelPaint)

            panelStrokePaint.color = palette.panelStroke
            canvas.drawRoundRect(panelRect, dp(22f), dp(22f), panelStrokePaint)
        }

        private fun drawGrid(
            canvas: Canvas,
            plotRect: RectF,
            palette: ChartPalette,
        ) {
            gridPaint.color = palette.grid
            gridPaint.alpha = 145

            val rowCount = 4
            repeat(rowCount) { index ->
                val y = plotRect.top + (plotRect.height() / (rowCount - 1)) * index
                canvas.drawLine(plotRect.left, y, plotRect.right, y, gridPaint)
            }

            val columnCount = 4
            repeat(columnCount) { index ->
                val x = plotRect.left + (plotRect.width() / (columnCount - 1)) * index
                canvas.drawLine(x, plotRect.top, x, plotRect.bottom, gridPaint)
            }
        }

        private fun drawLineChart(
            canvas: Canvas,
            plotRect: RectF,
            model: TrendChartModel.Line,
            centers: List<Float>,
            palette: ChartPalette,
        ) {
            val top = plotRect.top + dp(3f)
            val bottom = plotRect.bottom - dp(2f)
            val points =
                model.entries.mapIndexed { index, entry ->
                    PointF(
                        centers[index],
                        mapY(entry.value, model.minValue, model.maxValue, top, bottom),
                    )
                }

            val path = buildSmoothPath(points)
            fillPath.reset()
            fillPath.addPath(path)
            fillPath.lineTo(points.last().x, plotRect.bottom)
            fillPath.lineTo(points.first().x, plotRect.bottom)
            fillPath.close()

            fillPaint.shader =
                LinearGradient(
                    plotRect.left,
                    plotRect.top,
                    plotRect.left,
                    plotRect.bottom,
                    ColorUtils.setAlphaComponent(palette.toneMid, 44),
                    ColorUtils.setAlphaComponent(palette.toneLight, 0),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawPath(fillPath, fillPaint)

            linePaint.color = palette.line
            canvas.drawPath(path, linePaint)

            val drawAllPoints = model.entries.size <= 12
            model.entries.forEachIndexed { index, entry ->
                if (!drawAllPoints && index != selectedIndex && !entry.highlighted) {
                    return@forEachIndexed
                }
                drawPoint(
                    canvas = canvas,
                    point = points[index],
                    palette = palette,
                    selected = index == selectedIndex,
                    highlighted = entry.highlighted,
                )
            }
        }

        private fun drawPoint(
            canvas: Canvas,
            point: PointF,
            palette: ChartPalette,
            selected: Boolean,
            highlighted: Boolean,
        ) {
            if (highlighted || selected) {
                pointHaloPaint.color = ColorUtils.setAlphaComponent(palette.toneDeep, if (selected) 64 else 42)
                canvas.drawCircle(point.x, point.y, dp(if (selected) 7.2f else 6f), pointHaloPaint)
            }

            pointOuterPaint.color = palette.pointShell
            canvas.drawCircle(point.x, point.y, dp(if (selected) 4.2f else 3.6f), pointOuterPaint)

            pointInnerPaint.color = if (highlighted) palette.toneDeep else palette.toneMid
            canvas.drawCircle(point.x, point.y, dp(if (selected) 2.8f else 2.3f), pointInnerPaint)
        }

        private fun drawHeader(
            canvas: Canvas,
            headerRect: RectF,
            model: TrendChartModel.Line,
            palette: ChartPalette,
        ) {
            val selectedEntry = model.entries[selectedIndex.coerceIn(0, model.entries.lastIndex)]
            val title = compactLabel(selectedEntry.detailLabel)
            val value = selectedEntry.valueText

            val chipWidth =
                min(
                    headerRect.width() * 0.7f,
                    max(chipTitlePaint.measureText(title), chipValuePaint.measureText(value)) + dp(26f),
                )
            val chipRect =
                RectF(
                    headerRect.left,
                    headerRect.top,
                    headerRect.left + chipWidth,
                    headerRect.top + dp(34f),
                )

            val chipStart =
                if (selectedEntry.highlighted) {
                    ColorUtils.blendARGB(palette.toneLight, palette.toneMid, 0.52f)
                } else {
                    ColorUtils.blendARGB(palette.panelStart, palette.toneLight, 0.66f)
                }
            val chipEnd =
                if (selectedEntry.highlighted) {
                    ColorUtils.blendARGB(palette.toneMid, palette.toneDeep, 0.26f)
                } else {
                    ColorUtils.blendARGB(palette.panelEnd, palette.toneMid, 0.24f)
                }
            chipPaint.shader =
                LinearGradient(
                    chipRect.left,
                    chipRect.top,
                    chipRect.right,
                    chipRect.bottom,
                    chipStart,
                    chipEnd,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRoundRect(chipRect, dp(16f), dp(16f), chipPaint)

            chipTitlePaint.color = palette.secondaryText
            chipValuePaint.color = palette.primaryText
            canvas.drawText(title, chipRect.left + dp(12f), chipRect.top + dp(13f), chipTitlePaint)
            canvas.drawText(value, chipRect.left + dp(12f), chipRect.bottom - dp(9f), chipValuePaint)

            val rangeLabel = "${model.windowSizeDays} days"
            val badgeWidth = badgeTextPaint.measureText(rangeLabel) + dp(20f)
            val badgeRect =
                RectF(
                    headerRect.right - badgeWidth,
                    headerRect.top + dp(4f),
                    headerRect.right,
                    headerRect.top + dp(24f),
                )
            chipPaint.shader = null
            chipPaint.color = ColorUtils.blendARGB(palette.panelStart, palette.toneLight, 0.42f)
            canvas.drawRoundRect(badgeRect, dp(999f), dp(999f), chipPaint)
            badgeTextPaint.color = palette.secondaryText
            canvas.drawText(rangeLabel, badgeRect.centerX(), badgeRect.bottom - dp(7f), badgeTextPaint)
        }

        private fun drawAxisLabels(
            canvas: Canvas,
            panelRect: RectF,
            plotRect: RectF,
            model: TrendChartModel.Line,
            centers: List<Float>,
            palette: ChartPalette,
        ) {
            val labelIndexes = visibleLabelIndexes(model.entries.size, selectedIndex)
            val baseline = plotRect.bottom + dp(14f)
            val safeMinX = panelRect.left + dp(12f)
            val safeMaxX = panelRect.right - dp(12f)

            model.entries.forEachIndexed { index, entry ->
                if (index !in labelIndexes) return@forEachIndexed
                axisTextPaint.color = if (index == selectedIndex) palette.primaryText else palette.secondaryText
                val labelCenterX =
                    ChartAxisLabelLayout.clampedCenterX(
                        preferredCenterX = centers[index],
                        labelWidth = axisTextPaint.measureText(entry.axisLabel),
                        minX = safeMinX,
                        maxX = safeMaxX,
                    )
                canvas.drawText(entry.axisLabel, labelCenterX, baseline, axisTextPaint)
            }
        }

        private fun drawGuideLine(
            canvas: Canvas,
            x: Float,
            plotRect: RectF,
            palette: ChartPalette,
        ) {
            guidePaint.color = palette.guide
            canvas.drawLine(x, plotRect.top, x, plotRect.bottom, guidePaint)
        }

        private fun drawEmptyState(
            canvas: Canvas,
            panelRect: RectF,
            palette: ChartPalette,
        ) {
            chipValuePaint.color = palette.secondaryText
            val text = "No data"
            val baseline = panelRect.centerY() - (chipValuePaint.descent() + chipValuePaint.ascent()) / 2f
            canvas.drawText(text, panelRect.centerX() - chipValuePaint.measureText(text) / 2f, baseline, chipValuePaint)
        }

        private fun buildSmoothPath(points: List<PointF>): Path {
            linePath.reset()
            if (points.isEmpty()) {
                return linePath
            }
            linePath.moveTo(points.first().x, points.first().y)
            if (points.size == 1) {
                return linePath
            }

            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midpointX = (previous.x + current.x) / 2f
                val midpointY = (previous.y + current.y) / 2f
                linePath.quadTo(previous.x, previous.y, midpointX, midpointY)
            }

            val last = points.last()
            linePath.lineTo(last.x, last.y)
            return linePath
        }

        private fun updateSelection(x: Float) {
            val model = chartModel as? TrendChartModel.Line ?: return
            if (model.entries.isEmpty()) return

            val plotRect = buildPlotRect(buildPanelRect(), buildHeaderRect(buildPanelRect()))
            val centers = slotCenters(model.entries.size, plotRect)
            val closestIndex =
                centers.indices.minByOrNull { index ->
                    abs(centers[index] - x)
                } ?: return

            if (selectedIndex != closestIndex) {
                selectedIndex = closestIndex
                invalidate()
            }
        }

        private fun visibleLabelIndexes(
            count: Int,
            selected: Int,
        ): Set<Int> {
            if (count <= 0) return emptySet()
            val base =
                if (count <= 7) {
                    (0 until count).toSet()
                } else {
                    linkedSetOf(
                        0,
                        count / 4,
                        count / 2,
                        (count * 3) / 4,
                        count - 1,
                    )
                }
            return linkedSetOf<Int>().apply {
                addAll(base)
                add(selected.coerceIn(0, count - 1))
            }
        }

        private fun buildPanelRect(): RectF =
            RectF(
                paddingLeft + dp(2f),
                paddingTop + dp(2f),
                width - paddingRight - dp(2f),
                height - paddingBottom - dp(2f),
            )

        private fun buildHeaderRect(panelRect: RectF): RectF =
            RectF(
                panelRect.left + dp(12f),
                panelRect.top + dp(8f),
                panelRect.right - dp(12f),
                panelRect.top + dp(42f),
            )

        private fun buildPlotRect(
            panelRect: RectF,
            headerRect: RectF,
        ): RectF =
            RectF(
                panelRect.left + dp(12f),
                headerRect.bottom + dp(6f),
                panelRect.right - dp(12f),
                panelRect.bottom - dp(20f),
            )

        private fun slotCenters(
            count: Int,
            plotRect: RectF,
        ): List<Float> {
            if (count <= 0) return emptyList()
            if (count == 1) return listOf(plotRect.centerX())

            val slotWidth = plotRect.width() / (count - 1)
            return List(count) { index ->
                plotRect.left + slotWidth * index
            }
        }

        private fun mapY(
            value: Float,
            minValue: Float,
            maxValue: Float,
            top: Float,
            bottom: Float,
        ): Float {
            val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
            val normalized = (value - minValue) / range
            return bottom - (bottom - top) * normalized
        }

        private fun compactLabel(label: String): String =
            if (label.count { it == '-' } >= 2) {
                label.split("-").takeLast(2).joinToString("/")
            } else {
                label
            }

        private fun restartRevealAnimation() {
            revealAnimator?.cancel()
            if (chartModel !is TrendChartModel.Line || !isAttachedToWindow) {
                revealProgress = 1f
                return
            }

            revealProgress = 0f
            revealAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 240L
                    addUpdateListener { animator ->
                        revealProgress = animator.animatedValue as Float
                        postInvalidateOnAnimation()
                    }
                    start()
                }
        }

        private fun resolvePalette(tone: TrendChartTone): ChartPalette {
            val panelStart = themeColor(R.attr.vsColorSurfaceAlt)
            val panelEnd = ColorUtils.blendARGB(panelStart, themeColor(R.attr.vsColorPrimarySoft), 0.42f)
            val primaryStrong = themeColor(R.attr.vsColorPrimaryStrong)
            val primarySoft = themeColor(R.attr.vsColorPrimarySoft)
            val secondaryAccent = themeColor(R.attr.vsColorSecondaryAccent)
            val sleepAccent = themeColor(R.attr.vsColorWeekSignalSleep)
            val alert = themeColor(R.attr.vsColorAlert)
            val tonePalette =
                when (tone) {
                    TrendChartTone.SOFT ->
                        Triple(
                            ColorUtils.blendARGB(primarySoft, sleepAccent, 0.52f),
                            sleepAccent,
                            ColorUtils.blendARGB(sleepAccent, secondaryAccent, 0.3f),
                        )

                    TrendChartTone.CALM ->
                        Triple(
                            ColorUtils.blendARGB(primarySoft, secondaryAccent, 0.42f),
                            secondaryAccent,
                            ColorUtils.blendARGB(secondaryAccent, primaryStrong, 0.4f),
                        )

                    TrendChartTone.EMPHASIZED ->
                        Triple(
                            ColorUtils.blendARGB(primarySoft, alert, 0.35f),
                            ColorUtils.blendARGB(alert, secondaryAccent, 0.2f),
                            ColorUtils.blendARGB(alert, primaryStrong, 0.2f),
                        )
                }
            val toneLight = tonePalette.first
            val toneMid = tonePalette.second
            val toneDeep = tonePalette.third

            return ChartPalette(
                panelStart = panelStart,
                panelEnd = panelEnd,
                panelStroke = themeColor(com.google.android.material.R.attr.colorOutline),
                grid = ColorUtils.blendARGB(panelStart, toneMid, 0.22f),
                guide = ColorUtils.setAlphaComponent(toneDeep, 72),
                toneLight = toneLight,
                toneMid = toneMid,
                toneDeep = toneDeep,
                line = ColorUtils.blendARGB(toneDeep, toneMid, 0.28f),
                primaryText = themeColor(android.R.attr.textColorPrimary),
                secondaryText = themeColor(android.R.attr.textColorSecondary),
                pointShell = themeColor(com.google.android.material.R.attr.colorSurface),
            )
        }

        private fun themeColor(attrRes: Int): Int = ThemeAttrColorResolver.color(context, attrRes)

        private fun dp(value: Float): Float = value * resources.displayMetrics.density

        private fun sp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                resources.displayMetrics,
            )

        private data class ChartPalette(
            val panelStart: Int,
            val panelEnd: Int,
            val panelStroke: Int,
            val grid: Int,
            val guide: Int,
            val toneLight: Int,
            val toneMid: Int,
            val toneDeep: Int,
            val line: Int,
            val primaryText: Int,
            val secondaryText: Int,
            val pointShell: Int,
        )
    }
