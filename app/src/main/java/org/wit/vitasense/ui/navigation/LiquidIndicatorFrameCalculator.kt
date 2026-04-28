package org.wit.vitasense.ui.navigation

import kotlin.math.max

class LiquidIndicatorFrameCalculator(
    private val leadingDurationMs: Long = 220L,
    private val trailingDelayMs: Long = 80L,
    private val trailingDurationMs: Long = 180L,
) {
    val totalDurationMs: Long = max(leadingDurationMs, trailingDelayMs + trailingDurationMs)

    fun boundsAt(
        spec: LiquidIndicatorMotionSpec,
        elapsedMs: Long,
    ): IndicatorBounds {
        if (!spec.shouldAnimate || spec.direction == MotionDirection.NONE) {
            return spec.finalTarget
        }

        val leadingProgress = easedProgress(elapsedMs, delayMs = 0L, durationMs = leadingDurationMs)
        val trailingProgress = easedProgress(elapsedMs, delayMs = trailingDelayMs, durationMs = trailingDurationMs)

        return when (spec.direction) {
            MotionDirection.RIGHT ->
                IndicatorBounds(
                    left = lerp(spec.start.left, spec.finalTarget.left, trailingProgress),
                    right = lerp(spec.start.right, spec.stretchTarget.right, leadingProgress),
                )

            MotionDirection.LEFT ->
                IndicatorBounds(
                    left = lerp(spec.start.left, spec.stretchTarget.left, leadingProgress),
                    right = lerp(spec.start.right, spec.finalTarget.right, trailingProgress),
                )

            MotionDirection.NONE -> spec.finalTarget
        }
    }

    private fun easedProgress(
        elapsedMs: Long,
        delayMs: Long,
        durationMs: Long,
    ): Float {
        val adjusted = (elapsedMs - delayMs).coerceAtLeast(0L)
        if (adjusted <= 0L) {
            return 0f
        }
        if (adjusted >= durationMs) {
            return 1f
        }
        val linear = (adjusted.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return MotionEasing.fastOutSlowIn(linear)
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float,
    ): Float = start + (end - start) * fraction
}
