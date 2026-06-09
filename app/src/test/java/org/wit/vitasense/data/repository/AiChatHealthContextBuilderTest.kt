package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository

class AiChatHealthContextBuilderTest {
    @Test
    fun buildsCompactHealthContext() =
        runBlocking {
            val context =
                AiChatHealthContextBuilder(
                    healthRepository = FakeChatHealthRepository(),
                    moodRepository = FakeChatMoodRepository(),
                ).build()

            assertEquals(82, context["latest_risk"]?.let { it as Map<*, *> }?.get("total_score"))
            assertEquals(1, (context["recent_summaries"] as List<*>).size)
            assertEquals("CALM", context["latest_mood"]?.let { it as Map<*, *> }?.get("mood_type"))
        }
}

private class FakeChatHealthRepository : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)

    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)

    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> =
        flowOf(
            RiskAssessmentRecordEntity(
                date = "2026-06-09",
                totalScore = 82,
                riskLevel = "low",
                sleepScore = 20,
                hrvScore = 20,
                restingHrScore = 20,
                avgHrScore = 22,
                explanation = "Stable",
                suggestionText = "Rest well.",
            ),
        )

    override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> =
        flowOf(
            listOf(
                DailyPhysiologySummaryEntity(
                    date = "2026-06-09",
                    avgHeartRate = 66.0,
                    restingHeartRate = 61.0,
                    rmssd = 35.0,
                    sdnn = 50.0,
                    sleepDurationMinutes = 420,
                    baselineRestingHeartRate = 62.0,
                    baselineRmssd = 34.0,
                    baselineAvgHeartRate = 67.0,
                    anomalyFlags = "",
                    summaryText = "Stable.",
                ),
            ),
        )

    override fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>> = flowOf(emptyList())

    override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> = emptyList()

    override suspend fun importDemoBundle(bundleId: String): ImportOperationResult =
        ImportOperationResult(ImportStatus.SUCCESS, "", 0, 0, 0, 0)

    override suspend fun importRawJson(
        raw: String,
        sourceName: String,
    ): ImportOperationResult = ImportOperationResult(ImportStatus.SUCCESS, "", 0, 0, 0, 0)

    override suspend fun clearAllData() = Unit
}

private class FakeChatMoodRepository : MoodRepository {
    override fun observeMoodRecords(filter: MoodFilter): Flow<List<MoodRecordEntity>> = flowOf(emptyList())

    override suspend fun addMood(
        date: String,
        moodType: MoodType,
        note: String?,
    ) = Unit

    override suspend fun deleteMood(id: Long) = Unit

    override suspend fun getLatestMoodForDate(date: String): MoodRecordEntity? =
        MoodRecordEntity(
            date = date,
            moodType = "CALM",
            moodGroup = "positive",
            note = "steady",
            createdAt = 1L,
        )
}
