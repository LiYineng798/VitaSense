package org.wit.vitasense.ui.trends

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object MonthlyInsightFactory {
    private val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)

    fun buildOverview(
        snapshots: List<TrendDaySnapshot>,
        windowSizeDays: Int = snapshots.size,
    ): MonthlyInsightModel {
        val sorted = snapshots.sortedBy { it.date }
        val smoothedHrv = movingAverage(sorted.map { it.hrv }, window = 7)
        val weeklyAggregates = buildWeeklyAggregates(sorted)
        val firstWeek = sorted.take(7)
        val lastWeek = sorted.takeLast(minOf(7, sorted.size))
        val firstHalfAlerts = sorted.take(sorted.size / 2).sumOf { it.anomalyCount }
        val lastHalfAlerts = sorted.drop(sorted.size / 2).sumOf { it.anomalyCount }

        return MonthlyInsightModel(
            trendPoints =
                sorted.mapIndexed { index, snapshot ->
                    MonthlyTrendPointModel(
                        dayLabel = snapshot.date.asLocalDate().format(dateFormatter),
                        rawHrv = snapshot.hrv,
                        smoothedHrv = smoothedHrv[index],
                        averageHeartRate = snapshot.averageHeartRate,
                        sleepHours = snapshot.sleepHours,
                        anomalyCount = snapshot.anomalyCount,
                    )
                },
            weeklyAggregates = weeklyAggregates,
            heatmapCells =
                sorted.map { snapshot ->
                    RecoveryHeatCellModel(
                        dayLabel = snapshot.date.asLocalDate().format(dateFormatter),
                        intensity = snapshot.recoveryScore.coerceIn(0f, 1f),
                        hasAnomaly = snapshot.anomalyCount > 0,
                    )
                },
            insightCards =
                listOf(
                    buildInsightCard(
                        title = "HRV Trend",
                        current = lastWeek.map { it.hrv }.average().toFloat(),
                        baseline = firstWeek.map { it.hrv }.average().toFloat(),
                        valueText = String.format(Locale.US, "%.1f ms", sorted.map { it.hrv }.average()),
                    ),
                    buildInsightCard(
                        title = "Sleep Trend",
                        current = lastWeek.map { it.sleepHours }.average().toFloat(),
                        baseline = firstWeek.map { it.sleepHours }.average().toFloat(),
                        valueText = String.format(Locale.US, "%.1f h", sorted.map { it.sleepHours }.average()),
                    ),
                    buildInsightCard(
                        title = "Alerts",
                        current = -lastHalfAlerts.toFloat(),
                        baseline = -firstHalfAlerts.toFloat(),
                        valueText = "$lastHalfAlerts alerts",
                    ),
                ),
            windowSizeDays = windowSizeDays,
        )
    }

    private fun buildWeeklyAggregates(snapshots: List<TrendDaySnapshot>): List<WeeklyAggregateModel> =
        snapshots.chunked(7).map { group ->
            WeeklyAggregateModel(
                label = group.rangeLabel(),
                averageSleepHours = group.map { it.sleepHours }.average().toFloat(),
                averageHrv = group.map { it.hrv }.average().toFloat(),
                averageHeartRate = group.map { it.averageHeartRate }.average().toFloat(),
                anomalyCount = group.sumOf { it.anomalyCount },
            )
        }

    private fun buildInsightCard(
        title: String,
        current: Float,
        baseline: Float,
        valueText: String,
    ): MonthlyInsightCardModel {
        val delta = percentageDelta(current, baseline)
        return MonthlyInsightCardModel(
            title = title,
            valueText = valueText,
            deltaText = String.format(Locale.US, "%+.0f%%", delta),
            trendDirection = trendFor(delta),
        )
    }

    private fun percentageDelta(
        current: Float,
        baseline: Float,
    ): Float {
        if (baseline == 0f) {
            return if (current == 0f) 0f else 100f
        }
        return ((current - baseline) / abs(baseline)) * 100f
    }

    private fun trendFor(delta: Float): TrendDirection =
        when {
            abs(delta) < 2f -> TrendDirection.STABLE
            delta > 0f -> TrendDirection.UP
            else -> TrendDirection.DOWN
        }

    private fun movingAverage(
        values: List<Float>,
        window: Int,
    ): List<Float> =
        values.indices.map { index ->
            val start = (index - window + 1).coerceAtLeast(0)
            values.subList(start, index + 1).average().toFloat()
        }

    private fun List<TrendDaySnapshot>.rangeLabel(): String {
        val first = firstOrNull()?.date?.asLocalDate()?.format(dateFormatter) ?: ""
        val last = lastOrNull()?.date?.asLocalDate()?.format(dateFormatter) ?: ""
        return if (first == last) first else "$first-$last"
    }

    private fun String.asLocalDate(): LocalDate = LocalDate.parse(this, inputFormatter)
}
