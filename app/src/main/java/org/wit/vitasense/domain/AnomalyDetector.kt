package org.wit.vitasense.domain

import org.wit.vitasense.model.AnomalyDetectionResult
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.BaselineSnapshot
import org.wit.vitasense.model.DailyMetricsSnapshot

object AnomalyDetector {
    fun detect(
        snapshot: DailyMetricsSnapshot,
        baseline: BaselineSnapshot?,
        previousContinuousDays: Int,
    ): AnomalyDetectionResult {
        val flags = linkedSetOf<AnomalyFlag>()
        val severe = countSevereRules(snapshot, baseline)
        val moderate = countModerateRules(snapshot, baseline)

        val isSingleDay = severe > 0 || moderate >= 2
        if (isSingleDay) {
            flags += AnomalyFlag.SINGLE_DAY
        }

        val continuousDays =
            if (isSingleDay) previousContinuousDays + 1 else 0

        if (continuousDays >= 2) {
            flags += AnomalyFlag.CONTINUOUS
        }
        if (continuousDays >= 3) {
            flags += AnomalyFlag.PERSISTENT
        }

        return AnomalyDetectionResult(
            flags = flags,
            continuousDays = continuousDays,
        )
    }

    private fun countSevereRules(
        snapshot: DailyMetricsSnapshot,
        baseline: BaselineSnapshot?,
    ): Int {
        var count = 0
        if ((snapshot.sleepMinutes ?: Int.MAX_VALUE) < 300) count++
        if (baseline?.rmssd != null && snapshot.rmssd != null && snapshot.rmssd < baseline.rmssd * 0.7) count++
        if (baseline?.restingHeartRate != null && snapshot.restingHeartRate != null &&
            snapshot.restingHeartRate > baseline.restingHeartRate + 10
        ) {
            count++
        }
        if (baseline?.avgHeartRate != null && snapshot.avgHeartRate != null &&
            snapshot.avgHeartRate > baseline.avgHeartRate + 12
        ) {
            count++
        }
        return count
    }

    private fun countModerateRules(
        snapshot: DailyMetricsSnapshot,
        baseline: BaselineSnapshot?,
    ): Int {
        var count = 0
        if ((snapshot.sleepMinutes ?: Int.MAX_VALUE) < 360) count++
        if (baseline?.rmssd != null && snapshot.rmssd != null && snapshot.rmssd < baseline.rmssd * 0.85) count++
        if (baseline?.restingHeartRate != null && snapshot.restingHeartRate != null &&
            snapshot.restingHeartRate > baseline.restingHeartRate + 6
        ) {
            count++
        }
        if (baseline?.avgHeartRate != null && snapshot.avgHeartRate != null &&
            snapshot.avgHeartRate > baseline.avgHeartRate + 8
        ) {
            count++
        }
        return count
    }
}
