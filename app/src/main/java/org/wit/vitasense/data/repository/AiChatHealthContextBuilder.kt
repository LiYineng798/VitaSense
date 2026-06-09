package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.first
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository
import org.wit.vitasense.util.DateUtils

class AiChatHealthContextBuilder(
    private val healthRepository: HealthRepository,
    private val moodRepository: MoodRepository,
) {
    suspend fun build(): Map<String, Any?> {
        val latestRisk = healthRepository.observeLatestRisk().first()
        val summaries = healthRepository.observeSummaries(7).first()
        val latestDate = summaries.maxByOrNull { it.date }?.date ?: DateUtils.todayString()
        val mood = moodRepository.getLatestMoodForDate(latestDate)
        return mapOf(
            "latest_risk" to
                latestRisk?.let {
                    mapOf(
                        "date" to it.date,
                        "total_score" to it.totalScore,
                        "risk_level" to it.riskLevel,
                        "explanation" to it.explanation,
                        "suggestion" to it.suggestionText,
                    )
                },
            "recent_summaries" to
                summaries.map {
                    mapOf(
                        "date" to it.date,
                        "sleep_minutes" to it.sleepDurationMinutes,
                        "rmssd" to it.rmssd,
                        "resting_heart_rate" to it.restingHeartRate,
                        "avg_heart_rate" to it.avgHeartRate,
                        "anomaly_flags" to it.anomalyFlags,
                        "summary" to it.summaryText,
                    )
                },
            "latest_mood" to
                mood?.let {
                    mapOf(
                        "date" to it.date,
                        "mood_type" to it.moodType,
                        "mood_group" to it.moodGroup,
                        "note" to it.note,
                    )
                },
        )
    }
}
