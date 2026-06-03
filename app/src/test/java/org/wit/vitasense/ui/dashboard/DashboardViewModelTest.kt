package org.wit.vitasense.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
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
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.AiAdviceRepository
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

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
            val viewModel =
                DashboardViewModel(
                    healthRepository = repository,
                    authRepository = authRepository,
                    settingsRepository = FakeSettingsRepository(),
                    aiAdviceRepository = FakeAiAdviceRepository(),
                    scope = scope,
                )
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

    @Test
    fun ai_card_requires_settings_when_api_key_is_missing() {
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val viewModel =
                DashboardViewModel(
                    healthRepository = FakeHealthRepository(),
                    authRepository = FakeAuthRepository(null),
                    settingsRepository = FakeSettingsRepository(),
                    aiAdviceRepository = FakeAiAdviceRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()

            assertEquals("Set up AI advice in Settings.", viewModel.state.value.aiAdvice.statusText)
            assertEquals("Set up", viewModel.state.value.aiAdvice.actionText)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun generate_ai_advice_ignores_duplicate_click_while_loading() {
        runBlocking {
            val health = FakeHealthRepository()
            health.summaries.value = listOf(summary("2026-06-02", 430, 35.0, 65.0))
            health.latestRisk.value = risk(82)
            val settings = FakeSettingsRepository()
            settings.aiConfig.value = AiProviderConfig(apiKey = "sk-test")
            val adviceRepository = FakeAiAdviceRepository(delayUntilReleased = true)
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val viewModel =
                DashboardViewModel(
                    healthRepository = health,
                    authRepository = FakeAuthRepository(null),
                    settingsRepository = settings,
                    aiAdviceRepository = adviceRepository,
                    scope = scope,
                )

            val first = scope.launch { viewModel.generateAiAdvice() }
            viewModel.generateAiAdvice()

            assertEquals(1, adviceRepository.calls)
            adviceRepository.release()
            first.join()
            scope.coroutineContext[Job]?.cancel()
        }
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

    private class FakeSettingsRepository : SettingsRepository {
        val aiConfig = MutableStateFlow(AiProviderConfig())
        private val latestAiAdvice = MutableStateFlow<AiAdvice?>(null)
        private val latestAiAdviceGeneratedAt = MutableStateFlow<Long?>(null)
        private val lastSyncAt = MutableStateFlow<Long?>(null)
        private val syncStatus = MutableStateFlow("idle")
        private val syncError = MutableStateFlow("")

        override fun observeThemeMode(): Flow<ThemeMode> = flowOf(ThemeMode.LIGHT)

        override fun observeThemeFamily(): Flow<ThemeFamily> = flowOf(ThemeFamily.DEFAULT)

        override fun observeAuthBaseUrl(): Flow<String> = flowOf("")

        override fun observeAuthToken(): Flow<String> = flowOf("")

        override fun observeCurrentUserJson(): Flow<String> = flowOf("")

        override fun observeCurrentUserId(): Flow<Long?> = flowOf(null)

        override fun observeAiProviderConfig(): Flow<AiProviderConfig> = aiConfig

        override fun observeLatestAiAdvice(): Flow<AiAdvice?> = latestAiAdvice

        override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> = latestAiAdviceGeneratedAt

        override fun observeLastSyncAt(): Flow<Long?> = lastSyncAt

        override fun observeSyncStatus(): Flow<String> = syncStatus

        override fun observeSyncError(): Flow<String> = syncError

        override suspend fun getThemeMode(): ThemeMode = ThemeMode.LIGHT

        override suspend fun getThemeFamily(): ThemeFamily = ThemeFamily.DEFAULT

        override suspend fun getAuthBaseUrl(): String = ""

        override suspend fun getAuthToken(): String = ""

        override suspend fun getCurrentUserJson(): String = ""

        override suspend fun getCurrentUserId(): Long? = null

        override suspend fun getAiProviderConfig(): AiProviderConfig = aiConfig.value

        override suspend fun getLatestAiAdvice(): AiAdvice? = latestAiAdvice.value

        override suspend fun getLatestAiAdviceGeneratedAt(): Long? = latestAiAdviceGeneratedAt.value

        override suspend fun getLastSyncAt(): Long? = lastSyncAt.value

        override suspend fun getSyncStatus(): String = syncStatus.value

        override suspend fun getSyncError(): String = syncError.value

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setThemeFamily(family: ThemeFamily) = Unit

        override suspend fun setAuthBaseUrl(baseUrl: String) = Unit

        override suspend fun setAuthToken(token: String?) = Unit

        override suspend fun setCurrentUserJson(userJson: String?) = Unit

        override suspend fun setCurrentUserId(userId: Long?) = Unit

        override suspend fun setAiProviderConfig(config: AiProviderConfig) {
            aiConfig.value = config
        }

        override suspend fun setLatestAiAdvice(
            advice: AiAdvice,
            generatedAt: Long,
        ) {
            latestAiAdvice.value = advice
            latestAiAdviceGeneratedAt.value = generatedAt
        }

        override suspend fun setSyncStatus(
            status: String,
            error: String?,
            syncedAt: Long?,
        ) {
            syncStatus.value = status
            syncError.value = error.orEmpty()
            if (syncedAt != null) lastSyncAt.value = syncedAt
        }
    }

    private class FakeAiAdviceRepository(
        private val delayUntilReleased: Boolean = false,
    ) : AiAdviceRepository {
        private val releaseSignal = CompletableDeferred<Unit>()
        var calls = 0
            private set

        override suspend fun generateAdvice(
            config: AiProviderConfig,
            summary: AiHealthSummary,
        ): AiAdviceResult {
            calls++
            if (delayUntilReleased) {
                releaseSignal.await()
            }
            return AiAdviceResult.Success(
                AiAdvice(
                    summary = "Stable",
                    recommendations = listOf("Rest"),
                    riskNote = "Low risk",
                    disclaimer = "Not diagnosis",
                ),
            )
        }

        fun release() {
            releaseSignal.complete(Unit)
        }
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
