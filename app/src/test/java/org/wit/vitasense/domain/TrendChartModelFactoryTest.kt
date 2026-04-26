package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.chart.TrendChartModel
import org.wit.vitasense.ui.trends.TrendChartMetric
import org.wit.vitasense.ui.trends.TrendChartModelFactory
import org.wit.vitasense.ui.trends.TrendSummaryItem

class TrendChartModelFactoryTest {
    @Test
    fun builds_daily_sleep_chart_for_7_day_range() {
        val items =
            listOf(
                TrendSummaryItem(
                    date = "2026-04-18",
                    sleepMinutes = 412,
                    rmssd = 35.0,
                    avgHeartRate = 62.0,
                    anomalyFlags = emptySet(),
                ),
                TrendSummaryItem(
                    date = "2026-04-19",
                    sleepMinutes = 438,
                    rmssd = 38.0,
                    avgHeartRate = 64.0,
                    anomalyFlags = emptySet(),
                ),
                TrendSummaryItem(
                    date = "2026-04-20",
                    sleepMinutes = 401,
                    rmssd = 29.0,
                    avgHeartRate = 69.0,
                    anomalyFlags = setOf(AnomalyFlag.CONTINUOUS),
                ),
            )

        val model =
            TrendChartModelFactory.build(
                items = items,
                range = TimeRange.DAYS_7,
                metric = TrendChartMetric.SLEEP,
            )

        assertTrue(model is TrendChartModel.Line)
        val line = model as TrendChartModel.Line
        assertEquals(7, line.windowSizeDays)
        assertEquals(3, line.entries.size)
        assertEquals("04/20", line.entries.last().axisLabel)
        assertEquals("2026-04-20", line.entries.last().detailLabel)
        assertEquals("6.7 h", line.entries.last().valueText)
        assertTrue(line.entries.last().highlighted)
    }

    @Test
    fun builds_30_point_line_chart_for_30_day_sleep_range() {
        val items =
            (1..30).map { day ->
                TrendSummaryItem(
                    date = "2026-04-${day.toString().padStart(2, '0')}",
                    sleepMinutes = 390 + day,
                    rmssd = 28.0 + day,
                    avgHeartRate = 60.0 + (day % 6),
                    anomalyFlags =
                        if (day in 13..18) {
                            setOf(AnomalyFlag.SINGLE_DAY, AnomalyFlag.CONTINUOUS)
                        } else {
                            emptySet()
                        },
                )
            }

        val model =
            TrendChartModelFactory.build(
                items = items,
                range = TimeRange.DAYS_30,
                metric = TrendChartMetric.SLEEP,
            )

        assertTrue(model is TrendChartModel.Line)
        val line = model as TrendChartModel.Line
        assertEquals(30, line.entries.size)
        assertEquals("04/01", line.entries.first().axisLabel)
        assertEquals("2026-04-30", line.entries.last().detailLabel)
        assertTrue(line.entries[12].highlighted)
        assertEquals("6.5 h", line.entries.first().valueText)
    }

    @Test
    fun builds_anomaly_line_chart_for_30_day_range() {
        val items =
            listOf(
                TrendSummaryItem(
                    date = "2026-04-01",
                    sleepMinutes = 420,
                    rmssd = 30.0,
                    avgHeartRate = 66.0,
                    anomalyFlags = emptySet(),
                ),
                TrendSummaryItem(
                    date = "2026-04-02",
                    sleepMinutes = 410,
                    rmssd = 28.0,
                    avgHeartRate = 67.0,
                    anomalyFlags = setOf(AnomalyFlag.SINGLE_DAY),
                ),
                TrendSummaryItem(
                    date = "2026-04-03",
                    sleepMinutes = 405,
                    rmssd = 24.0,
                    avgHeartRate = 70.0,
                    anomalyFlags = setOf(AnomalyFlag.PERSISTENT),
                ),
                TrendSummaryItem(
                    date = "2026-04-04",
                    sleepMinutes = 430,
                    rmssd = 36.0,
                    avgHeartRate = 64.0,
                    anomalyFlags = emptySet(),
                ),
                TrendSummaryItem(
                    date = "2026-04-05",
                    sleepMinutes = 438,
                    rmssd = 39.0,
                    avgHeartRate = 63.0,
                    anomalyFlags = emptySet(),
                ),
                TrendSummaryItem(
                    date = "2026-04-06",
                    sleepMinutes = 442,
                    rmssd = 41.0,
                    avgHeartRate = 62.0,
                    anomalyFlags = emptySet(),
                ),
            )

        val model =
            TrendChartModelFactory.build(
                items = items,
                range = TimeRange.DAYS_30,
                metric = TrendChartMetric.ANOMALY,
            )

        assertTrue(model is TrendChartModel.Line)
        val line = model as TrendChartModel.Line
        assertEquals(30, line.windowSizeDays)
        assertEquals(6, line.entries.size)
        assertEquals("Stable", line.entries.first().valueText)
        assertEquals("Persistent Anomaly", line.entries[2].valueText)
        assertTrue(line.entries[1].highlighted)
        assertTrue(line.entries[2].highlighted)
        assertEquals(0f, line.minValue)
        assertTrue(line.maxValue >= 3f)
    }
}
