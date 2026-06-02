package org.wit.vitasense.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.aiErrorMessage
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.repository.AiAdviceRepository
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

class DashboardViewModel(
    private val healthRepository: HealthRepository,
    authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val aiAdviceRepository: AiAdviceRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val aiLoading = MutableStateFlow(false)
    private val aiErrorText = MutableStateFlow<String?>(null)

    val state: StateFlow<DashboardScreenState> =
        combine(
            healthRepository.observeSummaries(7),
            healthRepository.observeLatestRisk(),
            authRepository.observeCurrentUser(),
            settingsRepository.observeAiProviderConfig(),
            settingsRepository.observeLatestAiAdvice(),
            settingsRepository.observeLatestAiAdviceGeneratedAt(),
            aiLoading,
            aiErrorText,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val summaries = values[0] as List<DailyPhysiologySummaryEntity>
            val latestRisk = values[1] as RiskAssessmentRecordEntity?
            val currentUser = values[2] as AuthUser?
            val aiConfig = values[3] as AiProviderConfig
            val latestAiAdvice = values[4] as AiAdvice?
            val latestAiAdviceGeneratedAt = values[5] as Long?
            val isAiLoading = values[6] as Boolean
            val aiError = values[7] as String?
            DashboardHomeUiMapper.build(
                summaries = summaries,
                latestRisk = latestRisk,
                currentUser = currentUser,
                aiConfig = aiConfig,
                latestAiAdvice = latestAiAdvice,
                latestAiAdviceGeneratedAt = latestAiAdviceGeneratedAt,
                isAiLoading = isAiLoading,
                aiErrorText = aiError,
            )
        }.stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardScreenState(),
        )

    fun generateAiAdvice() {
        if (aiLoading.value) return
        modelScope.launch {
            val config = settingsRepository.getAiProviderConfig()
            if (!config.isComplete) {
                aiErrorText.value =
                    aiErrorMessage(
                        when {
                            config.apiKey.isBlank() -> "missing_api_key"
                            config.baseUrl.isBlank() -> "missing_base_url"
                            else -> "missing_model"
                        },
                    )
                return@launch
            }
            val latestSummary = healthRepository.observeSummaries(7).first().maxByOrNull { it.date }
            val latestRisk = healthRepository.observeLatestRisk().first()
            if (latestSummary == null) {
                aiErrorText.value = "Import health data before generating AI advice."
                return@launch
            }

            aiLoading.value = true
            aiErrorText.value = null
            val result =
                aiAdviceRepository.generateAdvice(
                    config = config,
                    summary =
                        AiHealthSummary(
                            date = latestSummary.date,
                            totalScore = latestRisk?.totalScore,
                            riskLevel = latestRisk?.riskLevel,
                            sleepMinutes = latestSummary.sleepDurationMinutes,
                            rmssd = latestSummary.rmssd,
                            restingHeartRate = latestSummary.restingHeartRate,
                            avgHeartRate = latestSummary.avgHeartRate,
                            anomalyFlags = latestSummary.anomalyFlags.split("|").filter { it.isNotBlank() },
                            ruleSuggestion = latestRisk?.suggestionText,
                        ),
                )
            when (result) {
                is AiAdviceResult.Success ->
                    settingsRepository.setLatestAiAdvice(result.advice, System.currentTimeMillis())
                is AiAdviceResult.Error ->
                    aiErrorText.value = aiErrorMessage(result.code)
            }
            aiLoading.value = false
        }
    }
}
