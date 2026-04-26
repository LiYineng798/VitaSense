package org.wit.vitasense.domain

import org.wit.vitasense.model.RiskLevel
import org.wit.vitasense.model.RiskScoreResult

object RiskScorer {
    fun score(
        sleepMinutes: Int?,
        rmssd: Double?,
        baselineRmssd: Double?,
        restingHeartRate: Double?,
        baselineRestingHeartRate: Double?,
        avgHeartRate: Double?,
        baselineAvgHeartRate: Double?,
    ): RiskScoreResult {
        val sleepScore = scoreSleep(sleepMinutes)
        val hrvScore = scoreHrv(rmssd, baselineRmssd)
        val restingHrScore = scoreResting(restingHeartRate, baselineRestingHeartRate)
        val avgHrScore = scoreAverage(avgHeartRate, baselineAvgHeartRate)
        val totalScore = sleepScore + hrvScore + restingHrScore + avgHrScore
        val riskLevel =
            when {
                totalScore >= 80 -> RiskLevel.LOW
                totalScore >= 60 -> RiskLevel.MEDIUM
                else -> RiskLevel.HIGH
            }

        val explanation =
            buildList {
                if ((sleepMinutes ?: 0) < 360) add("Sleep is short")
                if (rmssd != null && baselineRmssd != null && rmssd < baselineRmssd * 0.85) add("HRV is below your baseline")
                if (restingHeartRate != null && baselineRestingHeartRate != null && restingHeartRate > baselineRestingHeartRate + 6) add("Resting heart rate is elevated")
                if (avgHeartRate != null && baselineAvgHeartRate != null && avgHeartRate > baselineAvgHeartRate + 8) add("Average heart rate is elevated")
            }.ifEmpty { listOf("Overall metrics are stable") }
                .joinToString("; ")

        val suggestion =
            when (riskLevel) {
                RiskLevel.LOW -> "Keep the current pace and continue watching your daily trends."
                RiskLevel.MEDIUM -> "Reduce today's load and watch changes over the next two days."
                RiskLevel.HIGH -> "Reduce high-load activity, prioritize rest, and keep monitoring."
            }

        return RiskScoreResult(
            totalScore = totalScore,
            riskLevel = riskLevel,
            sleepScore = sleepScore,
            hrvScore = hrvScore,
            restingHrScore = restingHrScore,
            avgHrScore = avgHrScore,
            explanation = explanation,
            suggestion = suggestion,
        )
    }

    private fun scoreSleep(sleepMinutes: Int?): Int =
        when {
            sleepMinutes == null -> 15
            sleepMinutes in 420..540 -> 30
            sleepMinutes in 360..419 -> 24
            sleepMinutes in 300..359 -> 14
            sleepMinutes > 540 -> 24
            else -> 5
        }

    private fun scoreHrv(rmssd: Double?, baselineRmssd: Double?): Int {
        if (rmssd == null || baselineRmssd == null || baselineRmssd <= 0.0) return 15
        val ratio = rmssd / baselineRmssd
        return when {
            ratio >= 1.0 -> 30
            ratio >= 0.85 -> 24
            ratio >= 0.7 -> 12
            else -> 4
        }
    }

    private fun scoreResting(restingHeartRate: Double?, baselineRestingHeartRate: Double?): Int {
        if (restingHeartRate == null || baselineRestingHeartRate == null) return 10
        val delta = restingHeartRate - baselineRestingHeartRate
        return when {
            delta <= 0 -> 20
            delta <= 6 -> 14
            delta <= 10 -> 8
            else -> 2
        }
    }

    private fun scoreAverage(avgHeartRate: Double?, baselineAvgHeartRate: Double?): Int {
        if (avgHeartRate == null || baselineAvgHeartRate == null) return 10
        val delta = avgHeartRate - baselineAvgHeartRate
        return when {
            delta <= 0 -> 20
            delta <= 8 -> 14
            delta <= 12 -> 8
            else -> 2
        }
    }
}
