package org.wit.vitasense.ui.common.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAxisLabelLayoutTest {
    @Test
    fun clamps_left_edge_label_inside_bounds() {
        val clamped =
            ChartAxisLabelLayout.clampedCenterX(
                preferredCenterX = 20f,
                labelWidth = 40f,
                minX = 12f,
                maxX = 188f,
            )

        assertEquals(32f, clamped)
    }

    @Test
    fun clamps_right_edge_label_inside_bounds() {
        val clamped =
            ChartAxisLabelLayout.clampedCenterX(
                preferredCenterX = 180f,
                labelWidth = 40f,
                minX = 12f,
                maxX = 188f,
            )

        assertEquals(168f, clamped)
    }

    @Test
    fun keeps_middle_label_position_when_it_already_fits() {
        val clamped =
            ChartAxisLabelLayout.clampedCenterX(
                preferredCenterX = 100f,
                labelWidth = 40f,
                minX = 12f,
                maxX = 188f,
            )

        assertEquals(100f, clamped)
    }
}
