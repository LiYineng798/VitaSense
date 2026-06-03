package org.wit.vitasense.ui.profile

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
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

class ProfileViewModelTest {
    @Test
    fun combines_signed_in_user_with_theme_and_demo_data() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val user = AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")
        val viewModel =
            ProfileViewModel(
                authRepository = FakeProfileAuthRepository(user),
                healthRepository = FakeProfileHealthRepository(),
                settingsRepository = FakeProfileSettingsRepository(),
                scope = scope,
            )
        val collector =
            scope.launch {
                viewModel.state.collect {}
            }
        yield()

        assertEquals("Ava Stone", viewModel.state.value.user?.fullName)
        assertEquals(true, viewModel.state.value.isSignedIn)
        assertEquals(ThemeFamily.DEFAULT, viewModel.state.value.themeFamily)
        assertEquals(1, viewModel.state.value.demoBundles.size)

        collector.cancel()
        scope.coroutineContext[Job]?.cancel()
        Unit
    }
}

private class FakeProfileAuthRepository(
    user: AuthUser?,
) : AuthRepository {
    private val currentUser = MutableStateFlow(user)

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser

    override suspend fun getCurrentUser(): AuthUser? = currentUser.value

    override suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult = AuthResult.Error("unused")

    override suspend fun login(
        identifier: String,
        password: String,
    ): AuthResult = AuthResult.Error("unused")

    override suspend fun logout() {
        currentUser.value = null
    }
}

private class FakeProfileHealthRepository : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)

    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)

    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> = flowOf(null)

    override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> = flowOf(emptyList())

    override fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>> = flowOf(emptyList())

    override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> =
        listOf(
            DemoBundleInfo(
                id = "starter",
                title = "Starter Bundle",
                description = "Starter data",
            ),
        )

    override suspend fun importDemoBundle(bundleId: String): ImportOperationResult =
        ImportOperationResult(ImportStatus.SUCCESS, "Imported", 0, 0, 0, 0)

    override suspend fun importRawJson(
        raw: String,
        sourceName: String,
    ): ImportOperationResult = ImportOperationResult(ImportStatus.SUCCESS, "Imported", 0, 0, 0, 0)

    override suspend fun clearAllData() = Unit
}

private class FakeProfileSettingsRepository : SettingsRepository {
    private val themeMode = MutableStateFlow(ThemeMode.LIGHT)
    private val themeFamily = MutableStateFlow(ThemeFamily.DEFAULT)
    private val authBaseUrl = MutableStateFlow("")
    private val authToken = MutableStateFlow("")
    private val currentUserJson = MutableStateFlow("")
    private val currentUserId = MutableStateFlow<Long?>(1L)
    private val aiConfig = MutableStateFlow(AiProviderConfig())
    private val latestAiAdvice = MutableStateFlow<AiAdvice?>(null)
    private val latestAiAdviceGeneratedAt = MutableStateFlow<Long?>(null)
    val lastSyncAt = MutableStateFlow<Long?>(null)
    val syncStatus = MutableStateFlow("idle")
    val syncError = MutableStateFlow("")

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode

    override fun observeThemeFamily(): Flow<ThemeFamily> = themeFamily

    override fun observeAuthBaseUrl(): Flow<String> = authBaseUrl

    override fun observeAuthToken(): Flow<String> = authToken

    override fun observeCurrentUserJson(): Flow<String> = currentUserJson

    override fun observeCurrentUserId(): Flow<Long?> = currentUserId

    override fun observeAiProviderConfig(): Flow<AiProviderConfig> = aiConfig

    override fun observeLatestAiAdvice(): Flow<AiAdvice?> = latestAiAdvice

    override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> = latestAiAdviceGeneratedAt

    override fun observeLastSyncAt(): Flow<Long?> = lastSyncAt

    override fun observeSyncStatus(): Flow<String> = syncStatus

    override fun observeSyncError(): Flow<String> = syncError

    override suspend fun getThemeMode(): ThemeMode = themeMode.value

    override suspend fun getThemeFamily(): ThemeFamily = themeFamily.value

    override suspend fun getAuthBaseUrl(): String = authBaseUrl.value

    override suspend fun getAuthToken(): String = authToken.value

    override suspend fun getCurrentUserJson(): String = currentUserJson.value

    override suspend fun getCurrentUserId(): Long? = currentUserId.value

    override suspend fun getAiProviderConfig(): AiProviderConfig = aiConfig.value

    override suspend fun getLatestAiAdvice(): AiAdvice? = latestAiAdvice.value

    override suspend fun getLatestAiAdviceGeneratedAt(): Long? = latestAiAdviceGeneratedAt.value

    override suspend fun getLastSyncAt(): Long? = lastSyncAt.value

    override suspend fun getSyncStatus(): String = syncStatus.value

    override suspend fun getSyncError(): String = syncError.value

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
