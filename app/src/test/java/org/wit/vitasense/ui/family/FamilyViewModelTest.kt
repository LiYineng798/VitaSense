package org.wit.vitasense.ui.family

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyMember
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyRole
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.FamilyRepository

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
    val events = mutableListOf<String>()
    val cachedFamilyValue: Family?
        get() = cachedFamily.value

    override fun observeCachedFamily(): Flow<Family?> = cachedFamily

    override suspend fun refreshFamily(): FamilyResult {
        refreshCalls++
        events += "refreshFamily"
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
    ): FamilyResult = error("unused")

    override suspend fun sendSupport(
        familyId: Long,
        receiverUserId: Long,
        type: FamilySupportType,
    ): FamilyResult = error("unused")
}

private fun familyNamed(
    name: String,
    currentUserId: Long,
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
                ),
            ),
    )
