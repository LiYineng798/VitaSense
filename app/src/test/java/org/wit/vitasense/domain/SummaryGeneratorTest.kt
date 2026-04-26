package org.wit.vitasense.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.RiskLevel

class SummaryGeneratorTest {
    @Test
    fun generates_reassuring_summary_for_stable_day() {
        val summary = SummaryGenerator.generate(
            sleepMinutes = 460,
            rmssd = 44.0,
            baselineRmssd = 42.0,
            riskLevel = RiskLevel.LOW,
            anomalyFlags = emptySet(),
        )

        assertTrue(summary.contains("stable"))
    }

    @Test
    fun generates_observation_summary_for_continuous_anomaly() {
        val summary = SummaryGenerator.generate(
            sleepMinutes = 300,
            rmssd = 24.0,
            baselineRmssd = 40.0,
            riskLevel = RiskLevel.HIGH,
            anomalyFlags = setOf(AnomalyFlag.SINGLE_DAY, AnomalyFlag.CONTINUOUS),
        )

        assertTrue(summary.contains("Continuous anomalies"))
    }
}
