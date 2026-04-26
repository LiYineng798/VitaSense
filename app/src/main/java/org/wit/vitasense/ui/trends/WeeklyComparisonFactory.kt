package org.wit.vitasense.ui.trends

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object WeeklyComparisonFactory {
    private val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)

    fun buildOverview(snapshots: List<TrendDaySnapshot>): WeeklyOverviewModel {
        val sorted = snapshots.sortedBy { it.date }
        val baselineHrv = sorted.map { it.hrv }.average().toFloat()

        return WeeklyOverviewModel(
            cards =
                sorted.map { snapshot ->
                    val delta = snapshot.hrv - baselineHrv
                    WeeklyDetailCardModel(
                        id = snapshot.date,
                        dayLabel = snapshot.date.asLocalDate().format(dayFormatter).uppercase(Locale.US),
                        dateLabel = snapshot.date.asLocalDate().format(dateFormatter),
                        sleepHoursText = snapshot.sleepHours.asHourText(),
                        sleepScore = snapshot.sleepScore,
                        hrvText = snapshot.hrv.asMetricText("ms"),
                        hrvDeltaText = delta.asDeltaText("vs base"),
                        hrvTrend = trendFor(delta, threshold = 1.5f),
                        heartRateText = snapshot.averageHeartRate.asMetricText("bpm"),
                        restingHeartRateText = snapshot.restingHeartRate.asMetricText("bpm"),
                        hasAnomaly = snapshot.anomalyCount > 0,
                        anomalyLabel = snapshot.anomalyLabel ?: "Stable recovery",
                        recoveryScore = snapshot.recoveryScore,
                        summaryText = snapshot.summaryText,
                    )
                },
            trendSeries =
                listOf(
                    buildSeries(
                        title = "HRV",
                        values = sorted.map { it.hrv },
                        unit = "ms",
                    ),
                    buildSeries(
                        title = "Heart Rate",
                        values = sorted.map { it.averageHeartRate },
                        unit = "bpm",
                    ),
                    buildSeries(
                        title = "Sleep",
                        values = sorted.map { it.sleepHours },
                        unit = "h",
                        decimal = true,
                        threshold = 0.15f,
                    ),
                ),
        )
    }

    private fun buildSeries(
        title: String,
        values: List<Float>,
        unit: String,
        decimal: Boolean = false,
        threshold: Float = 1f,
    ): MiniTrendSeriesModel {
        val latestValue = values.lastOrNull() ?: 0f
        val delta = latestValue - (values.firstOrNull() ?: latestValue)
        return MiniTrendSeriesModel(
            title = title,
            values = values,
            latestValueText =
                if (decimal) {
                    latestValue.asHourText()
                } else {
                    latestValue.asMetricText(unit)
                },
            trendDirection = trendFor(delta, threshold),
        )
    }

    private fun trendFor(
        delta: Float,
        threshold: Float,
    ): TrendDirection =
        when {
            abs(delta) < threshold -> TrendDirection.STABLE
            delta > 0f -> TrendDirection.UP
            else -> TrendDirection.DOWN
        }

    private fun Float.asMetricText(unit: String): String = "${roundToInt()} $unit"

    private fun Float.asHourText(): String = String.format(Locale.US, "%.1f h", this)

    private fun Float.asDeltaText(suffix: String): String {
        val rounded = roundToInt()
        val prefix = if (rounded > 0) "+" else ""
        return "$prefix$rounded $suffix"
    }

    private fun String.asLocalDate(): LocalDate = LocalDate.parse(this, inputFormatter)
}
