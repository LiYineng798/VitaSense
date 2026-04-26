package org.wit.vitasense.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

class SettingsViewModel(
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _demoBundles = MutableStateFlow<List<DemoBundleInfo>>(emptyList())
    val demoBundles: StateFlow<List<DemoBundleInfo>> = _demoBundles.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.observeThemeMode()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.LIGHT,
            )

    init {
        viewModelScope.launch {
            _demoBundles.value = healthRepository.getAvailableDemoBundles()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun importDemo(bundleId: String) {
        viewModelScope.launch {
            val result = healthRepository.importDemoBundle(bundleId)
            _events.emit(UiEvent.Message(result.message))
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            healthRepository.clearAllData()
            _events.emit(UiEvent.Message("All local data has been deleted."))
        }
    }
}
