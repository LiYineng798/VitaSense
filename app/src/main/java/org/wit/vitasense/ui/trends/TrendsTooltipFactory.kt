package org.wit.vitasense.ui.trends

import java.util.Locale
import kotlin.math.abs

data class TrendTooltipModel(
    val title: String,
    val primaryText: String,
    val secondaryText: String,
    val tertiaryText: String? = null,
)

object TrendsTooltipFactory {
    fun monthChart(point: MonthlyTrendPointModel): TrendTooltipModel =
        TrendTooltipModel(
            title = point.dayLabel,
            primaryText = String.format(Locale.US, "HRV %.1f ms", point.rawHrv),
            secondaryText = String.format(Locale.US, "Sleep %.1f h | Avg Heart Rate %.0f bpm", point.sleepHours, point.averageHeartRate),
            tertiaryText = if (point.anomalyCount > 0) "Anomaly flags ${point.anomalyCount}" else "No anomaly flag",
        )

    fun heatmap(cell: RecoveryHeatCellModel): TrendTooltipModel =
        TrendTooltipModel(
            title = cell.dayLabel,
            primaryText = "Recovery ${(cell.intensity.coerceIn(0f, 1f) * 100f).toInt()}%",
            secondaryText = if (cell.hasAnomaly) "Status: anomaly present" else "Status: steady recovery",
        )

    fun nearestIndex(
        centers: List<Float>,
        touchX: Float,
    ): Int =
        centers.indices.minByOrNull { index ->
            abs(centers[index] - touchX)
        } ?: 0
}
