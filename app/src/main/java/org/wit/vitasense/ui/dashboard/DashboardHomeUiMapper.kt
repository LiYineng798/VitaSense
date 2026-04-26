package org.wit.vitasense.ui.dashboard

import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.chart.TrendChartModel
import org.wit.vitasense.ui.trends.TrendChartMetric
import org.wit.vitasense.ui.trends.TrendChartModelFactory
import org.wit.vitasense.ui.trends.TrendSummaryItem

object DashboardHomeUiMapper {
    fun build(
        summaries: List<DailyPhysiologySummaryEntity>,
        latestRisk: RiskAssessmentRecordEntity?,
    ): DashboardScreenState {
        val trendItems =
            summaries
                .sortedBy { it.date }
                .takeLast(TimeRange.DAYS_7.days)
                .map { it.toTrendSummaryItem() }

        if (trendItems.isEmpty()) {
            return DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = listOf(DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty)),
                showTrendDots = false,
            )
        }

        val pages =
            listOf(
                DashboardTrendPageModel(
                    title = "Sleep",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.SLEEP),
                ),
                DashboardTrendPageModel(
                    title = "HRV",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.HRV),
                ),
                DashboardTrendPageModel(
                    title = "Heart Rate",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.HEART_RATE),
                ),
            )

        val hasRenderableTrend = pages.any { it.chartModel is TrendChartModel.Line }
        return if (!hasRenderableTrend) {
            DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = listOf(DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty)),
                showTrendDots = false,
            )
        } else {
            DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = pages,
                showTrendDots = true,
            )
        }
    }

    private fun DailyPhysiologySummaryEntity.toTrendSummaryItem() =
        TrendSummaryItem(
            date = date,
            sleepMinutes = sleepDurationMinutes,
            rmssd = rmssd,
            avgHeartRate = avgHeartRate,
            restingHeartRate = restingHeartRate,
            baselineRmssd = baselineRmssd,
            baselineAvgHeartRate = baselineAvgHeartRate,
            anomalyFlags = anomalyFlags.toAnomalyFlags(),
            summaryText = summaryText,
        )

    private fun String.toAnomalyFlags(): Set<AnomalyFlag> =
        split("|")
            .mapNotNull { raw ->
                raw.takeIf { it.isNotBlank() }?.let {
                    runCatching { AnomalyFlag.valueOf(it) }.getOrNull()
                }
            }.toSet()
}
