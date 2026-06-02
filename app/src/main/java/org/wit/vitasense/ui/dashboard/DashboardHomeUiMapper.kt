package org.wit.vitasense.ui.dashboard

import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.chart.TrendChartModel
import org.wit.vitasense.ui.trends.TrendChartMetric
import org.wit.vitasense.ui.trends.TrendChartModelFactory
import org.wit.vitasense.ui.trends.TrendSummaryItem

object DashboardHomeUiMapper {
    fun build(
        summaries: List<DailyPhysiologySummaryEntity>,
        latestRisk: RiskAssessmentRecordEntity?,
        currentUser: AuthUser?,
        aiConfig: AiProviderConfig = AiProviderConfig(),
        latestAiAdvice: AiAdvice? = null,
        latestAiAdviceGeneratedAt: Long? = null,
        isAiLoading: Boolean = false,
        aiErrorText: String? = null,
    ): DashboardScreenState {
        val isSignedIn = currentUser != null
        val authPrompt =
            currentUser?.fullName?.let { "Welcome, $it!" } ?: "Tap to sign in"
        val authInitial =
            currentUser?.fullName
                ?.trim()
                ?.firstOrNull()
                ?.uppercase() ?: "?"

        val trendItems =
            summaries
                .sortedBy { it.date }
                .takeLast(TimeRange.DAYS_7.days)
                .map { it.toTrendSummaryItem() }

        if (trendItems.isEmpty()) {
            return DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = listOf(DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty)),
                showTrendDots = false,
                isSignedIn = isSignedIn,
                authPrompt = authPrompt,
                authInitial = authInitial,
                aiAdvice = buildAiState(aiConfig, latestAiAdvice, isAiLoading, aiErrorText),
            )
        }

        val pages =
            listOf(
                DashboardTrendPageModel(
                    title = "Sleep",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.SLEEP),
                ),
                DashboardTrendPageModel(
                    title = "HRV",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.HRV),
                ),
                DashboardTrendPageModel(
                    title = "Heart Rate",
                    chartModel = TrendChartModelFactory.build(trendItems, TimeRange.DAYS_7, TrendChartMetric.HEART_RATE),
                ),
            )

        val hasRenderableTrend = pages.any { it.chartModel is TrendChartModel.Line }
        return if (!hasRenderableTrend) {
            DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = listOf(DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty)),
                showTrendDots = false,
                isSignedIn = isSignedIn,
                authPrompt = authPrompt,
                authInitial = authInitial,
                aiAdvice = buildAiState(aiConfig, latestAiAdvice, isAiLoading, aiErrorText),
            )
        } else {
            DashboardScreenState(
                totalScore = latestRisk?.totalScore?.toString() ?: "--",
                trendPages = pages,
                showTrendDots = true,
                isSignedIn = isSignedIn,
                authPrompt = authPrompt,
                authInitial = authInitial,
                aiAdvice = buildAiState(aiConfig, latestAiAdvice, isAiLoading, aiErrorText),
            )
        }
    }

    private fun buildAiState(
        aiConfig: AiProviderConfig,
        latestAiAdvice: AiAdvice?,
        isAiLoading: Boolean,
        aiErrorText: String?,
    ): DashboardAiAdviceState =
        when {
            aiConfig.apiKey.isBlank() -> DashboardAiAdviceState()
            isAiLoading ->
                DashboardAiAdviceState(
                    statusText = "Generating personalized advice...",
                    actionText = "Generating...",
                    showProgress = true,
                    canGenerate = false,
                    shouldOpenSettings = false,
                )
            latestAiAdvice != null ->
                DashboardAiAdviceState(
                    statusText = "Latest generated advice",
                    summary = latestAiAdvice.summary,
                    recommendations = latestAiAdvice.recommendations,
                    riskNote = latestAiAdvice.riskNote,
                    disclaimer = latestAiAdvice.disclaimer,
                    actionText = "Refresh advice",
                    canGenerate = true,
                    shouldOpenSettings = false,
                    errorText = aiErrorText,
                )
            else ->
                DashboardAiAdviceState(
                    statusText = "Generate advice from today's health data.",
                    actionText = "Generate advice",
                    canGenerate = true,
                    shouldOpenSettings = false,
                    errorText = aiErrorText,
                )
        }

    private fun DailyPhysiologySummaryEntity.toTrendSummaryItem() =
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
}
