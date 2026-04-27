package org.wit.vitasense.ui.theme

import androidx.annotation.StyleRes
import org.wit.vitasense.R
import org.wit.vitasense.model.ThemeFamily

object ThemeFamilyStyleResolver {
    @StyleRes
    fun styleFor(family: ThemeFamily): Int =
        when (family) {
            ThemeFamily.DEFAULT -> R.style.Theme_VitaSense_Default
            ThemeFamily.OLIVE_EMBER -> R.style.Theme_VitaSense_OliveEmber
            ThemeFamily.SUNLIT_MEADOW -> R.style.Theme_VitaSense_SunlitMeadow
            ThemeFamily.ROSE_INDIGO -> R.style.Theme_VitaSense_RoseIndigo
        }
}
