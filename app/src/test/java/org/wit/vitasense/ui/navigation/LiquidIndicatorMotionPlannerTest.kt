package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidIndicatorMotionPlannerTest {
    private val planner = LiquidIndicatorMotionPlanner()

    @Test
    fun stretches_right_edge_first_when_target_is_to_the_right() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 100f, right = 200f),
            )

        assertTrue(spec.shouldAnimate)
        assertEquals(MotionDirection.RIGHT, spec.direction)
        assertEquals(0f, spec.stretchTarget.left)
        assertEquals(200f, spec.stretchTarget.right)
        assertEquals(100f, spec.finalTarget.left)
    }

    @Test
    fun returns_non_animated_spec_when_target_matches_start() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 0f, right = 100f),
            )

        assertFalse(spec.shouldAnimate)
        assertEquals(MotionDirection.NONE, spec.direction)
    }
}
