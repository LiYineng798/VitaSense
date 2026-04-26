package org.wit.vitasense.ui.common.chart

import kotlin.math.max

class ChartScaleCalculator {
    fun paddedRange(
        values: List<Float>,
        floorAtZero: Boolean = false,
        paddingFraction: Float = 0.14f,
    ): ClosedFloatingPointRange<Float> {
        if (values.isEmpty()) {
            return 0f..1f
        }

        val minValue = values.minOrNull() ?: 0f
        val maxValue = values.maxOrNull() ?: 0f
        val span = maxValue - minValue
        val padding = if (span > 0f) span * paddingFraction else max(1f, maxValue * paddingFraction)
        val start = if (floorAtZero) max(0f, minValue - padding) else minValue - padding
        val end = max(maxValue + padding, start + 1f)
        return start..end
    }

    fun mapValue(
        value: Float,
        minValue: Float,
        maxValue: Float,
        top: Float,
        bottom: Float,
    ): Float {
        val safeRange = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val ratio = (value - minValue) / safeRange
        return bottom - (bottom - top) * ratio
    }
}
