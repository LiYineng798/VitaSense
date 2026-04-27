package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidIndicatorFrameCalculatorTest {
    private val planner = LiquidIndicatorMotionPlanner()
    private val calculator = LiquidIndicatorFrameCalculator()

    @Test
    fun moves_only_the_leading_edge_before_the_trailing_delay_on_rightward_motion() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 100f, right = 200f),
            )

        val bounds = calculator.boundsAt(spec, elapsedMs = 40L)

        assertEquals(0f, bounds.left)
        assertTrue(bounds.right > 100f)
    }

    @Test
    fun lands_exactly_on_the_final_target_at_the_end_of_the_motion() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 100f, right = 200f),
                target = IndicatorBounds(left = 0f, right = 100f),
            )

        val bounds = calculator.boundsAt(spec, elapsedMs = calculator.totalDurationMs)

        assertEquals(0f, bounds.left)
        assertEquals(100f, bounds.right)
    }
}
