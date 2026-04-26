package org.wit.vitasense.ui.trends

import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.chart.TrendChartModel

data class TrendSummaryItem(
    val date: String,
    val sleepMinutes: Int?,
    val rmssd: Double?,
    val avgHeartRate: Double?,
    val restingHeartRate: Double? = null,
    val baselineRmssd: Double? = null,
    val baselineAvgHeartRate: Double? = null,
    val anomalyFlags: Set<AnomalyFlag> = emptySet(),
    val summaryText: String = "",
)

data class TrendDaySnapshot(
    val date: String,
    val sleepHours: Float,
    val sleepScore: Int,
    val averageHeartRate: Float,
    val restingHeartRate: Float,
    val hrv: Float,
    val recoveryScore: Float,
    val anomalyFlags: Set<AnomalyFlag>,
    val anomalyLabel: String?,
    val summaryText: String,
) {
    val anomalyCount: Int
        get() = anomalyFlags.size
}

enum class TrendDirection {
    UP,
    DOWN,
    STABLE,
}

data class MiniTrendSeriesModel(
    val title: String,
    val values: List<Float>,
    val latestValueText: String,
    val trendDirection: TrendDirection,
)

data class WeeklyDetailCardModel(
    val id: String,
    val dayLabel: String,
    val dateLabel: String,
    val sleepHoursText: String,
    val sleepScore: Int,
    val hrvText: String,
    val hrvDeltaText: String,
    val hrvTrend: TrendDirection,
    val heartRateText: String,
    val restingHeartRateText: String,
    val hasAnomaly: Boolean,
    val anomalyLabel: String,
    val recoveryScore: Float,
    val summaryText: String,
)

data class WeeklyOverviewModel(
    val cards: List<WeeklyDetailCardModel>,
    val trendSeries: List<MiniTrendSeriesModel>,
)

data class MonthlyTrendPointModel(
    val dayLabel: String,
    val rawHrv: Float,
    val smoothedHrv: Float,
    val averageHeartRate: Float,
    val sleepHours: Float,
    val anomalyCount: Int,
)

data class WeeklyAggregateModel(
    val label: String,
    val averageSleepHours: Float,
    val averageHrv: Float,
    val averageHeartRate: Float,
    val anomalyCount: Int,
)

data class RecoveryHeatCellModel(
    val dayLabel: String,
    val intensity: Float,
    val hasAnomaly: Boolean,
)

data class MonthlyInsightCardModel(
    val title: String,
    val valueText: String,
    val deltaText: String,
    val trendDirection: TrendDirection,
)

data class MonthlyInsightModel(
    val trendPoints: List<MonthlyTrendPointModel>,
    val weeklyAggregates: List<WeeklyAggregateModel>,
    val heatmapCells: List<RecoveryHeatCellModel>,
    val insightCards: List<MonthlyInsightCardModel>,
    val windowSizeDays: Int,
)

data class TrendsScreenState(
    val selectedRange: TimeRange = TimeRange.DAYS_7,
    val latestSelectedDate: String = "",
    val windowInsight: String = "No trend data yet.",
    val sleepSummary: String = "",
    val hrvSummary: String = "",
    val anomalySummary: String = "",
    val heartRateSummary: String = "",
    val sleepChartModel: TrendChartModel = TrendChartModel.Empty,
    val hrvChartModel: TrendChartModel = TrendChartModel.Empty,
    val anomalyChartModel: TrendChartModel = TrendChartModel.Empty,
    val heartRateChartModel: TrendChartModel = TrendChartModel.Empty,
    val weekOverview: WeeklyOverviewModel? = null,
    val monthOverview: MonthlyInsightModel? = null,
    val empty: Boolean = true,
)
