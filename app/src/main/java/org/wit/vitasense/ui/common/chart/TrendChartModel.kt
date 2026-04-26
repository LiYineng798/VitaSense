package org.wit.vitasense.ui.common.chart

enum class TrendChartTone {
    SOFT,
    CALM,
    EMPHASIZED,
}

sealed interface TrendChartModel {
    val tone: TrendChartTone
    val minValue: Float
    val maxValue: Float
    val selectionIndex: Int

    data object Empty : TrendChartModel {
        override val tone: TrendChartTone = TrendChartTone.SOFT
        override val minValue: Float = 0f
        override val maxValue: Float = 1f
        override val selectionIndex: Int = -1
    }

    data class Line(
        override val tone: TrendChartTone,
        override val minValue: Float,
        override val maxValue: Float,
        override val selectionIndex: Int,
        val windowSizeDays: Int,
        val entries: List<LineEntry>,
    ) : TrendChartModel
}

data class LineEntry(
    val axisLabel: String,
    val detailLabel: String,
    val value: Float,
    val valueText: String,
    val highlighted: Boolean,
)
