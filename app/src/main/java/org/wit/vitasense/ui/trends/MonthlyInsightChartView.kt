package org.wit.vitasense.ui.trends

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.wit.vitasense.R
import org.wit.vitasense.ui.common.chart.ChartScaleCalculator
import kotlin.math.max

class MonthlyInsightChartView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        var model: MonthlyInsightModel? = null
            set(value) {
                field = value
                selectedIndex = selectedIndex?.coerceAtMost((value?.trendPoints?.lastIndex ?: 0).coerceAtLeast(0))
                syncPulseAnimator()
                invalidate()
            }

        private val scaleCalculator = ChartScaleCalculator()

        private var selectedIndex: Int? = null
        private var pulseProgress: Float = 0f
        private var pulseAnimator: ValueAnimator? = null

        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
            }
        private val sleepBarPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val smoothLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = dp(2.6f)
            }
        private val heartLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = dp(1.9f)
                pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
            }
        private val rawPointShellPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val rawPointCorePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val markerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val markerHaloPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val guidePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.2f)
                pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
            }
        private val tooltipPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val tooltipStrokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
            }
        private val tooltipTitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(11f)
                isFakeBoldText = true
            }
        private val tooltipPrimaryPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(13f)
                isFakeBoldText = true
            }
        private val tooltipSecondaryPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(11f)
            }
        private val tooltipTertiaryPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp(11f)
            }

        private val smoothPath = Path()
        private val heartPath = Path()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            syncPulseAnimator()
        }

        override fun onDetachedFromWindow() {
            pulseAnimator?.cancel()
            pulseAnimator = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val trendPoints = model?.trendPoints.orEmpty()
            if (trendPoints.isEmpty()) return

            val palette = resolvePalette()
            val plotRect = buildPlotRect()
            val markerY = height - paddingBottom - dp(16f)
            val centers = slotCenters(trendPoints.size, plotRect)

            gridPaint.color = ColorUtils.setAlphaComponent(palette.grid, 140)
            repeat(4) { index ->
                val y = plotRect.top + (plotRect.height() / 3f) * index
                canvas.drawLine(plotRect.left, y, plotRect.right, y, gridPaint)
            }

            val sleepRange = scaleCalculator.paddedRange(trendPoints.map { it.sleepHours }, floorAtZero = true)
            val hrvRange =
                scaleCalculator.paddedRange(
                    trendPoints.flatMap { listOf(it.rawHrv, it.smoothedHrv) },
                    floorAtZero = false,
                )
            val heartRange = scaleCalculator.paddedRange(trendPoints.map { it.averageHeartRate }, floorAtZero = false)

            val sleepRects =
                buildSleepRects(
                    plotRect = plotRect,
                    points = trendPoints,
                    centers = centers,
                    range = sleepRange,
                )
            val rawPoints =
                buildMappedPoints(
                    points = trendPoints.map { it.rawHrv },
                    centers = centers,
                    range = hrvRange,
                    top = plotRect.top,
                    bottom = plotRect.top + plotRect.height() * 0.56f,
                )
            val smoothedPoints =
                buildMappedPoints(
                    points = trendPoints.map { it.smoothedHrv },
                    centers = centers,
                    range = hrvRange,
                    top = plotRect.top,
                    bottom = plotRect.top + plotRect.height() * 0.56f,
                )
            val heartPoints =
                buildMappedPoints(
                    points = trendPoints.map { it.averageHeartRate },
                    centers = centers,
                    range = heartRange,
                    top = plotRect.top + plotRect.height() * 0.14f,
                    bottom = plotRect.top + plotRect.height() * 0.62f,
                )

            drawSleepBars(canvas, sleepRects, palette)
            drawHrvLayer(canvas, rawPoints, smoothedPoints, palette)
            drawHeartRateLayer(canvas, heartPoints, palette)
            drawAnomalyMarkers(canvas, trendPoints, centers, markerY, palette)

            selectedIndex?.takeIf { it in trendPoints.indices }?.let { index ->
                val anchor = rawPoints.getOrNull(index) ?: heartPoints.getOrNull(index)
                if (anchor != null) {
                    guidePaint.color = palette.guide
                    canvas.drawLine(anchor.x, plotRect.top, anchor.x, markerY - dp(8f), guidePaint)
                    drawTooltip(
                        canvas = canvas,
                        tooltip = TrendsTooltipFactory.monthChart(trendPoints[index]),
                        anchor = anchor,
                        palette = palette,
                        bounds = RectF(paddingLeft.toFloat(), paddingTop.toFloat(), width - paddingRight.toFloat(), height - paddingBottom.toFloat()),
                    )
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val trendPoints = model?.trendPoints.orEmpty()
            if (trendPoints.isEmpty()) return super.onTouchEvent(event)

            val plotRect = buildPlotRect()
            val centers = slotCenters(trendPoints.size, plotRect)

            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    selectedIndex = TrendsTooltipFactory.nearestIndex(centers, event.x.coerceIn(plotRect.left, plotRect.right))
                    invalidate()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> super.onTouchEvent(event)
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun drawSleepBars(
            canvas: Canvas,
            rects: List<RectF>,
            palette: ChartPalette,
        ) {
            sleepBarPaint.color = palette.sleepBar
            rects.forEach { rect ->
                canvas.drawRoundRect(rect, dp(6f), dp(6f), sleepBarPaint)
            }
        }

        private fun drawHrvLayer(
            canvas: Canvas,
            rawPoints: List<PointF>,
            smoothedPoints: List<PointF>,
            palette: ChartPalette,
        ) {
            smoothLinePaint.color = palette.smoothLine
            canvas.drawPath(buildSmoothPath(smoothedPoints, smoothPath), smoothLinePaint)

            rawPointShellPaint.color = palette.pointShell
            rawPointCorePaint.color = palette.rawPoint
            rawPoints.forEachIndexed { index, point ->
                val selected = selectedIndex == index
                canvas.drawCircle(point.x, point.y, dp(if (selected) 4.7f else 3.6f), rawPointShellPaint)
                canvas.drawCircle(point.x, point.y, dp(if (selected) 2.8f else 2.1f), rawPointCorePaint)
            }
        }

        private fun drawHeartRateLayer(
            canvas: Canvas,
            heartPoints: List<PointF>,
            palette: ChartPalette,
        ) {
            heartLinePaint.color = palette.heartLine
            canvas.drawPath(buildSmoothPath(heartPoints, heartPath), heartLinePaint)
        }

        private fun drawAnomalyMarkers(
            canvas: Canvas,
            points: List<MonthlyTrendPointModel>,
            centers: List<Float>,
            markerY: Float,
            palette: ChartPalette,
        ) {
            points.forEachIndexed { index, point ->
                if (point.anomalyCount <= 0) return@forEachIndexed

                val centerX = centers[index]
                val haloRadius = dp(4.8f + 3.2f * pulseProgress)
                markerHaloPaint.color = ColorUtils.setAlphaComponent(palette.marker, (92 * (1f - pulseProgress)).toInt())
                canvas.drawCircle(centerX, markerY, haloRadius, markerHaloPaint)

                markerPaint.color = palette.marker
                canvas.drawCircle(centerX, markerY, dp(3.2f), markerPaint)
            }
        }

        private fun drawTooltip(
            canvas: Canvas,
            tooltip: TrendTooltipModel,
            anchor: PointF,
            palette: ChartPalette,
            bounds: RectF,
        ) {
            tooltipTitlePaint.color = palette.tooltipTextPrimary
            tooltipPrimaryPaint.color = palette.tooltipTextPrimary
            tooltipSecondaryPaint.color = palette.tooltipTextSecondary
            tooltipTertiaryPaint.color = palette.tooltipTextSecondary

            val maxTextWidth =
                maxOf(
                    tooltipTitlePaint.measureText(tooltip.title),
                    tooltipPrimaryPaint.measureText(tooltip.primaryText),
                    tooltipSecondaryPaint.measureText(tooltip.secondaryText),
                    tooltip.tertiaryText?.let { tooltipTertiaryPaint.measureText(it) } ?: 0f,
                )

            val cardWidth = (maxTextWidth + dp(28f)).coerceAtMost(bounds.width() - dp(20f))
            val lineCount = if (tooltip.tertiaryText == null) 3 else 4
            val cardHeight = dp(if (lineCount == 4) 82f else 68f)
            val desiredLeft = anchor.x - cardWidth / 2f
            val left = desiredLeft.coerceIn(bounds.left + dp(8f), bounds.right - cardWidth - dp(8f))
            val top =
                (anchor.y - cardHeight - dp(16f)).takeIf { it >= bounds.top + dp(8f) }
                    ?: (anchor.y + dp(14f)).coerceAtMost(bounds.bottom - cardHeight - dp(8f))
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)

            tooltipPaint.color = palette.tooltipBackground
            tooltipStrokePaint.color = palette.tooltipStroke
            canvas.drawRoundRect(rect, dp(16f), dp(16f), tooltipPaint)
            canvas.drawRoundRect(rect, dp(16f), dp(16f), tooltipStrokePaint)

            val textLeft = rect.left + dp(14f)
            var baseline = rect.top + dp(18f)
            canvas.drawText(tooltip.title, textLeft, baseline, tooltipTitlePaint)
            baseline += dp(18f)
            canvas.drawText(tooltip.primaryText, textLeft, baseline, tooltipPrimaryPaint)
            baseline += dp(18f)
            canvas.drawText(tooltip.secondaryText, textLeft, baseline, tooltipSecondaryPaint)
            tooltip.tertiaryText?.let {
                baseline += dp(16f)
                canvas.drawText(it, textLeft, baseline, tooltipTertiaryPaint)
            }
        }

        private fun buildSleepRects(
            plotRect: RectF,
            points: List<MonthlyTrendPointModel>,
            centers: List<Float>,
            range: ClosedFloatingPointRange<Float>,
        ): List<RectF> {
            val barWidth = (plotRect.width() / max(points.size, 1) * 0.54f).coerceAtMost(dp(10f))
            val barTop = plotRect.top + plotRect.height() * 0.48f
            return points.mapIndexed { index, point ->
                val x = centers[index]
                val y =
                    scaleCalculator.mapValue(
                        point.sleepHours,
                        range.start,
                        range.endInclusive,
                        barTop,
                        plotRect.bottom,
                    )
                RectF(x - barWidth / 2f, y, x + barWidth / 2f, plotRect.bottom)
            }
        }

        private fun buildMappedPoints(
            points: List<Float>,
            centers: List<Float>,
            range: ClosedFloatingPointRange<Float>,
            top: Float,
            bottom: Float,
        ): List<PointF> =
            points.mapIndexed { index, value ->
                PointF(
                    centers[index],
                    scaleCalculator.mapValue(
                        value,
                        range.start,
                        range.endInclusive,
                        top,
                        bottom,
                    ),
                )
            }

        private fun buildSmoothPath(
            points: List<PointF>,
            path: Path,
        ): Path {
            path.reset()
            if (points.isEmpty()) return path
            path.moveTo(points.first().x, points.first().y)
            if (points.size == 1) return path
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midpointX = (previous.x + current.x) / 2f
                val midpointY = (previous.y + current.y) / 2f
                path.quadTo(previous.x, previous.y, midpointX, midpointY)
            }
            path.lineTo(points.last().x, points.last().y)
            return path
        }

        private fun slotCenters(
            count: Int,
            plotRect: RectF,
        ): List<Float> {
            if (count <= 1) return listOf(plotRect.centerX())
            val slotWidth = plotRect.width() / max(1, count - 1)
            return List(count) { index -> plotRect.left + slotWidth * index }
        }

        private fun buildPlotRect(): RectF {
            val left = paddingLeft + dp(8f)
            val right = width - paddingRight - dp(8f)
            val top = paddingTop + dp(10f)
            val markerY = height - paddingBottom - dp(16f)
            val bottom = markerY - dp(16f)
            return RectF(left, top, right, bottom)
        }

        private fun syncPulseAnimator() {
            val hasAnomaly = model?.trendPoints?.any { it.anomalyCount > 0 } == true
            if (!isAttachedToWindow || !hasAnomaly) {
                pulseAnimator?.cancel()
                pulseAnimator = null
                pulseProgress = 0f
                return
            }
            if (pulseAnimator != null) return

            pulseAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1200L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { animator ->
                        pulseProgress = animator.animatedValue as Float
                        postInvalidateOnAnimation()
                    }
                    start()
                }
        }

        private fun resolvePalette(): ChartPalette {
            val isNight =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            return ChartPalette(
                grid = color(if (isNight) R.color.vs_dark_border_soft else R.color.vs_border_soft),
                sleepBar =
                    ColorUtils.blendARGB(
                        color(if (isNight) R.color.vs_dark_primary_300 else R.color.vs_primary_100),
                        color(if (isNight) R.color.vs_dark_primary_500 else R.color.vs_primary_300),
                        0.38f,
                    ),
                smoothLine = color(if (isNight) R.color.vs_dark_primary_900 else R.color.vs_primary_900),
                heartLine = color(if (isNight) R.color.vs_dark_primary_500 else R.color.vs_primary_500),
                rawPoint = color(if (isNight) R.color.vs_dark_primary_700 else R.color.vs_primary_700),
                marker = color(if (isNight) R.color.vs_alert_red_dark else R.color.vs_alert_red),
                pointShell = color(if (isNight) R.color.vs_dark_surface else R.color.white),
                guide = color(if (isNight) R.color.vs_dark_primary_700 else R.color.vs_primary_700),
                tooltipBackground = color(if (isNight) R.color.vs_dark_surface else R.color.vs_surface),
                tooltipStroke = color(if (isNight) R.color.vs_dark_border_soft else R.color.vs_border_soft),
                tooltipTextPrimary = color(if (isNight) R.color.vs_dark_text_primary else R.color.vs_text_primary),
                tooltipTextSecondary = color(if (isNight) R.color.vs_dark_text_secondary else R.color.vs_text_secondary),
            )
        }

        private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                resources.displayMetrics,
            )

        private fun sp(value: Float): Float =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                resources.displayMetrics,
            )

        private data class ChartPalette(
            val grid: Int,
            val sleepBar: Int,
            val smoothLine: Int,
            val heartLine: Int,
            val rawPoint: Int,
            val marker: Int,
            val pointShell: Int,
            val guide: Int,
            val tooltipBackground: Int,
            val tooltipStroke: Int,
            val tooltipTextPrimary: Int,
            val tooltipTextSecondary: Int,
        )
    }
