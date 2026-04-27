package org.wit.vitasense.ui.navigation

enum class MotionDirection {
    LEFT,
    RIGHT,
    NONE,
}

data class LiquidIndicatorMotionSpec(
    val shouldAnimate: Boolean,
    val direction: MotionDirection,
    val start: IndicatorBounds,
    val stretchTarget: IndicatorBounds,
    val finalTarget: IndicatorBounds,
)

class LiquidIndicatorMotionPlanner {
    fun plan(
        start: IndicatorBounds,
        target: IndicatorBounds,
    ): LiquidIndicatorMotionSpec {
        if (start == target) {
            return LiquidIndicatorMotionSpec(
                shouldAnimate = false,
                direction = MotionDirection.NONE,
                start = start,
                stretchTarget = target,
                finalTarget = target,
            )
        }

        return if (target.left > start.left) {
            LiquidIndicatorMotionSpec(
                shouldAnimate = true,
                direction = MotionDirection.RIGHT,
                start = start,
                stretchTarget = IndicatorBounds(left = start.left, right = target.right),
                finalTarget = target,
            )
        } else {
            LiquidIndicatorMotionSpec(
                shouldAnimate = true,
                direction = MotionDirection.LEFT,
                start = start,
                stretchTarget = IndicatorBounds(left = target.left, right = start.right),
                finalTarget = target,
            )
        }
    }
}
