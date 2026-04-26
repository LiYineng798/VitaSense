package org.wit.vitasense.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.ui.common.chart.TrendChartModel

class DashboardHomeUiMapperTest {
    @Test
    fun builds_score_and_three_trend_pages_when_recent_data_exists() {
        val state =
            DashboardHomeUiMapper.build(
                summaries =
                    listOf(
                        summary("2026-04-19", sleepMinutes = 430, rmssd = 36.0, avgHeartRate = 63.0),
                        summary("2026-04-20", sleepMinutes = 415, rmssd = 34.0, avgHeartRate = 65.0),
                        summary("2026-04-21", sleepMinutes = 445, rmssd = 39.0, avgHeartRate = 62.0),
                    ),
                latestRisk = risk(totalScore = 84),
            )

        assertEquals("84", state.totalScore)
        assertEquals(3, state.trendPages.size)
        assertEquals("Sleep", state.trendPages[0].title)
        assertEquals("HRV", state.trendPages[1].title)
        assertEquals("Heart Rate", state.trendPages[2].title)
        assertTrue(state.trendPages.all { it.chartModel is TrendChartModel.Line })
        assertTrue(state.showTrendDots)
    }

    @Test
    fun builds_fallback_score_and_single_empty_page_when_no_trend_data_exists() {
        val state = DashboardHomeUiMapper.build(emptyList(), latestRisk = null)

        assertEquals("--", state.totalScore)
        assertEquals(1, state.trendPages.size)
        assertEquals("7-Day Trend", state.trendPages.single().title)
        assertTrue(state.trendPages.single().chartModel is TrendChartModel.Empty)
        assertFalse(state.showTrendDots)
    }

    @Test
    fun keeps_missing_metric_page_empty_without_hiding_other_pages() {
        val state =
            DashboardHomeUiMapper.build(
                summaries =
                    listOf(
                        summary("2026-04-19", sleepMinutes = 430, rmssd = null, avgHeartRate = 63.0),
                        summary("2026-04-20", sleepMinutes = 415, rmssd = null, avgHeartRate = 65.0),
                        summary("2026-04-21", sleepMinutes = 445, rmssd = null, avgHeartRate = 62.0),
                    ),
                latestRisk = risk(totalScore = 76),
            )

        assertEquals(3, state.trendPages.size)
        assertTrue(state.trendPages[0].chartModel is TrendChartModel.Line)
        assertTrue(state.trendPages[1].chartModel is TrendChartModel.Empty)
        assertTrue(state.trendPages[2].chartModel is TrendChartModel.Line)
        assertTrue(state.showTrendDots)
    }

    @Test
    fun uses_single_empty_home_page_when_all_recent_trend_metrics_are_missing() {
        val state =
            DashboardHomeUiMapper.build(
                summaries =
                    listOf(
                        summary("2026-04-19", sleepMinutes = null, rmssd = null, avgHeartRate = null),
                        summary("2026-04-20", sleepMinutes = null, rmssd = null, avgHeartRate = null),
                    ),
                latestRisk = risk(totalScore = 64),
            )

        assertEquals(1, state.trendPages.size)
        assertEquals("7-Day Trend", state.trendPages.single().title)
        assertTrue(state.trendPages.single().chartModel is TrendChartModel.Empty)
        assertFalse(state.showTrendDots)
    }

    private fun summary(
        date: String,
        sleepMinutes: Int?,
        rmssd: Double?,
        avgHeartRate: Double?,
    ) = DailyPhysiologySummaryEntity(
        date = date,
        avgHeartRate = avgHeartRate,
        restingHeartRate = null,
        rmssd = rmssd,
        sdnn = null,
        sleepDurationMinutes = sleepMinutes,
        baselineRestingHeartRate = null,
        baselineRmssd = 35.0,
        baselineAvgHeartRate = 64.0,
        anomalyFlags = "",
        summaryText = "",
    )

    private fun risk(totalScore: Int) = RiskAssessmentRecordEntity(
        date = "2026-04-21",
        totalScore = totalScore,
        riskLevel = "medium",
        sleepScore = 24,
        hrvScore = 24,
        restingHrScore = 14,
        avgHrScore = 14,
        explanation = "Recovery is mixed",
        suggestionText = "Keep watching the trend",
    )
}
