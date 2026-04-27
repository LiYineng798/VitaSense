package org.wit.vitasense.ui.theme

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSemanticColorRegressionTest {
    private val bannedTokens =
        listOf(
            "@color/vs_primary_",
            "@color/vs_dark_primary_",
            "@color/vs_text_primary",
            "@color/vs_text_secondary",
            "@color/vs_dark_text_primary",
            "@color/vs_dark_text_secondary",
        )

    private val themedFiles =
        listOf(
            "src/main/res/layout/fragment_dashboard.xml",
            "src/main/res/layout/fragment_trends.xml",
            "src/main/res/layout/fragment_assessment.xml",
            "src/main/res/layout/fragment_mood.xml",
            "src/main/res/layout/fragment_settings.xml",
            "src/main/res/layout/view_floating_bottom_tabs.xml",
            "src/main/res/drawable/bg_trends_summary_tile.xml",
            "src/main/res/drawable/bg_trends_anomaly_chip.xml",
            "src/main/res/drawable/bg_floating_tab_bar.xml",
        )

    @Test
    fun themed_xml_uses_attrs_instead_of_family_bound_colors() {
        val moduleRoot = resolveModuleRoot()
        themedFiles.forEach { relativePath ->
            val file = moduleRoot.resolve(relativePath)
            assertTrue("Expected file to exist: $relativePath", file.exists())
            val content = Files.readString(file)
            bannedTokens.forEach { token ->
                assertFalse("Unexpected token $token in $relativePath", content.contains(token))
            }
        }
    }

    private fun resolveModuleRoot(): Path {
        val cwd = Paths.get("").toAbsolutePath()
        return listOf(cwd, cwd.resolve("app")).first { it.resolve("src/main/AndroidManifest.xml").exists() }
    }
}
