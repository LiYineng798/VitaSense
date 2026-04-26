package org.wit.vitasense.ui.common.chart

internal object ChartAxisLabelLayout {
    fun clampedCenterX(
        preferredCenterX: Float,
        labelWidth: Float,
        minX: Float,
        maxX: Float,
    ): Float {
        val availableWidth = maxX - minX
        if (availableWidth <= 0f) return preferredCenterX
        if (labelWidth >= availableWidth) {
            return (minX + maxX) / 2f
        }

        val halfWidth = labelWidth / 2f
        return preferredCenterX.coerceIn(minX + halfWidth, maxX - halfWidth)
    }
}
