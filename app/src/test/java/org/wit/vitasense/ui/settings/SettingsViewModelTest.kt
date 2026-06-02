package org.wit.vitasense.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

class SettingsViewModelTest {
    @Test
    fun exposes_theme_family_and_persists_family_changes() {
        runBlocking {
            val repository = FakeSettingsRepository()
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val viewModel = SettingsViewModel(FakeHealthRepository(), repository, scope)
            val collector =
                scope.launch {
                    viewModel.themeFamily.collect {}
                }

            repository.themeFamily.value = ThemeFamily.OLIVE_EMBER
            yield()

            assertEquals(ThemeFamily.OLIVE_EMBER, viewModel.themeFamily.value)

            viewModel.setThemeFamily(ThemeFamily.SUNLIT_MEADOW)
            yield()

            assertEquals(ThemeFamily.SUNLIT_MEADOW, repository.themeFamily.value)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeFamily = MutableStateFlow(ThemeFamily.DEFAULT)
    val authBaseUrl = MutableStateFlow("")
    val authToken = MutableStateFlow("")
    val currentUserJson = MutableStateFlow("")
    val currentUserId = MutableStateFlow<Long?>(null)
    val aiConfig = MutableStateFlow(AiProviderConfig())
    val latestAiAdvice = MutableStateFlow<AiAdvice?>(null)
    val latestAiAdviceGeneratedAt = MutableStateFlow<Long?>(null)

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode

    override fun observeThemeFamily(): Flow<ThemeFamily> = themeFamily

    override fun observeAuthBaseUrl(): Flow<String> = authBaseUrl

    override fun observeAuthToken(): Flow<String> = authToken

    override fun observeCurrentUserJson(): Flow<String> = currentUserJson

    override fun observeCurrentUserId(): Flow<Long?> = currentUserId

    override fun observeAiProviderConfig(): Flow<AiProviderConfig> = aiConfig

    override fun observeLatestAiAdvice(): Flow<AiAdvice?> = latestAiAdvice

    override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> = latestAiAdviceGeneratedAt

    override suspend fun getThemeMode(): ThemeMode = themeMode.value

    override suspend fun getThemeFamily(): ThemeFamily = themeFamily.value

    override suspend fun getAuthBaseUrl(): String = authBaseUrl.value

    override suspend fun getAuthToken(): String = authToken.value

    override suspend fun getCurrentUserJson(): String = currentUserJson.value

    override suspend fun getCurrentUserId(): Long? = currentUserId.value

    override suspend fun getAiProviderConfig(): AiProviderConfig = aiConfig.value

    override suspend fun getLatestAiAdvice(): AiAdvice? = latestAiAdvice.value

    override suspend fun getLatestAiAdviceGeneratedAt(): Long? = latestAiAdviceGeneratedAt.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setThemeFamily(family: ThemeFamily) {
        themeFamily.value = family
    }

    override suspend fun setAuthBaseUrl(baseUrl: String) {
        authBaseUrl.value = baseUrl
    }

    override suspend fun setAuthToken(token: String?) {
        authToken.value = token.orEmpty()
    }

    override suspend fun setCurrentUserJson(userJson: String?) {
        currentUserJson.value = userJson.orEmpty()
    }

    override suspend fun setCurrentUserId(userId: Long?) {
        currentUserId.value = userId
    }

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
}

private class FakeHealthRepository : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)

    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)

    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> = flowOf(null)

    override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> = flowOf(emptyList())

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
