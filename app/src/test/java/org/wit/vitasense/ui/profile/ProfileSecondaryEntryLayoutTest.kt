package org.wit.vitasense.ui.profile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSecondaryEntryLayoutTest {
    private val projectRoot = locateProjectRoot()
    private val profileLayout =
        projectRoot.resolve("app/src/main/res/layout/fragment_profile.xml").readText()
    private val authLayout =
        projectRoot.resolve("app/src/main/res/layout/fragment_auth.xml").readText()
    private val appearanceLayout =
        projectRoot.resolve("app/src/main/res/layout/fragment_appearance.xml").readText()
    private val settingsLayout =
        projectRoot.resolve("app/src/main/res/layout/fragment_settings.xml").readText()
    private val stringsXml =
        projectRoot.resolve("app/src/main/res/values/strings.xml").readText()

    @Test
    fun profile_secondary_entries_use_compact_themed_labels() {
        assertFalse(profileLayout.contains("profile_appearance_entry_subtitle"))
        assertFalse(profileLayout.contains("profile_settings_entry_subtitle"))
        assertTrue(profileLayout.contains("@string/profile_appearance_button_label"))
        assertTrue(profileLayout.contains("@string/profile_settings_button_label"))
        assertFalse(profileLayout.contains("?attr/vsColorPrimaryStrong"))
        assertTrue(profileLayout.contains("app:cardBackgroundColor=\"?attr/vsColorPrimarySoft\""))
        assertFalse(profileLayout.contains("?attr/colorOnPrimary"))
        assertTrue(profileLayout.contains("?android:attr/textColorPrimary"))
        assertTrue(profileLayout.contains("@string/common_forward_glyph"))
    }

    @Test
    fun secondary_pages_use_text_glyphs_for_back_and_forward_actions() {
        assertTrue(stringsXml.contains("name=\"common_back_glyph\""))
        assertTrue(stringsXml.contains("name=\"common_forward_glyph\""))

        assertFalse(authLayout.contains("ic_media_previous"))
        assertFalse(appearanceLayout.contains("ic_media_previous"))
        assertFalse(settingsLayout.contains("ic_media_previous"))

        assertTrue(authLayout.contains("@string/common_back_glyph"))
        assertTrue(appearanceLayout.contains("@string/common_back_glyph"))
        assertTrue(settingsLayout.contains("@string/common_back_glyph"))
    }

    private fun locateProjectRoot(): File {
        var current = File(checkNotNull(System.getProperty("user.dir")))
        repeat(5) {
            if (current.resolve("app/src/main/res/layout/fragment_profile.xml").exists()) {
                return current
            }
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate project root from ${System.getProperty("user.dir")}")
    }
}
