package org.wit.vitasense.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.wit.vitasense.repository.HealthRepository

class DashboardViewModel(
    healthRepository: HealthRepository,
) : ViewModel() {
    val state: StateFlow<DashboardScreenState> =
        combine(
            healthRepository.observeSummaries(7),
            healthRepository.observeLatestRisk(),
        ) { summaries, latestRisk ->
            DashboardHomeUiMapper.build(
                summaries = summaries,
                latestRisk = latestRisk,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardScreenState(),
        )
}
