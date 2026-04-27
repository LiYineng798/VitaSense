package org.wit.vitasense.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

object ThemeAttrColorResolver {
    @ColorInt
    fun color(
        context: Context,
        @AttrRes attrRes: Int,
    ): Int {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attrRes, typedValue, true)
        check(resolved) { "Theme attribute not found: $attrRes" }
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
}
