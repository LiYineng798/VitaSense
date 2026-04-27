package org.wit.vitasense.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

class SettingsViewModel(
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val _demoBundles = MutableStateFlow<List<DemoBundleInfo>>(emptyList())
    val demoBundles: StateFlow<List<DemoBundleInfo>> = _demoBundles.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.observeThemeMode()
            .stateIn(
                scope = modelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.LIGHT,
            )

    val themeFamily: StateFlow<ThemeFamily> =
        settingsRepository.observeThemeFamily()
            .stateIn(
                scope = modelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeFamily.DEFAULT,
            )

    init {
        modelScope.launch {
            _demoBundles.value = healthRepository.getAvailableDemoBundles()
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

    fun setTheme(mode: ThemeMode) {
        setThemeMode(mode)
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
}
