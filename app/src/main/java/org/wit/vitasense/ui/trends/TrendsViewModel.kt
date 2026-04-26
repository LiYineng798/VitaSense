package org.wit.vitasense.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.repository.HealthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val healthRepository: HealthRepository,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(TimeRange.DAYS_7)

    val state: StateFlow<TrendsScreenState> =
        selectedRange.flatMapLatest { range ->
            combine(
                healthRepository.observeSummaries(range.days),
                healthRepository.observeRisks(range.days),
            ) { summaries, risks ->
                val summaryItems = summaries.map { it.toTrendSummaryItem() }
                TrendsUiMapper.buildState(summaryItems, range).copy(
                    windowInsight = buildWindowInsight(risks, range),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrendsScreenState(),
        )

    fun selectRange(range: TimeRange) {
        selectedRange.value = range
    }

    private fun DailyPhysiologySummaryEntity.toTrendSummaryItem(): TrendSummaryItem =
        TrendSummaryItem(
            date = date,
            sleepMinutes = sleepDurationMinutes,
            rmssd = rmssd,
            avgHeartRate = avgHeartRate,
            restingHeartRate = restingHeartRate,
            baselineRmssd = baselineRmssd,
            baselineAvgHeartRate = baselineAvgHeartRate,
            anomalyFlags = anomalyFlags.toAnomalyFlags(),
            summaryText = summaryText,
        )

    private fun String.toAnomalyFlags(): Set<AnomalyFlag> =
        split("|")
            .mapNotNull { raw ->
                raw.takeIf { it.isNotBlank() }?.let {
                    runCatching { AnomalyFlag.valueOf(it) }.getOrNull()
                }
            }.toSet()

    private fun buildWindowInsight(
        risks: List<RiskAssessmentRecordEntity>,
        range: TimeRange,
    ): String {
        if (risks.isEmpty()) {
            return "No risk insight for the last ${range.days} days yet."
        }

        val latestLabel = risks.last().riskLevel.toRiskLabel()
        val highCount = risks.count { it.riskLevel.equals("high", ignoreCase = true) }
        val mediumCount = risks.count { it.riskLevel.equals("medium", ignoreCase = true) }

        return when {
            highCount > 0 -> "$highCount high-risk day(s) in the last ${risks.size} days. Latest: $latestLabel."
            mediumCount > 0 -> "Recovery fluctuated in the last ${risks.size} days. Latest: $latestLabel."
            else -> "Recent ${risks.size}-day trend is stable. Latest: $latestLabel."
        }
    }

    private fun String.toRiskLabel(): String =
        when (lowercase()) {
            "low" -> "Low"
            "medium" -> "Medium"
            "high" -> "High"
            else -> "Unknown"
        }
}
