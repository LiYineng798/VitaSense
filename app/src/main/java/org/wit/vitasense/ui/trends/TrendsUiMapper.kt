package org.wit.vitasense.ui.trends

import kotlin.math.abs
import kotlin.math.roundToInt
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange

object TrendsUiMapper {
    fun buildState(
        items: List<TrendSummaryItem>,
        range: TimeRange,
    ): TrendsScreenState {
        val visibleItems = items.sortedBy { it.date }.takeLast(range.days)
        if (visibleItems.isEmpty()) {
            return TrendsScreenState(selectedRange = range)
        }

        val snapshots = visibleItems.map { it.toDaySnapshot() }

        return TrendsScreenState(
            selectedRange = range,
            latestSelectedDate = visibleItems.last().date,
            sleepSummary = buildSleepSummary(visibleItems),
            hrvSummary = buildHrvSummary(visibleItems),
            anomalySummary = buildAnomalySummary(visibleItems),
            heartRateSummary = buildHeartRateSummary(visibleItems),
            sleepChartModel = TrendChartModelFactory.build(visibleItems, range, TrendChartMetric.SLEEP),
            hrvChartModel = TrendChartModelFactory.build(visibleItems, range, TrendChartMetric.HRV),
            anomalyChartModel = TrendChartModelFactory.build(visibleItems, range, TrendChartMetric.ANOMALY),
            heartRateChartModel = TrendChartModelFactory.build(visibleItems, range, TrendChartMetric.HEART_RATE),
            weekOverview =
                if (range == TimeRange.DAYS_7) {
                    WeeklyComparisonFactory.buildOverview(snapshots)
                } else {
                    null
                },
            monthOverview =
                if (range == TimeRange.DAYS_30) {
                    MonthlyInsightFactory.buildOverview(snapshots, range.days)
                } else {
                    null
                },
            empty = false,
        )
    }

    private fun TrendSummaryItem.toDaySnapshot(): TrendDaySnapshot {
        val sleepHours = (sleepMinutes ?: 0) / 60f
        val averageHeartRateValue = avgHeartRate?.toFloat() ?: baselineAvgHeartRate?.toFloat() ?: 0f
        val restingHeartRateValue = restingHeartRate?.toFloat() ?: averageHeartRateValue
        val hrvValue = rmssd?.toFloat() ?: baselineRmssd?.toFloat() ?: 0f

        return TrendDaySnapshot(
            date = date,
            sleepHours = sleepHours,
            sleepScore = buildSleepScore(sleepHours),
            averageHeartRate = averageHeartRateValue,
            restingHeartRate = restingHeartRateValue,
            hrv = hrvValue,
            recoveryScore =
                buildRecoveryScore(
                    sleepHours = sleepHours,
                    rmssd = hrvValue,
                    baselineRmssd = baselineRmssd?.toFloat(),
                    averageHeartRate = averageHeartRateValue,
                    baselineAvgHeartRate = baselineAvgHeartRate?.toFloat(),
                ),
            anomalyFlags = anomalyFlags,
            anomalyLabel = primaryAnomalyLabel(anomalyFlags),
            summaryText = summaryText,
        )
    }

    private fun buildSleepScore(sleepHours: Float): Int =
        ((sleepHours / 8f) * 100f).roundToInt().coerceIn(42, 98)

    private fun buildRecoveryScore(
        sleepHours: Float,
        rmssd: Float,
        baselineRmssd: Float?,
        averageHeartRate: Float,
        baselineAvgHeartRate: Float?,
    ): Float {
        val sleepComponent = (sleepHours / 8f).coerceIn(0f, 1f)
        val hrvComponent =
            baselineRmssd
                ?.takeIf { it > 0f }
                ?.let { (rmssd / it).coerceIn(0.55f, 1.25f) }
                ?.let { (it - 0.55f) / 0.70f }
                ?: 0.72f
        val heartRateComponent =
            baselineAvgHeartRate
                ?.takeIf { it > 0f }
                ?.let { (it / averageHeartRate.coerceAtLeast(1f)).coerceIn(0.72f, 1.18f) }
                ?.let { (it - 0.72f) / 0.46f }
                ?: 0.7f

        return (
            sleepComponent * 0.34f +
                hrvComponent * 0.42f +
                heartRateComponent * 0.24f
        ).coerceIn(0f, 1f)
    }

    private fun primaryAnomalyLabel(flags: Set<AnomalyFlag>): String? =
        when {
            flags.contains(AnomalyFlag.PERSISTENT) -> "Persistent alert"
            flags.contains(AnomalyFlag.CONTINUOUS) -> "Continuous alert"
            flags.contains(AnomalyFlag.SINGLE_DAY) -> "Single-day alert"
            else -> null
        }

    private fun buildSleepSummary(items: List<TrendSummaryItem>): String {
        val sleepMinutes = items.mapNotNull { it.sleepMinutes }
        if (sleepMinutes.isEmpty()) {
            return "No sleep data in this window."
        }
        val averageHours = sleepMinutes.average() / 60.0
        val direction = describeDirection(sleepMinutes.first().toDouble(), sleepMinutes.last().toDouble(), "stable")
        val flaggedCount = items.count { it.anomalyFlags.isNotEmpty() }
        val flaggedText = if (flaggedCount > 0) ", $flaggedCount flagged day(s)" else ""
        return "Avg sleep ${formatOneDecimal(averageHours)} h, $direction$flaggedText."
    }

    private fun buildHrvSummary(items: List<TrendSummaryItem>): String {
        val values = items.mapNotNull { it.rmssd }
        if (values.isEmpty()) {
            return "No HRV data in this window."
        }
        val direction = describeDirection(values.first(), values.last(), "stable")
        return "Avg RMSSD ${formatOneDecimal(values.average())} ms, $direction."
    }

    private fun buildAnomalySummary(items: List<TrendSummaryItem>): String {
        val abnormalDays = items.count { it.anomalyFlags.isNotEmpty() }
        if (abnormalDays == 0) {
            return "No anomaly markers in this window."
        }
        val continuousDays =
            items.count {
                it.anomalyFlags.contains(AnomalyFlag.CONTINUOUS) ||
                    it.anomalyFlags.contains(AnomalyFlag.PERSISTENT)
            }
        return "$abnormalDays flagged day(s), $continuousDays continuous/persistent."
    }

    private fun buildHeartRateSummary(items: List<TrendSummaryItem>): String {
        val values = items.mapNotNull { it.avgHeartRate }
        if (values.isEmpty()) {
            return "No heart-rate data in this window."
        }
        val direction = describeDirection(values.first(), values.last(), "stable")
        return "Avg heart rate ${formatOneDecimal(values.average())} bpm, $direction."
    }

    private fun describeDirection(
        first: Double,
        last: Double,
        stableLabel: String,
    ): String =
        when {
            abs(last - first) < 0.5 -> stableLabel
            last > first -> "up vs start"
            else -> "down vs start"
        }

    private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)
}
