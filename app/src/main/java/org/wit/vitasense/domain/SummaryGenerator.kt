package org.wit.vitasense.domain

import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.RiskLevel

object SummaryGenerator {
    fun generate(
        sleepMinutes: Int?,
        rmssd: Double?,
        baselineRmssd: Double?,
        riskLevel: RiskLevel,
        anomalyFlags: Set<AnomalyFlag>,
    ): String {
        if (anomalyFlags.contains(AnomalyFlag.CONTINUOUS)) {
            return "Continuous anomalies appeared over the last two days. Reduce heavy activity and keep observing."
        }

        val sleepPart =
            when {
                sleepMinutes == null -> "data is incomplete"
                sleepMinutes < 360 -> "sleep is short"
                sleepMinutes >= 420 -> "sleep is sufficient"
                else -> "sleep is moderate"
            }

        val hrvPart =
            when {
                rmssd == null || baselineRmssd == null -> "HRV is pending"
                rmssd < baselineRmssd * 0.85 -> "HRV is below your baseline"
                else -> "HRV is close to your baseline"
            }

        return when (riskLevel) {
            RiskLevel.LOW -> "Today looks stable, $sleepPart, and $hrvPart."
            RiskLevel.MEDIUM -> "Recovery looks moderate today, $sleepPart, and $hrvPart."
            RiskLevel.HIGH -> "Today needs closer attention, $sleepPart, and $hrvPart."
        }
    }
}
