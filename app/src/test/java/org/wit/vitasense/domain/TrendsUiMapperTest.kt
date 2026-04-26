package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.trends.TrendSummaryItem
import org.wit.vitasense.ui.trends.TrendsUiMapper

class TrendsUiMapperTest {
    @Test
    fun builds_weekly_overview_from_7_day_summaries() {
        val items =
            listOf(
                TrendSummaryItem(
                    date = "2026-04-20",
                    sleepMinutes = 420,
                    rmssd = 38.0,
                    avgHeartRate = 62.0,
                    restingHeartRate = 56.0,
                    baselineRmssd = 34.0,
                    baselineAvgHeartRate = 64.0,
                    anomalyFlags = emptySet(),
                    summaryText = "Stable status",
                ),
                TrendSummaryItem(
                    date = "2026-04-21",
                    sleepMinutes = 390,
                    rmssd = 30.0,
                    avgHeartRate = 70.0,
                    restingHeartRate = 61.0,
                    baselineRmssd = 35.0,
                    baselineAvgHeartRate = 64.0,
                    anomalyFlags = setOf(AnomalyFlag.SINGLE_DAY, AnomalyFlag.CONTINUOUS),
                    summaryText = "Recovery is softer",
                ),
            )

        val state = TrendsUiMapper.buildState(items, TimeRange.DAYS_7)

        assertTrue(state.weekOverview != null)
        assertNull(state.monthOverview)
        assertEquals(2, state.weekOverview!!.cards.size)
        assertEquals(3, state.weekOverview!!.trendSeries.size)
        assertTrue(state.weekOverview!!.cards[1].hasAnomaly)
        assertEquals("2026-04-21", state.latestSelectedDate)
    }

    @Test
    fun builds_monthly_insight_from_30_day_summaries() {
        val items =
            (1..30).map { day ->
                TrendSummaryItem(
                    date = "2026-04-${day.toString().padStart(2, '0')}",
                    sleepMinutes = 400 + day,
                    rmssd = 30.0 + day,
                    avgHeartRate = 60.0 + day,
                    restingHeartRate = 54.0 + day * 0.2,
                    baselineRmssd = 30.0,
                    baselineAvgHeartRate = 66.0,
                    anomalyFlags = emptySet(),
                    summaryText = "Day $day status",
                )
            }

        val state = TrendsUiMapper.buildState(items, TimeRange.DAYS_30)

        assertNull(state.weekOverview)
        assertTrue(state.monthOverview != null)
        assertEquals(30, state.monthOverview!!.trendPoints.size)
        assertEquals(5, state.monthOverview!!.weeklyAggregates.size)
        assertEquals("2026-04-30", state.latestSelectedDate)
    }

    @Test
    fun preserves_30_day_window_for_sparse_30_day_state() {
        val items =
            listOf(
                TrendSummaryItem(
                    date = "2026-04-01",
                    sleepMinutes = 420,
                    rmssd = 32.0,
                    avgHeartRate = 64.0,
                    restingHeartRate = 56.0,
                    baselineRmssd = 31.0,
                    baselineAvgHeartRate = 63.0,
                    anomalyFlags = emptySet(),
                    summaryText = "Status is stable",
                ),
                TrendSummaryItem(
                    date = "2026-04-02",
                    sleepMinutes = 405,
                    rmssd = 27.0,
                    avgHeartRate = 69.0,
                    restingHeartRate = 60.0,
                    baselineRmssd = 31.0,
                    baselineAvgHeartRate = 63.0,
                    anomalyFlags = setOf(AnomalyFlag.PERSISTENT),
                    summaryText = "Pressure is rising",
                ),
            )

        val state = TrendsUiMapper.buildState(items, TimeRange.DAYS_30)

        assertTrue(state.monthOverview != null)
        assertEquals(2, state.monthOverview!!.trendPoints.size)
        assertEquals(30, state.monthOverview!!.windowSizeDays)
    }
}
