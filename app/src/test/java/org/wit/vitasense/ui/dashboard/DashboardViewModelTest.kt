package org.wit.vitasense.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.repository.HealthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposes_total_score_and_three_home_trend_pages() =
        runTest {
            val repository = FakeHealthRepository()
            val viewModel = DashboardViewModel(repository)
            val collector = collectState(viewModel, this)

            repository.summaries.value =
                listOf(
                    summary("2026-04-19", sleepMinutes = 430, rmssd = 36.0, avgHeartRate = 63.0),
                    summary("2026-04-20", sleepMinutes = 415, rmssd = 34.0, avgHeartRate = 65.0),
                    summary("2026-04-21", sleepMinutes = 445, rmssd = 39.0, avgHeartRate = 62.0),
                )
            repository.latestRisk.value = risk(totalScore = 91)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("91", state.totalScore)
            assertEquals(3, state.trendPages.size)
            assertTrue(state.showTrendDots)

            collector.cancel()
        }

    private fun collectState(
        viewModel: DashboardViewModel,
        scope: TestScope,
    ) = scope.backgroundScope.launch {
        viewModel.state.collect {}
    }

    private class FakeHealthRepository : HealthRepository {
        val summaries = MutableStateFlow<List<DailyPhysiologySummaryEntity>>(emptyList())
        val latestRisk = MutableStateFlow<RiskAssessmentRecordEntity?>(null)

        override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)

        override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)

        override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> = latestRisk

        override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> = summaries

        override fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>> = flowOf(emptyList())

        override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> = emptyList()

        override suspend fun importDemoBundle(bundleId: String): ImportOperationResult =
            ImportOperationResult(ImportStatus.SUCCESS, "unused", 0, 0, 0, 0)

        override suspend fun importRawJson(
            raw: String,
            sourceName: String,
        ): ImportOperationResult = ImportOperationResult(ImportStatus.SUCCESS, "unused", 0, 0, 0, 0)

        override suspend fun clearAllData() = Unit
    }

    private fun summary(
        date: String,
        sleepMinutes: Int?,
        rmssd: Double?,
        avgHeartRate: Double?,
    ) = DailyPhysiologySummaryEntity(
        date = date,
        avgHeartRate = avgHeartRate,
        restingHeartRate = null,
        rmssd = rmssd,
        sdnn = null,
        sleepDurationMinutes = sleepMinutes,
        baselineRestingHeartRate = null,
        baselineRmssd = 35.0,
        baselineAvgHeartRate = 64.0,
        anomalyFlags = "",
        summaryText = "",
    )

    private fun risk(totalScore: Int) = RiskAssessmentRecordEntity(
        date = "2026-04-21",
        totalScore = totalScore,
        riskLevel = "low",
        sleepScore = 30,
        hrvScore = 24,
        restingHrScore = 20,
        avgHrScore = 17,
        explanation = "Stable recovery",
        suggestionText = "Keep the current pace",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
