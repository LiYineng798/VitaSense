package org.wit.vitasense.domain

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationRegressionTest {
    private val hanRegex = Regex("[\\u4E00-\\u9FFF]")

    private val localizedFiles =
        listOf(
            "src/main/res/values/strings.xml",
            "src/main/java/org/wit/vitasense/data/importer/DemoImportProvider.kt",
            "src/main/java/org/wit/vitasense/data/repository/DefaultHealthRepository.kt",
            "src/main/java/org/wit/vitasense/domain/RiskScorer.kt",
            "src/main/java/org/wit/vitasense/domain/SummaryGenerator.kt",
            "src/main/java/org/wit/vitasense/model/MoodType.kt",
            "src/main/java/org/wit/vitasense/ui/assessment/AssessmentViewModel.kt",
            "src/main/java/org/wit/vitasense/ui/common/chart/SimpleLineChartView.kt",
            "src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt",
            "src/main/java/org/wit/vitasense/ui/mood/MoodAdapter.kt",
            "src/main/java/org/wit/vitasense/ui/mood/MoodFragment.kt",
            "src/main/java/org/wit/vitasense/ui/mood/MoodViewModel.kt",
            "src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt",
            "src/main/java/org/wit/vitasense/ui/trends/TrendChartModelFactory.kt",
            "src/main/java/org/wit/vitasense/ui/trends/TrendsTooltipFactory.kt",
            "src/main/java/org/wit/vitasense/ui/trends/WeeklyAggregateAdapter.kt",
            "src/main/java/org/wit/vitasense/ui/trends/WeeklyDetailAdapter.kt",
        )

    @Test
    fun localized_sources_do_not_contain_han_characters() {
        val moduleRoot = resolveModuleRoot()

        localizedFiles.forEach { relativePath ->
            val file = moduleRoot.resolve(relativePath)
            assertTrue("Expected file to exist: $relativePath", file.exists())

            val content = Files.readString(file)
            assertFalse("Expected English-only user-facing copy in $relativePath", hanRegex.containsMatchIn(content))
        }
    }

    private fun resolveModuleRoot(): Path {
        val cwd = Paths.get("").toAbsolutePath()
        val candidates =
            listOf(
                cwd,
                cwd.resolve("app"),
            )

        return candidates.firstOrNull { candidate ->
            candidate.resolve("src/main/AndroidManifest.xml").exists()
        } ?: error("Unable to resolve app module root from $cwd")
    }
}
