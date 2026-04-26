package org.wit.vitasense.ui.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.wit.vitasense.repository.HealthRepository

data class AssessmentScreenState(
    val totalScore: String = "--",
    val riskLabel: String = "Not Assessed",
    val scoreBreakdown: String = "No score data yet",
    val explanation: String = "Import demo data first.",
    val suggestion: String = "This app provides trend review and stress-management support.",
    val empty: Boolean = true,
)

class AssessmentViewModel(
    healthRepository: HealthRepository,
) : ViewModel() {
    val state: StateFlow<AssessmentScreenState> =
        combine(
            healthRepository.observeLatestSummary(),
            healthRepository.observeLatestRisk(),
        ) { summary, risk ->
            if (summary == null || risk == null) {
                AssessmentScreenState()
            } else {
                AssessmentScreenState(
                    totalScore = risk.totalScore.toString(),
                    riskLabel = risk.riskLevel.toRiskLabel(),
                    scoreBreakdown = "Sleep ${risk.sleepScore} / HRV ${risk.hrvScore} / Resting ${risk.restingHrScore} / Average ${risk.avgHrScore}",
                    explanation = risk.explanation,
                    suggestion = risk.suggestionText,
                    empty = false,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AssessmentScreenState(),
        )

    private fun String.toRiskLabel(): String =
        when (lowercase()) {
            "low" -> "Low Risk"
            "medium" -> "Medium Risk"
            "high" -> "High Risk"
            else -> "Not Assessed"
        }
}
