package org.wit.vitasense.ui.navigation

object BottomTabIndicatorGeometry {
    fun insetBounds(
        bounds: IndicatorBounds,
        horizontalInsetPx: Float,
    ): IndicatorBounds {
        val width = (bounds.right - bounds.left).coerceAtLeast(0f)
        val safeInset = horizontalInsetPx.coerceAtLeast(0f).coerceAtMost(width / 2f)
        return IndicatorBounds(
            left = bounds.left + safeInset,
            right = bounds.right - safeInset,
        )
    }
}
