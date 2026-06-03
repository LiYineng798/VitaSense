package org.wit.vitasense.ui.family

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

private class FakeFamilyRepository : FamilyRepository {
    private val cachedFamily = MutableStateFlow<Family?>(null)
    var refreshCalls = 0
        private set
    var createCalls = 0
        private set
    var createdName = ""
        private set

    override fun observeCachedFamily(): Flow<Family?> = cachedFamily

    override suspend fun refreshFamily(): FamilyResult {
        refreshCalls++
        return FamilyResult.Success(cachedFamily.value)
    }

    override suspend fun createFamily(name: String): FamilyResult {
        createCalls++
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
