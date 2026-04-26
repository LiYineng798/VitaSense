package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.RiskLevel

class RiskScorerTest {
    @Test
    fun assigns_high_risk_when_sleep_and_hrv_are_poor() {
        val result = RiskScorer.score(
            sleepMinutes = 260,
            rmssd = 18.0,
            baselineRmssd = 40.0,
            restingHeartRate = 72.0,
            baselineRestingHeartRate = 58.0,
            avgHeartRate = 79.0,
            baselineAvgHeartRate = 64.0,
        )

        assertEquals(RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun assigns_low_risk_when_metrics_are_stable() {
        val result = RiskScorer.score(
            sleepMinutes = 460,
            rmssd = 44.0,
            baselineRmssd = 42.0,
            restingHeartRate = 56.0,
            baselineRestingHeartRate = 57.0,
            avgHeartRate = 61.0,
            baselineAvgHeartRate = 62.0,
        )

        assertEquals(RiskLevel.LOW, result.riskLevel)
    }
}
