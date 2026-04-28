package org.wit.vitasense.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository

class DashboardViewModel(
    healthRepository: HealthRepository,
    authRepository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope

    val state: StateFlow<DashboardScreenState> =
        combine(
            healthRepository.observeSummaries(7),
            healthRepository.observeLatestRisk(),
            authRepository.observeCurrentUser(),
        ) { summaries, latestRisk, currentUser ->
            DashboardHomeUiMapper.build(
                summaries = summaries,
                latestRisk = latestRisk,
                currentUser = currentUser,
            )
        }.stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardScreenState(),
        )
}
