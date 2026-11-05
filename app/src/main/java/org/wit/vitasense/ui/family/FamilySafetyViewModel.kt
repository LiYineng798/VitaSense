package org.wit.vitasense.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.wit.vitasense.data.repository.FamilySafetyRepository
import org.wit.vitasense.db.entity.TrustedContactEntity
import org.wit.vitasense.model.FamilyResult

data class FamilySafetyState(val contacts: List<TrustedContactEntity> = emptyList(), val privacy: Map<String,Boolean> = emptyMap(), val history: List<String> = emptyList(), val loading: Boolean = false, val message: String = "Contacts stay on this device. No calls or messages are sent automatically.")
class FamilySafetyViewModel(private val repository: FamilySafetyRepository) : ViewModel() {
    val state = MutableStateFlow(FamilySafetyState())
    init { viewModelScope.launch { repository.contacts.collect { state.value = state.value.copy(contacts = it) } }; refresh() }
    private fun run(action: suspend () -> String) { if (state.value.loading) return; viewModelScope.launch {
        state.value = state.value.copy(loading = true)
        val message = try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { e.message ?: "Unable to complete action. Try again." }
        state.value = state.value.copy(loading = false, message = message)
    } }
    fun refresh() = run { state.value = state.value.copy(privacy = listOf("mood","score","checkin").associateWith { repository.privacy(it) }); "Local preferences loaded. Publish sharing preferences to update or revoke what your family can see." }
    fun save(contact: TrustedContactEntity) = run { repository.save(contact); "Contact saved on this device." }
    fun delete(id: String) = run { repository.delete(id); "Contact deleted." }
    fun privacy(field: String, enabled: Boolean) = run {
        repository.setPrivacy(field,enabled)
        state.value = state.value.copy(privacy = state.value.privacy + (field to enabled))
        publishResult(repository.shareStatus())
    }
    fun share(checkIn: String? = null) = run {
        val result = repository.shareStatus(checkIn)
        if (result is FamilyResult.Success) state.value = state.value.copy(history = repository.history())
        publishResult(result)
    }
    fun history() = run { state.value = state.value.copy(history = repository.history()); "Check-in history refreshed (latest 100)." }
    private fun publishResult(result: FamilyResult): String = when (result) {
        is FamilyResult.Success -> "Family sharing updated."
        is FamilyResult.Error -> "Local preferences saved; server update not confirmed: ${result.message} Retry Publish sharing preferences."
    }
}
