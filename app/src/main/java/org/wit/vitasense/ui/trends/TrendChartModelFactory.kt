package org.wit.vitasense.ui.trends

import java.util.Locale
import kotlin.math.max
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.chart.LineEntry
import org.wit.vitasense.ui.common.chart.TrendChartModel
import org.wit.vitasense.ui.common.chart.TrendChartTone

enum class TrendChartMetric {
    SLEEP,
    HRV,
    ANOMALY,
    HEART_RATE,
}

object TrendChartModelFactory {
    fun build(
        items: List<TrendSummaryItem>,
        range: TimeRange,
        metric: TrendChartMetric,
    ): TrendChartModel {
        val visibleItems = items.sortedBy { it.date }.takeLast(range.days)
        if (visibleItems.isEmpty()) {
            return TrendChartModel.Empty
        }

        val entries =
            visibleItems.mapNotNull { item ->
                item.metricValue(metric)?.let { value ->
                    LineEntry(
                        axisLabel = item.axisLabel(),
                        detailLabel = item.date,
                        value = value,
                        valueText = formatValue(metric, value),
                        highlighted = item.anomalyFlags.isNotEmpty(),
                    )
                }
            }

        if (entries.isEmpty()) {
            return TrendChartModel.Empty
        }

        val (minValue, maxValue) =
            paddedRange(
                values = entries.map { it.value },
                floorAtZero = metric == TrendChartMetric.ANOMALY,
            )

        return TrendChartModel.Line(
            tone = toneFor(metric),
            minValue = minValue,
            maxValue = maxValue,
            selectionIndex = entries.lastIndex,
            windowSizeDays = range.days,
            entries = entries,
        )
    }

    private fun TrendSummaryItem.metricValue(metric: TrendChartMetric): Float? =
        when (metric) {
            TrendChartMetric.SLEEP -> sleepMinutes?.div(60f)
            TrendChartMetric.HRV -> rmssd?.toFloat()
            TrendChartMetric.ANOMALY -> anomalyScore()
            TrendChartMetric.HEART_RATE -> avgHeartRate?.toFloat()
        }

    private fun TrendSummaryItem.anomalyScore(): Float =
        when {
            anomalyFlags.contains(AnomalyFlag.PERSISTENT) -> 3f
            anomalyFlags.contains(AnomalyFlag.CONTINUOUS) -> 2f
            anomalyFlags.contains(AnomalyFlag.SINGLE_DAY) -> 1f
            else -> 0f
        }

    private fun TrendSummaryItem.axisLabel(): String =
        date.split("-")
            .takeLast(2)
            .joinToString("/")

    private fun toneFor(metric: TrendChartMetric): TrendChartTone =
        when (metric) {
            TrendChartMetric.SLEEP -> TrendChartTone.SOFT
            TrendChartMetric.HRV -> TrendChartTone.CALM
            TrendChartMetric.ANOMALY -> TrendChartTone.EMPHASIZED
            TrendChartMetric.HEART_RATE -> TrendChartTone.CALM
        }

    private fun formatValue(
        metric: TrendChartMetric,
        value: Float,
    ): String =
        when (metric) {
            TrendChartMetric.SLEEP -> "${formatOneDecimal(value)} h"
            TrendChartMetric.HRV -> "${formatOneDecimal(value)} ms"
            TrendChartMetric.HEART_RATE -> "${formatOneDecimal(value)} bpm"
            TrendChartMetric.ANOMALY -> anomalyLabel(value)
        }

    private fun anomalyLabel(value: Float): String =
        when (value) {
            3f -> "Persistent Anomaly"
            2f -> "Continuous Anomaly"
            1f -> "Single-Day Anomaly"
            else -> "Stable"
        }

    private fun paddedRange(
        values: List<Float>,
        floorAtZero: Boolean,
    ): Pair<Float, Float> {
        if (values.isEmpty()) {
            return 0f to 1f
        }

        val minValue = values.minOrNull() ?: 0f
        val maxValue = values.maxOrNull() ?: 0f
        val span = maxValue - minValue
        val padding = if (span > 0f) span * 0.18f else max(1f, maxValue * 0.18f)
        val paddedMin = if (floorAtZero) max(0f, minValue - padding) else minValue - padding
        val paddedMax = maxValue + padding
        return paddedMin to max(paddedMax, paddedMin + 1f)
    }

    private fun formatOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)
}
