package org.wit.vitasense.ui.navigation

object MotionEasing {
    fun fastOutSlowIn(input: Float): Float = cubicBezier(input, 0.4f, 0f, 0.2f, 1f)

    private fun cubicBezier(
        input: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val clamped = input.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f

        repeat(14) {
            val midpoint = (low + high) / 2f
            val estimate = cubicBezierComponent(midpoint, x1, x2)
            if (estimate < clamped) {
                low = midpoint
            } else {
                high = midpoint
            }
        }

        val t = (low + high) / 2f
        return cubicBezierComponent(t, y1, y2)
    }

    private fun cubicBezierComponent(
        t: Float,
        a: Float,
        b: Float,
    ): Float {
        val inverse = 1f - t
        return 3f * inverse * inverse * t * a + 3f * inverse * t * t * b + t * t * t
    }
}
