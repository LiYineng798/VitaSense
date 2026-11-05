package org.wit.vitasense.ui.navigation

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/** Keep focused/requested controls above the floating navigation while content can draw beneath it. */
class OverlayAwareScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : NestedScrollView(context, attrs) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val visibleRect = Rect(rect)
        visibleRect.bottom += paddingBottom
        return super.computeScrollDeltaToGetChildRectOnScreen(visibleRect)
    }
}
