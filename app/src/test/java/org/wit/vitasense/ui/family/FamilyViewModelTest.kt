package org.wit.vitasense.ui.family

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyMember
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyRole
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.FamilyRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository

class FamilyViewModelTest {
    @Test
    fun exposes_signed_out_state_when_user_is_absent() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository()
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(null),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()

            assertEquals(FamilyScreenMode.SIGNED_OUT, viewModel.state.value.mode)
            assertEquals(0, familyRepository.refreshCalls)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun create_family_refreshes_state_from_repository_result() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository()
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            viewModel.createFamily("Home")
            yield()

            assertEquals(1, familyRepository.createCalls)
            assertEquals("Home", familyRepository.createdName)
            assertEquals(FamilyScreenMode.JOINED_FAMILY, viewModel.state.value.mode)
            assertEquals("Home", viewModel.state.value.familyName)
            assertEquals(null, viewModel.state.value.errorMessage)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun refreshes_family_when_auth_user_transitions_from_null_to_user() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val authRepository = FakeAuthRepository(null)
            val familyRepository = FakeFamilyRepository()
            val viewModel =
                FamilyViewModel(
                    authRepository = authRepository,
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            authRepository.setCurrentUser(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"))
            yield()

            assertEquals(1, familyRepository.refreshCalls)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun signed_out_state_clears_cached_family_when_auth_emits_null() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val authRepository =
                FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"))
            val familyRepository = FakeFamilyRepository()
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val viewModel =
                FamilyViewModel(
                    authRepository = authRepository,
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            authRepository.setCurrentUser(null)
            yield()

            assertEquals(1, familyRepository.clearCacheCalls)
            assertEquals(null, familyRepository.cachedFamilyValue)
            assertEquals(FamilyScreenMode.SIGNED_OUT, viewModel.state.value.mode)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun user_change_clears_cached_family_before_failed_refresh_renders_old_family() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val authRepository =
                FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"))
            val familyRepository =
                FakeFamilyRepository(
                    refreshResult = FamilyResult.Error("network", "Family service is unavailable right now."),
                )
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val viewModel =
                FamilyViewModel(
                    authRepository = authRepository,
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            familyRepository.clearEvents()
            authRepository.setCurrentUser(AuthUser(2, "Ben Stone", "ben@example.com", "ben", "2001-03-04"))
            yield()

            assertEquals(1, familyRepository.clearCacheCalls)
            assertEquals(2, familyRepository.refreshCalls)
            assertEquals(listOf("clearCache", "refreshFamily"), familyRepository.events)
            assertEquals(FamilyScreenMode.NO_FAMILY, viewModel.state.value.mode)
            assertEquals("", viewModel.state.value.familyName)
            assertEquals(null, familyRepository.cachedFamilyValue)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun signed_in_user_does_not_render_cached_family_that_does_not_include_them() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository =
                FakeFamilyRepository(
                    refreshResult = FamilyResult.Error("network", "Family service is unavailable right now."),
                )
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(2, "Ben Stone", "ben@example.com", "ben", "2001-03-04")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()

            assertEquals(FamilyScreenMode.NO_FAMILY, viewModel.state.value.mode)
            assertEquals("", viewModel.state.value.familyName)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun blank_create_family_sets_validation_error() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = FakeFamilyRepository(),
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            viewModel.createFamily("   ")
            yield()

            assertEquals("Family name is required.", viewModel.state.value.errorMessage)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun repository_error_sets_error_message_and_clears_loading() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository =
                FakeFamilyRepository(
                    createResult = FamilyResult.Error("server", "Family service is unavailable right now."),
                )
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            viewModel.createFamily("Home")
            yield()

            assertEquals("Family service is unavailable right now.", viewModel.state.value.errorMessage)
            assertEquals(false, viewModel.state.value.isLoading)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun repository_exception_sets_fallback_error_and_clears_loading() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository(createException = IllegalStateException("boom"))
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            viewModel.createFamily("Home")
            yield()

            assertEquals("Unable to update Family right now.", viewModel.state.value.errorMessage)
            assertEquals(false, viewModel.state.value.isLoading)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun rapid_duplicate_create_family_calls_only_start_one_action() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository(createDelayMillis = 1_000)
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            viewModel.createFamily("Home")
            viewModel.createFamily("Home")
            yield()

            assertEquals(1, familyRepository.createCalls)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun sync_today_status_uploads_latest_mood_snapshot_for_joined_family() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository()
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val moodRepository =
                FakeMoodRepository(
                    latestMood =
                        MoodRecordEntity(
                            date = "2026-06-03",
                            moodType = "CALM",
                            moodGroup = "positive",
                            note = "steady",
                            createdAt = 1770000000000,
                        ),
                )
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = moodRepository,
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            viewModel.syncTodayStatus("2026-06-03")
            yield()

            assertEquals("2026-06-03", moodRepository.latestMoodDate)
            assertEquals(20, familyRepository.lastStatusFamilyId)
            assertEquals("CALM", familyRepository.lastStatusSnapshot?.moodType)
            assertEquals("steady", familyRepository.lastStatusSnapshot?.moodNote)
            assertEquals("Checked in today", familyRepository.lastStatusSnapshot?.statusLabel)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun sync_today_status_runs_after_initial_refresh_finishes() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val refreshGate = CompletableDeferred<Unit>()
            val familyRepository = FakeFamilyRepository(refreshGate = refreshGate)
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val moodRepository =
                FakeMoodRepository(
                    latestMood =
                        MoodRecordEntity(
                            date = "2026-06-03",
                            moodType = "HAPPY",
                            moodGroup = "positive",
                            note = "better",
                            createdAt = 1770000000000,
                        ),
                )
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = moodRepository,
                    healthRepository = FakeHealthRepository(),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            viewModel.syncTodayStatus("2026-06-03")
            yield()
            assertEquals(null, familyRepository.lastStatusSnapshot)

            refreshGate.complete(Unit)
            yield()

            assertEquals("HAPPY", familyRepository.lastStatusSnapshot?.moodType)
            assertEquals("better", familyRepository.lastStatusSnapshot?.moodNote)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun enabling_health_score_sharing_syncs_latest_score_summary() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository()
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(risk(totalScore = 82, date = "2026-06-03")),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            viewModel.setShareHealthScore(true)
            yield()

            assertEquals(true, familyRepository.lastStatusSnapshot?.shareHealthScore)
            assertEquals(82, familyRepository.lastStatusSnapshot?.healthScore)
            assertEquals("Stable", familyRepository.lastStatusSnapshot?.healthScoreLabel)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    @Test
    fun disabling_health_score_sharing_syncs_hidden_score_state() =
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            val familyRepository = FakeFamilyRepository()
            familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1, shareHealthScore = true))
            val viewModel =
                FamilyViewModel(
                    authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                    familyRepository = familyRepository,
                    moodRepository = FakeMoodRepository(),
                    healthRepository = FakeHealthRepository(risk(totalScore = 91, date = "2026-06-03")),
                    scope = scope,
                )
            val collector = collectState(viewModel, scope)

            yield()
            viewModel.setShareHealthScore(false)
            yield()

            assertEquals(false, familyRepository.lastStatusSnapshot?.shareHealthScore)
            assertEquals(null, familyRepository.lastStatusSnapshot?.healthScore)
            assertEquals(null, familyRepository.lastStatusSnapshot?.healthScoreLabel)

            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
            Unit
        }

    private fun collectState(
        viewModel: FamilyViewModel,
        scope: CoroutineScope,
    ) = scope.launch {
        viewModel.state.collect {}
    }
}

private class FakeAuthRepository(
    user: AuthUser?,
) : AuthRepository {
    private val currentUser = MutableStateFlow(user)

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser

    override suspend fun getCurrentUser(): AuthUser? = currentUser.value

    fun setCurrentUser(user: AuthUser?) {
        currentUser.value = user
    }

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

private class FakeFamilyRepository(
    private val refreshResult: FamilyResult? = null,
    private val createResult: FamilyResult? = null,
    private val createException: Exception? = null,
    private val createDelayMillis: Long = 0,
    private val refreshGate: CompletableDeferred<Unit>? = null,
) : FamilyRepository {
    private val cachedFamily = MutableStateFlow<Family?>(null)
    var refreshCalls = 0
        private set
    var createCalls = 0
        private set
    var clearCacheCalls = 0
        private set
    var createdName = ""
        private set
    var lastStatusFamilyId: Long? = null
        private set
    var lastStatusSnapshot: FamilyStatusSnapshot? = null
        private set
    val events = mutableListOf<String>()
    val cachedFamilyValue: Family?
        get() = cachedFamily.value

    override fun observeCachedFamily(): Flow<Family?> = cachedFamily

    override suspend fun refreshFamily(): FamilyResult {
        refreshCalls++
        events += "refreshFamily"
        refreshGate?.await()
        refreshResult?.let { return it }
        return FamilyResult.Success(cachedFamily.value)
    }

    override fun clearCache() {
        clearCacheCalls++
        events += "clearCache"
        cachedFamily.value = null
    }

    override suspend fun createFamily(name: String): FamilyResult {
        createCalls++
        if (createDelayMillis > 0) delay(createDelayMillis)
        createException?.let { throw it }
        createResult?.let { return it }
        createdName = name
        val family =
            Family(
                id = 20,
                name = name,
                inviteCode = "JOIN20",
                currentUserRole = FamilyRole.OWNER,
                members =
                    listOf(
                        FamilyMember(
                            userId = 1,
                            fullName = "Ava Stone",
                            username = "ava",
                            role = FamilyRole.OWNER,
                            moodType = null,
                            moodNote = null,
                            statusLabel = "No check-in yet",
                            statusUpdatedAt = null,
                            supportCountToday = 0,
                            latestSupportType = null,
                            latestSupportSentAt = null,
                        ),
                    ),
            )
        cachedFamily.value = family
        return FamilyResult.Success(family)
    }

    fun seedFamily(family: Family) {
        cachedFamily.value = family
    }

    fun clearEvents() {
        events.clear()
    }

    override suspend fun joinFamily(inviteCode: String): FamilyResult = error("unused")

    override suspend fun renameFamily(
        familyId: Long,
        name: String,
    ): FamilyResult = error("unused")

    override suspend fun regenerateInviteCode(familyId: Long): FamilyResult = error("unused")

    override suspend fun removeMember(
        familyId: Long,
        userId: Long,
    ): FamilyResult = error("unused")

    override suspend fun leaveFamily(familyId: Long): FamilyResult = error("unused")

    override suspend fun upsertStatus(
        familyId: Long,
        snapshot: FamilyStatusSnapshot,
    ): FamilyResult {
        lastStatusFamilyId = familyId
        lastStatusSnapshot = snapshot
        return FamilyResult.Success(cachedFamily.value)
    }

    override suspend fun sendSupport(
        familyId: Long,
        receiverUserId: Long,
        type: FamilySupportType,
    ): FamilyResult = error("unused")
}

private class FakeMoodRepository(
    private val latestMood: MoodRecordEntity? = null,
) : MoodRepository {
    var latestMoodDate: String? = null
        private set

    override fun observeMoodRecords(filter: MoodFilter): Flow<List<MoodRecordEntity>> = MutableStateFlow(emptyList())

    override suspend fun addMood(
        date: String,
        moodType: MoodType,
        note: String?,
    ) = Unit

    override suspend fun deleteMood(id: Long) = Unit

    override suspend fun getLatestMoodForDate(date: String): MoodRecordEntity? {
        latestMoodDate = date
        return latestMood
    }
}

private class FakeHealthRepository(
    private val latestRisk: RiskAssessmentRecordEntity? = null,
) : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)

    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)

    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> = flowOf(latestRisk)

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

