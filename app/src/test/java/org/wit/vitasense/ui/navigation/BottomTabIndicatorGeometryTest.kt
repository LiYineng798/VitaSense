package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomTabIndicatorGeometryTest {
    @Test
    fun inset_bounds_shrinks_both_sides_evenly() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 0f, right = 76f),
                horizontalInsetPx = 10f,
            )

        assertEquals(10f, result.left, 0.001f)
        assertEquals(66f, result.right, 0.001f)
    }

    @Test
    fun inset_bounds_clamps_at_half_width() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 20f, right = 60f),
                horizontalInsetPx = 40f,
            )

        assertEquals(40f, result.left, 0.001f)
        assertEquals(40f, result.right, 0.001f)
    }

    @Test
    fun inset_bounds_treats_negative_inset_as_zero() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 12f, right = 88f),
                horizontalInsetPx = -6f,
            )

        assertEquals(12f, result.left, 0.001f)
        assertEquals(88f, result.right, 0.001f)
    }
}
