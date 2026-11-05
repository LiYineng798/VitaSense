package org.wit.vitasense.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.entity.*
import org.wit.vitasense.domain.HealthCorrelationAnalyzer
import org.wit.vitasense.model.*
import org.wit.vitasense.repository.SettingsRepository
import java.time.LocalDate
import java.util.Locale

const val INSIGHT_DISCLAIMER = "Informational only, not a medical diagnosis. Correlation does not imply causation. No statistical significance or predictions are claimed. Recovery uses the existing health assessment score; its formula includes sleep and heart rate, so these associations are partly built in."
fun Double?.display(unit: String = ""): String = this?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: "Unavailable"

data class HealthSnapshot(
    val start: LocalDate, val end: LocalDate,
    val summaries: List<DailyPhysiologySummaryEntity>, val risks: List<RiskAssessmentRecordEntity>,
    val moods: List<MoodRecordEntity>, val sleeps: List<SleepRecordEntity>,
) {
    val dailyMood get() = moods.groupBy { it.date }.mapValues { (_, records) -> records.mapNotNull { HealthCorrelationAnalyzer.moodValence(it.moodType) }.takeIf { it.isNotEmpty() }?.average() }
    val sleepByDate get() = sleeps.associate { it.date to it.durationMinutes.toDouble() } + summaries.mapNotNull { s -> s.sleepDurationMinutes?.let { s.date to it.toDouble() } }.toMap()
    val days get() = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
}

class HealthInsightsRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val ai: AiChatRemoteDataSource,
) {
    suspend fun snapshot(start: LocalDate, end: LocalDate): HealthSnapshot = database.withTransaction {
        HealthSnapshot(start, end,
            database.dailySummaryDao().getRange(start.toString(), end.toString()),
            database.riskAssessmentDao().getRange(start.toString(), end.toString()),
            database.moodRecordDao().getAllForSync().filter { it.deletedAt == null && it.date >= start.toString() && it.date <= end.toString() },
            database.sleepRecordDao().getAllActiveForSync().filter { it.date >= start.toString() && it.date <= end.toString() })
    }

    fun correlations(s: HealthSnapshot): List<HealthCorrelationAnalyzer.Result> {
        val moods = s.dailyMood; val sleep = s.sleepByDate; val risks = s.risks.associateBy { it.date }
        return listOf(
            HealthCorrelationAnalyzer.analyze("Sleep duration vs next-day mood", sleep.map { (date, minutes) -> minutes to moods[LocalDate.parse(date).plusDays(1).toString()] }),
            HealthCorrelationAnalyzer.analyze("HRV vs mood", s.summaries.map { it.rmssd to moods[it.date] }),
            HealthCorrelationAnalyzer.analyze("Sleep duration vs HRV", s.summaries.map { sleep[it.date] to it.rmssd }),
            HealthCorrelationAnalyzer.analyze("Heart rate vs recovery score", s.summaries.map { it.avgHeartRate to risks[it.date]?.totalScore?.toDouble() }),
            HealthCorrelationAnalyzer.analyze("Sleep duration vs recovery score", sleep.map { (date, minutes) -> minutes to risks[date]?.totalScore?.toDouble() }),
        )
    }

    /** Call only following the per-request consent dialog. Only generated aggregates enter this API. */
    suspend fun explainAggregates(aggregate: String): String {
        val config = settings.getAiProviderConfig()
        if (!config.isComplete) return "Local analysis retained. Configure an AI provider in Settings to request an explanation."
        return try {
            val result = StringBuilder(); var done = false; var failed = false
            ai.streamChat(config, listOf(AiChatMessage(AiChatRole.USER,
                "Explain these aggregated health observations in English. Separate observations from hypotheses; give practical non-medical suggestions. Include main observations, supported positive changes, and areas to review; do not invent changes when comparison data is missing. Do not diagnose, predict disease, or claim significance. Mention formula-derived recovery associations.\n$aggregate")), emptyMap()) { event ->
                when (event) { is AiChatStreamEvent.Delta -> result.append(event.text); is AiChatStreamEvent.Done -> done = true; is AiChatStreamEvent.Error -> failed = true }
            }
            if (done && !failed && result.isNotBlank()) result.toString() else "AI unavailable. Local analysis retained."
        } catch (e: CancellationException) { throw e } catch (_: Exception) { "AI unavailable. Local analysis retained." }
    }
}
