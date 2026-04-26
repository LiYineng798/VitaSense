package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.ui.trends.MonthlyTrendPointModel
import org.wit.vitasense.ui.trends.RecoveryHeatCellModel
import org.wit.vitasense.ui.trends.TrendsTooltipFactory

class TrendsTooltipFactoryTest {
    @Test
    fun builds_month_chart_tooltip_text() {
        val tooltip =
            TrendsTooltipFactory.monthChart(
                MonthlyTrendPointModel(
                    dayLabel = "04/17",
                    rawHrv = 41.8f,
                    smoothedHrv = 39.4f,
                    averageHeartRate = 63.6f,
                    sleepHours = 6.8f,
                    anomalyCount = 2,
                ),
            )

        assertEquals("04/17", tooltip.title)
        assertEquals("HRV 41.8 ms", tooltip.primaryText)
        assertEquals("Sleep 6.8 h | Avg Heart Rate 64 bpm", tooltip.secondaryText)
        assertEquals("Anomaly flags 2", tooltip.tertiaryText)
    }

    @Test
    fun builds_heatmap_tooltip_text() {
        val tooltip =
            TrendsTooltipFactory.heatmap(
                RecoveryHeatCellModel(
                    dayLabel = "04/17",
                    intensity = 0.72f,
                    hasAnomaly = true,
                ),
            )

        assertEquals("04/17", tooltip.title)
        assertEquals("Recovery 72%", tooltip.primaryText)
        assertEquals("Status: anomaly present", tooltip.secondaryText)
        assertEquals(null, tooltip.tertiaryText)
    }

    @Test
    fun finds_nearest_center_for_touch_point() {
        val index = TrendsTooltipFactory.nearestIndex(listOf(24f, 78f, 132f, 186f), 121f)

        assertEquals(2, index)
    }
}
