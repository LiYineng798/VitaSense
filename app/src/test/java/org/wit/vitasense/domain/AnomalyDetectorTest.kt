package org.wit.vitasense.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.BaselineSnapshot
import org.wit.vitasense.model.DailyMetricsSnapshot

class AnomalyDetectorTest {
    @Test
    fun flags_single_day_anomaly_when_metrics_drop_or_rise() {
        val result = AnomalyDetector.detect(
            snapshot = DailyMetricsSnapshot(
                date = "2026-04-23",
                sleepMinutes = 280,
                rmssd = 22.0,
                restingHeartRate = 69.0,
                avgHeartRate = 74.0,
            ),
            baseline = BaselineSnapshot(
                rmssd = 40.0,
                restingHeartRate = 58.0,
                avgHeartRate = 63.0,
            ),
            previousContinuousDays = 0,
        )

        assertTrue(result.flags.contains(AnomalyFlag.SINGLE_DAY))
    }

    @Test
    fun escalates_to_continuous_anomaly_when_previous_day_was_abnormal() {
        val result = AnomalyDetector.detect(
            snapshot = DailyMetricsSnapshot(
                date = "2026-04-24",
                sleepMinutes = 310,
                rmssd = 24.0,
                restingHeartRate = 66.0,
                avgHeartRate = 73.0,
            ),
            baseline = BaselineSnapshot(
                rmssd = 42.0,
                restingHeartRate = 58.0,
                avgHeartRate = 62.0,
            ),
            previousContinuousDays = 1,
        )

        assertTrue(result.flags.contains(AnomalyFlag.CONTINUOUS))
    }
}
