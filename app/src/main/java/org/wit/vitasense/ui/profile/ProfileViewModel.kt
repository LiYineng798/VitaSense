package org.wit.vitasense.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

data class ProfileScreenState(
    val user: AuthUser? = null,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val themeFamily: ThemeFamily = ThemeFamily.DEFAULT,
    val demoBundles: List<DemoBundleInfo> = emptyList(),
    val isSignedIn: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val demoBundles = MutableStateFlow<List<DemoBundleInfo>>(emptyList())
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val state: StateFlow<ProfileScreenState> =
        combine(
            authRepository.observeCurrentUser(),
            settingsRepository.observeThemeMode(),
            settingsRepository.observeThemeFamily(),
            demoBundles,
        ) { user, themeMode, themeFamily, bundles ->
            ProfileScreenState(
                user = user,
                themeMode = themeMode,
                themeFamily = themeFamily,
                demoBundles = bundles,
                isSignedIn = user != null,
            )
        }.stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileScreenState(),
        )

    init {
        modelScope.launch {
            demoBundles.value = healthRepository.getAvailableDemoBundles()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        modelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setThemeFamily(family: ThemeFamily) {
        modelScope.launch {
            settingsRepository.setThemeFamily(family)
        }
    }

    fun importDemo(bundleId: String) {
        modelScope.launch {
            val result = healthRepository.importDemoBundle(bundleId)
            _events.emit(UiEvent.Message(result.message))
        }
    }

    fun clearAllData() {
        modelScope.launch {
            healthRepository.clearAllData()
            _events.emit(UiEvent.Message("All local data has been deleted."))
        }
    }

    fun logout() {
        modelScope.launch {
            authRepository.logout()
        }
    }
}
