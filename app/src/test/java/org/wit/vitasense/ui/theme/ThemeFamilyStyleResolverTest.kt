package org.wit.vitasense.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.R
import org.wit.vitasense.model.ThemeFamily

class ThemeFamilyStyleResolverTest {
    @Test
    fun maps_family_to_expected_runtime_theme_style() {
        assertEquals(R.style.Theme_VitaSense_Default, ThemeFamilyStyleResolver.styleFor(ThemeFamily.DEFAULT))
        assertEquals(R.style.Theme_VitaSense_OliveEmber, ThemeFamilyStyleResolver.styleFor(ThemeFamily.OLIVE_EMBER))
        assertEquals(R.style.Theme_VitaSense_SunlitMeadow, ThemeFamilyStyleResolver.styleFor(ThemeFamily.SUNLIT_MEADOW))
        assertEquals(R.style.Theme_VitaSense_RoseIndigo, ThemeFamilyStyleResolver.styleFor(ThemeFamily.ROSE_INDIGO))
    }
}
