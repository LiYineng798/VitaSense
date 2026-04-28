package org.wit.vitasense.ui.dashboard

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.wit.vitasense.ui.common.chart.TrendChartModel

data class DashboardTrendPageModel(
    val title: String,
    val chartModel: TrendChartModel,
)

data class DashboardScreenState(
    val totalScore: String = "--",
    val trendPages: List<DashboardTrendPageModel> = listOf(
        DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty),
    ),
    val showTrendDots: Boolean = false,
    val isSignedIn: Boolean = false,
    val authPrompt: String = "Tap to sign in",
    val authInitial: String = "?",
    val todayLabel: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val greeting: String = "Take a moment today to check in on your recovery.",
    val latestHeartRate: String = "--",
    val restingHeartRate: String = "--",
    val sleepDuration: String = "--",
    val hrv: String = "--",
    val summary: String = "No data yet. Import demo data from Profile to get started.",
    val riskLabel: String = "Not Assessed",
    val riskDescription: String = "No assessment result yet",
    val anomalyReminder: String = "After you import data, this area will highlight single-day and continuous anomalies.",
    val showEmptyState: Boolean = true,
)
