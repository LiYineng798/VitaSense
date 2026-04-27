package org.wit.vitasense.ui.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class LiquidTabIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val indicatorRect = RectF()
    private val indicatorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val frameCalculator = LiquidIndicatorFrameCalculator()

    private var currentBounds = IndicatorBounds(0f, 0f)
    private var activeSpec: LiquidIndicatorMotionSpec? = null
    private var animationStartNanos = Long.MIN_VALUE
    private var frameCallbackPosted = false
    private var verticalInsetPx = 0f

    private val frameCallback =
        Choreographer.FrameCallback { frameTimeNanos ->
            frameCallbackPosted = false
            val spec = activeSpec ?: return@FrameCallback

            if (animationStartNanos == Long.MIN_VALUE) {
                animationStartNanos = frameTimeNanos
            }

            val elapsedMs = (frameTimeNanos - animationStartNanos) / 1_000_000L
            currentBounds = frameCalculator.boundsAt(spec, elapsedMs)
            invalidate()

            if (elapsedMs >= frameCalculator.totalDurationMs) {
                activeSpec = null
            } else {
                postNextFrame()
            }
        }

    fun setIndicatorColor(color: Int) {
        indicatorPaint.color = color
        invalidate()
    }

    fun setVerticalInsetPx(insetPx: Float) {
        verticalInsetPx = insetPx.coerceAtLeast(0f)
        invalidate()
    }

    fun currentBounds(): IndicatorBounds = currentBounds

    fun snapTo(bounds: IndicatorBounds) {
        cancelAnimation()
        currentBounds = bounds
        invalidate()
    }

    fun animateWith(spec: LiquidIndicatorMotionSpec) {
        if (!spec.shouldAnimate) {
            snapTo(spec.finalTarget)
            return
        }

        cancelAnimation()
        activeSpec = spec
        animationStartNanos = Long.MIN_VALUE
        postNextFrame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val safeInset = verticalInsetPx.coerceAtMost(height / 2f)
        val top = safeInset
        val bottom = (height.toFloat() - safeInset).coerceAtLeast(top)
        indicatorRect.set(currentBounds.left, top, currentBounds.right, bottom)
        val radius = (bottom - top) / 2f
        canvas.drawRoundRect(indicatorRect, radius, radius, indicatorPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimation()
    }

    private fun postNextFrame() {
        if (frameCallbackPosted || !isAttachedToWindow) {
            return
        }
        frameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun cancelAnimation() {
        if (frameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameCallbackPosted = false
        }
        activeSpec = null
        animationStartNanos = Long.MIN_VALUE
    }
}
