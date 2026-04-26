package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.ui.trends.MonthlyInsightFactory
import org.wit.vitasense.ui.trends.TrendDaySnapshot
import org.wit.vitasense.ui.trends.TrendDirection

class MonthlyInsightFactoryTest {
    @Test
    fun builds_monthly_insight_layers_for_30_day_range() {
        val snapshots =
            (1..30).map { day ->
                TrendDaySnapshot(
                    date = "2026-04-${day.toString().padStart(2, '0')}",
                    sleepHours = 6.0f + day * 0.04f,
                    sleepScore = 64 + (day % 20),
                    averageHeartRate = 72f - day * 0.25f,
                    restingHeartRate = 61f - day * 0.12f,
                    hrv = 24f + day * 0.9f,
                    recoveryScore = 0.22f + day * 0.02f,
                    anomalyFlags =
                        if (day in 8..10 || day in 23..24) {
                            setOf(AnomalyFlag.SINGLE_DAY)
                        } else {
                            emptySet()
                        },
                    anomalyLabel = if (day in 8..10 || day in 23..24) "Single-Day Anomaly" else null,
                    summaryText = "Day $day monthly summary",
                )
            }

        val insight = MonthlyInsightFactory.buildOverview(snapshots)

        assertEquals(30, insight.trendPoints.size)
        assertEquals(5, insight.weeklyAggregates.size)
        assertEquals(30, insight.heatmapCells.size)
        assertEquals(3, insight.insightCards.size)
        assertEquals("HRV Trend", insight.insightCards[0].title)
        assertEquals(TrendDirection.UP, insight.insightCards[0].trendDirection)
        assertTrue(insight.trendPoints[8].anomalyCount > 0)
        assertTrue(insight.heatmapCells[23].hasAnomaly)
    }
}
