package org.wit.vitasense.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository

class DashboardViewModelTest {
    @Test
    fun exposes_total_score_and_three_home_trend_pages() =
        runBlocking {
            val repository = FakeHealthRepository()
            val authRepository =
                FakeAuthRepository(
                    AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"),
                )
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val viewModel = DashboardViewModel(repository, authRepository, scope)
            val collector = collectState(viewModel, scope)

            repository.summaries.value =
                listOf(
                    summary("2026-04-19", sleepMinutes = 430, rmssd = 36.0, avgHeartRate = 63.0),
                    summary("2026-04-20", sleepMinutes = 415, rmssd = 34.0, avgHeartRate = 65.0),
                    summary("2026-04-21", sleepMinutes = 445, rmssd = 39.0, avgHeartRate = 62.0),
                )
            repository.latestRisk.value = risk(totalScore = 91)

            yield()

            val state = viewModel.state.value
            assertEquals("91", state.totalScore)
            assertEquals(3, state.trendPages.size)
            assertTrue(state.showTrendDots)
            assertEquals(true, state.isSignedIn)
            assertEquals("Welcome, Ava Stone!", state.authPrompt)
            assertEquals("A", state.authInitial)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    private fun collectState(
        viewModel: DashboardViewModel,
        scope: CoroutineScope,
    ) = scope.launch {
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

    private class FakeAuthRepository(
        private val currentUser: AuthUser?,
    ) : AuthRepository {
        override fun observeCurrentUser(): Flow<AuthUser?> = flowOf(currentUser)

        override suspend fun getCurrentUser(): AuthUser? = currentUser

        override suspend fun register(
            fullName: String,
            email: String,
            username: String,
            password: String,
            birthDate: String,
        ): AuthResult = error("unused")

        override suspend fun login(
            identifier: String,
            password: String,
        ): AuthResult = error("unused")

        override suspend fun logout() = Unit
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
