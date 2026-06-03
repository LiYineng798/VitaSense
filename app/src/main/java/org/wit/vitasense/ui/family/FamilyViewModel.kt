package org.wit.vitasense.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.model.familyErrorMessage
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.FamilyRepository

class FamilyViewModel(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val loading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private var runningAction: Job? = null
    private var observedUserId: Long? = null

    val state: StateFlow<FamilyScreenState> =
        combine(
            authRepository.observeCurrentUser(),
            familyRepository.observeCachedFamily(),
            loading,
            errorMessage,
        ) { currentUser: AuthUser?, family: Family?, isLoading: Boolean, error: String? ->
            val visibleFamily =
                family?.takeIf { candidate ->
                    currentUser != null && candidate.members.any { member -> member.userId == currentUser.id }
                }
            FamilyUiMapper.build(
                currentUserId = currentUser?.id,
                isSignedIn = currentUser != null,
                family = visibleFamily,
                isLoading = isLoading,
                errorMessage = error,
            )
        }.stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FamilyScreenState(),
        )

    init {
        modelScope.launch {
            authRepository.observeCurrentUser()
                .map { user -> user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    errorMessage.value = null
                    runningAction?.cancelAndJoin()
                    loading.value = false
                    if ((observedUserId != userId && observedUserId != null) || userId == null) {
                        familyRepository.clearCache()
                    }
                    observedUserId = userId
                    if (userId != null) {
                        refresh()
                    }
                }
        }
    }

    fun refresh() {
        runFamilyAction {
            familyRepository.refreshFamily()
        }
    }

    fun createFamily(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            errorMessage.value = familyErrorMessage("invalid_family_name")
            return
        }
        runFamilyAction {
            familyRepository.createFamily(trimmedName)
        }
    }

    fun joinFamily(inviteCode: String) {
        val trimmedInviteCode = inviteCode.trim()
        if (trimmedInviteCode.isBlank()) {
            errorMessage.value = familyErrorMessage("invalid_invite_code")
            return
        }
        runFamilyAction {
            familyRepository.joinFamily(trimmedInviteCode)
        }
    }

    fun renameFamily(
        familyId: Long,
        name: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            errorMessage.value = familyErrorMessage("invalid_family_name")
            return
        }
        runFamilyAction {
            familyRepository.renameFamily(familyId, trimmedName)
        }
    }

    fun regenerateInviteCode(familyId: Long) {
        runFamilyAction {
            familyRepository.regenerateInviteCode(familyId)
        }
    }

    fun removeMember(
        familyId: Long,
        userId: Long,
    ) {
        runFamilyAction {
            familyRepository.removeMember(familyId, userId)
        }
    }

    fun leaveFamily(familyId: Long) {
        runFamilyAction {
            familyRepository.leaveFamily(familyId)
        }
    }

    fun sendSupport(
        familyId: Long,
        receiverUserId: Long,
        type: FamilySupportType,
    ) {
        runFamilyAction {
            familyRepository.sendSupport(familyId, receiverUserId, type)
        }
    }

    fun upsertStatus(
        familyId: Long,
        snapshot: FamilyStatusSnapshot,
    ) {
        runFamilyAction {
            familyRepository.upsertStatus(familyId, snapshot)
        }
    }

    private fun runFamilyAction(action: suspend () -> FamilyResult) {
        if (runningAction?.isActive == true) return
        loading.value = true
        runningAction =
            modelScope.launch {
                try {
                    applyResult(action())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    applyResult(
                        FamilyResult.Error(
                            code = "unknown",
                            message = familyErrorMessage("unknown"),
                        ),
                    )
                } finally {
                    loading.value = false
                }
            }
    }

    override fun onCleared() {
        runningAction = null
        super.onCleared()
    }

    private fun applyResult(result: FamilyResult) {
        errorMessage.value =
            when (result) {
                is FamilyResult.Success -> null
                is FamilyResult.Error -> result.message.ifBlank { familyErrorMessage(result.code) }
            }
    }
}
