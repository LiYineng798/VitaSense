package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.ui.trends.TrendDaySnapshot
import org.wit.vitasense.ui.trends.TrendDirection
import org.wit.vitasense.ui.trends.WeeklyComparisonFactory

class WeeklyComparisonFactoryTest {
    @Test
    fun builds_weekly_overview_with_three_series_and_seven_cards() {
        val snapshots =
            (1..7).map { day ->
                TrendDaySnapshot(
                    date = "2026-04-${day.toString().padStart(2, '0')}",
                    sleepHours = 6.1f + day * 0.15f,
                    sleepScore = 68 + day,
                    averageHeartRate = 70f - day,
                    restingHeartRate = 62f - day * 0.4f,
                    hrv = 28f + day * 2f,
                    recoveryScore = 0.34f + day * 0.06f,
                    anomalyFlags =
                        if (day == 3 || day == 4) {
                            setOf(AnomalyFlag.CONTINUOUS)
                        } else {
                            emptySet()
                        },
                    anomalyLabel = if (day == 3 || day == 4) "Continuous fluctuation" else null,
                    summaryText = "Day $day status summary",
                )
            }

        val overview = WeeklyComparisonFactory.buildOverview(snapshots)

        assertEquals(3, overview.trendSeries.size)
        assertEquals(7, overview.cards.size)
        assertEquals("HRV", overview.trendSeries[0].title)
        assertEquals(TrendDirection.UP, overview.trendSeries[0].trendDirection)
        assertEquals("04/07", overview.cards.last().dateLabel)
        assertTrue(overview.cards[2].hasAnomaly)
        assertEquals("Continuous fluctuation", overview.cards[2].anomalyLabel)
        assertTrue(overview.cards.last().recoveryScore > overview.cards.first().recoveryScore)
    }
}
