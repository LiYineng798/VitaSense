package org.wit.vitasense.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.data.importer.DemoImportProvider
import org.wit.vitasense.data.repository.DefaultHealthRepository
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.ImportLogEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.db.entity.SleepRecordEntity
import org.wit.vitasense.domain.HealthRecomputeEngine

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_and_query_mood_records_by_group() =
        runBlocking {
            database.moodRecordDao().insert(
                MoodRecordEntity(
                    date = "2026-04-23",
                    moodType = "CALM",
                    moodGroup = "positive",
                    note = "ok",
                ),
            )
            database.moodRecordDao().insert(
                MoodRecordEntity(
                    date = "2026-04-22",
                    moodType = "LOW",
                    moodGroup = "negative",
                    note = "tired",
                ),
            )

            val result =
                database.moodRecordDao().observeFiltered(
                    group = "negative",
                    startDate = null,
                    endDate = null,
                ).first()

            assertEquals(1, result.size)
            assertEquals("LOW", result.first().moodType)
        }

    @Test
    fun clear_all_data_removes_health_and_mood_records() =
        runBlocking {
            val repository =
                DefaultHealthRepository(
                    database = database,
                    heartRateDao = database.heartRateRawSampleDao(),
                    sleepRecordDao = database.sleepRecordDao(),
                    dailySummaryDao = database.dailySummaryDao(),
                    riskAssessmentDao = database.riskAssessmentDao(),
                    moodRecordDao = database.moodRecordDao(),
                    importLogDao = database.importLogDao(),
                    demoImportProvider = DemoImportProvider(),
                    recomputeEngine =
                        HealthRecomputeEngine(
                            heartRateDao = database.heartRateRawSampleDao(),
                            sleepRecordDao = database.sleepRecordDao(),
                            dailySummaryDao = database.dailySummaryDao(),
                            riskAssessmentDao = database.riskAssessmentDao(),
                        ),
                )

            database.heartRateRawSampleDao().insertAll(
                listOf(
                    HeartRateRawSampleEntity(
                        sampleTimestamp = 1_714_000_000_000,
                        date = "2026-04-23",
                        heartRate = 62,
                        sourceBatchId = "demo-a",
                    ),
                ),
            )
            database.sleepRecordDao().insert(
                SleepRecordEntity(
                    date = "2026-04-23",
                    startAt = 1_714_000_000_000,
                    endAt = 1_714_028_800_000,
                    durationMinutes = 480,
                    sourceBatchId = "demo-a",
                ),
            )
            database.dailySummaryDao().upsert(
                DailyPhysiologySummaryEntity(
                    date = "2026-04-23",
                    avgHeartRate = 62.0,
                    restingHeartRate = 58.0,
                    rmssd = 35.0,
                    sdnn = 42.0,
                    sleepDurationMinutes = 480,
                    baselineRestingHeartRate = 58.0,
                    baselineRmssd = 36.0,
                    baselineAvgHeartRate = 61.0,
                    anomalyFlags = "",
                    summaryText = "稳定",
                ),
            )
            database.riskAssessmentDao().upsert(
                RiskAssessmentRecordEntity(
                    date = "2026-04-23",
                    totalScore = 82,
                    riskLevel = "low",
                    sleepScore = 24,
                    hrvScore = 26,
                    restingHrScore = 16,
                    avgHrScore = 16,
                    explanation = "状态稳定",
                    suggestionText = "保持节奏",
                ),
            )
            database.moodRecordDao().insert(
                MoodRecordEntity(
                    date = "2026-04-23",
                    moodType = "CALM",
                    moodGroup = "positive",
                    note = "ok",
                ),
            )
            database.importLogDao().insert(
                ImportLogEntity(
                    batchId = "demo-a",
                    sourceType = "mock_json",
                    sourceName = "demo-a",
                    status = "success",
                    message = "imported",
                    rawCount = 2,
                    insertedCount = 2,
                    duplicateCount = 0,
                    invalidCount = 0,
                    checksum = "checksum",
                ),
            )

            repository.clearAllData()

            assertNull(database.heartRateRawSampleDao().getLatestOnce())
            assertNull(database.sleepRecordDao().getByDate("2026-04-23"))
            assertNull(database.dailySummaryDao().getLatestOnce())
            assertNull(database.riskAssessmentDao().getLatestOnce())
            assertEquals(
                emptyList<MoodRecordEntity>(),
                database.moodRecordDao().observeFiltered(null, null, null).first(),
            )
            assertNull(database.importLogDao().observeLatest().first())
        }
}
