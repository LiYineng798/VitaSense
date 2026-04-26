package org.wit.vitasense.model

data class HeartRatePoint(
    val timestamp: Long,
    val heartRate: Int,
)

data class HrvMetrics(
    val rmssd: Double,
    val sdnn: Double,
)

data class BaselineSnapshot(
    val rmssd: Double?,
    val restingHeartRate: Double?,
    val avgHeartRate: Double?,
)

data class DailyMetricsSnapshot(
    val date: String,
    val sleepMinutes: Int?,
    val rmssd: Double?,
    val restingHeartRate: Double?,
    val avgHeartRate: Double?,
)

enum class AnomalyFlag {
    SINGLE_DAY,
    CONTINUOUS,
    PERSISTENT,
}

data class AnomalyDetectionResult(
    val flags: Set<AnomalyFlag>,
    val continuousDays: Int,
)

data class RiskScoreResult(
    val totalScore: Int,
    val riskLevel: RiskLevel,
    val sleepScore: Int,
    val hrvScore: Int,
    val restingHrScore: Int,
    val avgHrScore: Int,
    val explanation: String,
    val suggestion: String,
)