private fun familyNamed(
    name: String,
    currentUserId: Long,
    shareHealthScore: Boolean = false,
): Family =
    Family(
        id = 20,
        name = name,
        inviteCode = "JOIN20",
        currentUserRole = FamilyRole.OWNER,
        members =
            listOf(
                FamilyMember(
                    userId = currentUserId,
                    fullName = "Ava Stone",
                    username = "ava",
                    role = FamilyRole.OWNER,
                    moodType = null,
                    moodNote = null,
                    statusLabel = "No check-in yet",
                    statusUpdatedAt = null,
                    supportCountToday = 0,
                    latestSupportType = null,
                    latestSupportSentAt = null,
                    shareHealthScore = shareHealthScore,
                    healthScore = if (shareHealthScore) 82 else null,
                    healthScoreLabel = if (shareHealthScore) "Stable" else null,
                    healthScoreUpdatedAt = if (shareHealthScore) 1770000000000 else null,
                ),
            ),
    )

private fun risk(
    totalScore: Int,
    date: String,
) = RiskAssessmentRecordEntity(
    date = date,
    totalScore = totalScore,
    riskLevel = "low",
    sleepScore = 30,
    hrvScore = 25,
    restingHrScore = 15,
    avgHrScore = totalScore - 70,
    explanation = "unused",
    suggestionText = "unused",
)
