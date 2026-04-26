package org.wit.vitasense.ui.trends

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil
import kotlin.math.max
import org.wit.vitasense.R

class RecoveryHeatmapView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        var cells: List<RecoveryHeatCellModel> = emptyList()
            set(value) {
                field = value
                selectedIndex = selectedIndex?.takeIf { it in value.indices }
                requestLayout()
                invalidate()
            }

        private val columns = 6
        private val gap = dp(6f)

        private var selectedIndex: Int? = null

        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
            }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = sp(10f)
            }
        private val dotPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
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

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val cellSize = calculateCellSize(width)
            val rows = max(1, ceil(cells.size.coerceAtLeast(1) / columns.toFloat()).toInt())
            val desiredHeight = paddingTop + paddingBottom + (rows * cellSize) + ((rows - 1) * gap)
            val resolvedHeight = resolveSize(desiredHeight.toInt(), heightMeasureSpec)
            setMeasuredDimension(width, resolvedHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (cells.isEmpty()) return

            val palette = resolvePalette()
            val cellSize = calculateCellSize(width)

            cells.forEachIndexed { index, cell ->
                val rect = cellRect(index, cellSize)
                val selected = selectedIndex == index

                fillPaint.color = blendColor(palette.low, palette.high, cell.intensity.coerceIn(0f, 1f))
                borderPaint.color = if (selected) palette.selectedBorder else palette.border
                borderPaint.strokeWidth = if (selected) dp(1.6f) else dp(1f)
                canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)
                canvas.drawRoundRect(rect, dp(10f), dp(10f), borderPaint)

                textPaint.color = if (cell.intensity > 0.56f) palette.textOnDark else palette.textOnLight
                val dayLabel = cell.dayLabel.substringAfter('/').takeLast(2)
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(dayLabel, rect.centerX(), baseline, textPaint)

                if (cell.hasAnomaly) {
                    dotPaint.color = palette.dot
                    canvas.drawCircle(rect.right - dp(7f), rect.top + dp(7f), dp(2.8f), dotPaint)
                }
            }

            selectedIndex?.takeIf { it in cells.indices }?.let { index ->
                drawTooltip(
                    canvas = canvas,
                    tooltip = TrendsTooltipFactory.heatmap(cells[index]),
                    anchor = cellRect(index, cellSize),
                    palette = palette,
                    bounds = RectF(paddingLeft.toFloat(), paddingTop.toFloat(), width - paddingRight.toFloat(), height - paddingBottom.toFloat()),
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (cells.isEmpty()) return super.onTouchEvent(event)

            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    selectedIndex = findCellIndex(event.x, event.y)
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

        private fun drawTooltip(
            canvas: Canvas,
            tooltip: TrendTooltipModel,
            anchor: RectF,
            palette: HeatmapPalette,
            bounds: RectF,
        ) {
            tooltipTitlePaint.color = palette.tooltipTextPrimary
            tooltipPrimaryPaint.color = palette.tooltipTextPrimary
            tooltipSecondaryPaint.color = palette.tooltipTextSecondary

            val maxTextWidth =
                maxOf(
                    tooltipTitlePaint.measureText(tooltip.title),
                    tooltipPrimaryPaint.measureText(tooltip.primaryText),
                    tooltipSecondaryPaint.measureText(tooltip.secondaryText),
                )
            val cardWidth = (maxTextWidth + dp(28f)).coerceAtMost(bounds.width() - dp(20f))
            val cardHeight = dp(68f)
            val desiredLeft = anchor.centerX() - cardWidth / 2f
            val left = desiredLeft.coerceIn(bounds.left + dp(8f), bounds.right - cardWidth - dp(8f))
            val top =
                (anchor.top - cardHeight - dp(10f)).takeIf { it >= bounds.top + dp(8f) }
                    ?: (anchor.bottom + dp(10f)).coerceAtMost(bounds.bottom - cardHeight - dp(8f))
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
        }

        private fun calculateCellSize(measuredWidth: Int): Float {
            val availableWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
            return if (availableWidth == 0) {
                dp(24f)
            } else {
                ((availableWidth - gap * (columns - 1)) / columns.toFloat()).coerceAtLeast(dp(22f))
            }
        }

        private fun cellRect(
            index: Int,
            cellSize: Float,
        ): RectF {
            val row = index / columns
            val column = index % columns
            val left = paddingLeft + column * (cellSize + gap)
            val top = paddingTop + row * (cellSize + gap)
            return RectF(left, top, left + cellSize, top + cellSize)
        }

        private fun findCellIndex(
            x: Float,
            y: Float,
        ): Int? {
            val cellSize = calculateCellSize(width)
            return cells.indices.firstOrNull { index ->
                cellRect(index, cellSize).contains(x, y)
            }
        }

        private fun blendColor(
            startColor: Int,
            endColor: Int,
            fraction: Float,
        ): Int = ColorUtils.blendARGB(startColor, endColor, fraction)

        private fun resolvePalette(): HeatmapPalette {
            val isNight =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            return HeatmapPalette(
                low = color(if (isNight) R.color.vs_dark_primary_200 else R.color.vs_primary_100),
                high = color(if (isNight) R.color.vs_dark_primary_700 else R.color.vs_primary_700),
                border = color(if (isNight) R.color.vs_dark_border_soft else R.color.vs_border_soft),
                selectedBorder = color(if (isNight) R.color.vs_dark_primary_900 else R.color.vs_primary_900),
                dot = color(if (isNight) R.color.vs_alert_red_dark else R.color.vs_alert_red),
                textOnLight = color(if (isNight) R.color.vs_dark_text_primary else R.color.vs_text_primary),
                textOnDark = color(R.color.white),
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

        private data class HeatmapPalette(
            val low: Int,
            val high: Int,
            val border: Int,
            val selectedBorder: Int,
            val dot: Int,
            val textOnLight: Int,
            val textOnDark: Int,
            val tooltipBackground: Int,
            val tooltipStroke: Int,
            val tooltipTextPrimary: Int,
            val tooltipTextSecondary: Int,
        )
    }
