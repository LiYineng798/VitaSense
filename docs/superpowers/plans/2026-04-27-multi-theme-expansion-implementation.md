# Multi-Theme Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two new user-selectable theme families to VitaSense, keep the current palette as the default family, and let users choose theme family and light/dark mode independently while propagating the active palette across settings, shared UI, floating navigation, and charts.

**Architecture:** Keep the existing `XML + Fragment + Room + ViewModel` structure. Extend settings persistence with a new `ThemeFamily`, keep `ThemeMode` as a separate setting, and centralize theme selection in `MainActivity` plus a small theme resolver layer. Replace warm-brown direct color usage in XML and custom views with semantic theme attributes so all three theme families work in both light and dark mode without scattering family-specific conditionals across screens.

**Tech Stack:** Kotlin, Android Views/XML, Material3, Room, Coroutines Flow, JUnit4, AndroidX Test, local Gradle 9.3.1

---

## Local Command Preamble

Run every Gradle command in this plan from `D:\1\yidong\mid_1\project` with this PowerShell preamble:

```powershell
$env:JAVA_HOME='D:\JDK21'
$env:GRADLE_USER_HOME=(Join-Path $env:USERPROFILE '.gradle')
$env:ANDROID_USER_HOME='D:\1\yidong\mid_1\project\.tmp\android-user-home'
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=D:\1\yidong\mid_1\project\.tmp\java-tmp'
$env:TEMP='D:\1\yidong\mid_1\project\.tmp\java-tmp'
$env:TMP='D:\1\yidong\mid_1\project\.tmp\java-tmp'
```

Use this Gradle binary:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat'
```

Execution rules for this workspace:

- Do not run multiple Gradle commands in parallel against this project.
- Run `--stop` before a new verification batch.
- Prefer the smallest relevant test target first, then broaden to compile gates.

## File Map

### Create

- `app/src/main/java/org/wit/vitasense/model/ThemeFamily.kt`
- `app/src/main/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolver.kt`
- `app/src/main/java/org/wit/vitasense/ui/theme/ThemeAttrColorResolver.kt`
- `app/src/main/res/values/attrs.xml`
- `app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt`
- `app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt`
- `app/src/test/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolverTest.kt`
- `app/src/test/java/org/wit/vitasense/ui/theme/ThemeSemanticColorRegressionTest.kt`

### Modify

- `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/trends/TrendsFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/common/chart/SimpleLineChartView.kt`
- `app/src/main/java/org/wit/vitasense/ui/trends/MonthlyInsightChartView.kt`
- `app/src/main/java/org/wit/vitasense/ui/trends/RecoveryHeatmapView.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/layout/fragment_dashboard.xml`
- `app/src/main/res/layout/fragment_trends.xml`
- `app/src/main/res/layout/fragment_assessment.xml`
- `app/src/main/res/layout/fragment_mood.xml`
- `app/src/main/res/layout/fragment_placeholder_page.xml`
- `app/src/main/res/layout/item_dashboard_trend_page.xml`
- `app/src/main/res/layout/item_mood_record.xml`
- `app/src/main/res/layout/item_weekly_aggregate.xml`
- `app/src/main/res/layout/item_weekly_detail_card.xml`
- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- `app/src/main/res/drawable/bg_trends_summary_tile.xml`
- `app/src/main/res/drawable/bg_trends_anomaly_chip.xml`
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/strings.xml`

### Reuse Without Structural Changes

- `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`

## Task 1: Extend Theme Settings Domain And Persistence

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/model/ThemeFamily.kt`
- Modify: `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Create: `app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

Create `DefaultSettingsRepositoryTest.kt` with repository coverage for:

- missing family key falls back to `ThemeFamily.DEFAULT`
- existing mode key still restores `ThemeMode.DARK`
- setting family persists a lowercase value

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepositoryTest {
    @Test
    fun defaults_to_default_family_when_key_is_missing() = runTest {
        val dao = FakeAppSettingDao(mapOf("theme_mode" to "dark"))
        val repository = DefaultSettingsRepository(dao)

        assertEquals(ThemeFamily.DEFAULT, repository.getThemeFamily())
        assertEquals(ThemeMode.DARK, repository.getThemeMode())
        assertEquals(ThemeFamily.DEFAULT, repository.observeThemeFamily().first())
    }

    @Test
    fun persists_family_value_as_lowercase_keyed_setting() = runTest {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setThemeFamily(ThemeFamily.SUNLIT_MEADOW)

        assertEquals("sunlit_meadow", dao.snapshot()["theme_family"])
    }
}

private class FakeAppSettingDao(
    seed: Map<String, String> = emptyMap(),
) : AppSettingDao {
    private val values = seed.toMutableMap()
    private val flows =
        values.mapValues { MutableStateFlow(AppSettingEntity(it.key, it.value)) }.toMutableMap()

    override suspend fun upsert(setting: AppSettingEntity) {
        values[setting.key] = setting.value
        flows.getOrPut(setting.key) { MutableStateFlow(setting) }.value = setting
    }

    override fun observe(key: String) =
        flows.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun get(key: String): AppSettingEntity? =
        values[key]?.let { AppSettingEntity(key, it) }

    fun snapshot(): Map<String, String> = values.toMap()
}
```

- [ ] **Step 2: Run the repository test to verify it fails**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest'
```

Expected:

- compile or symbol failure because `ThemeFamily`, `observeThemeFamily`, `getThemeFamily`, and `setThemeFamily` do not exist yet

- [ ] **Step 3: Write the minimal persistence implementation**

Create `ThemeFamily.kt`:

```kotlin
package org.wit.vitasense.model

enum class ThemeFamily {
    DEFAULT,
    OLIVE_EMBER,
    SUNLIT_MEADOW,
}
```

Expand `SettingsRepository.kt`:

```kotlin
interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeThemeFamily(): Flow<ThemeFamily>
    suspend fun getThemeMode(): ThemeMode
    suspend fun getThemeFamily(): ThemeFamily
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeFamily(family: ThemeFamily)
}
```

Update `DefaultSettingsRepository.kt` with a second key and lowercase mapping:

```kotlin
override fun observeThemeFamily(): Flow<ThemeFamily> =
    appSettingDao.observe(THEME_FAMILY_KEY).map { entity ->
        when (entity?.value?.lowercase()) {
            "olive_ember" -> ThemeFamily.OLIVE_EMBER
            "sunlit_meadow" -> ThemeFamily.SUNLIT_MEADOW
            else -> ThemeFamily.DEFAULT
        }
    }

override suspend fun getThemeFamily(): ThemeFamily =
    when (appSettingDao.get(THEME_FAMILY_KEY)?.value?.lowercase()) {
        "olive_ember" -> ThemeFamily.OLIVE_EMBER
        "sunlit_meadow" -> ThemeFamily.SUNLIT_MEADOW
        else -> ThemeFamily.DEFAULT
    }

override suspend fun setThemeFamily(family: ThemeFamily) {
    appSettingDao.upsert(
        AppSettingEntity(
            key = THEME_FAMILY_KEY,
            value = family.name.lowercase(),
        ),
    )
}
```

- [ ] **Step 4: Run the repository test to verify it passes**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest'
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/model/ThemeFamily.kt app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt
git commit -m "feat: persist theme families"
```

## Task 2: Add Theme Family Style Resolution And Semantic Theme Attributes

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolver.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/theme/ThemeAttrColorResolver.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolverTest.kt`
- Create: `app/src/main/res/values/attrs.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`

- [ ] **Step 1: Write the failing style resolver test**

Create `ThemeFamilyStyleResolverTest.kt`:

```kotlin
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
    }
}
```

- [ ] **Step 2: Run the resolver test to verify it fails**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeFamilyStyleResolverTest'
```

Expected:

- compile failure because `ThemeFamilyStyleResolver` and the new style ids do not exist yet

- [ ] **Step 3: Implement semantic attrs, theme styles, and runtime theme selection**

Create `ThemeFamilyStyleResolver.kt`:

```kotlin
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
        }
}
```

Create `ThemeAttrColorResolver.kt`:

```kotlin
package org.wit.vitasense.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

object ThemeAttrColorResolver {
    @ColorInt
    fun color(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return context.getColor(typedValue.resourceId)
    }
}
```

Add semantic attrs in `attrs.xml`:

```xml
<resources>
    <attr name="vsColorPrimaryStrong" format="reference" />
    <attr name="vsColorPrimarySoft" format="reference" />
    <attr name="vsColorSecondaryAccent" format="reference" />
    <attr name="vsColorSurfaceAlt" format="reference" />
    <attr name="vsColorWeekSignalTile" format="reference" />
    <attr name="vsColorWeekSignalHrv" format="reference" />
    <attr name="vsColorWeekSignalHeartRate" format="reference" />
    <attr name="vsColorWeekSignalSleep" format="reference" />
    <attr name="vsColorAlert" format="reference" />
    <attr name="vsColorTextTertiary" format="reference" />
</resources>
```

Define the three family styles in `values/themes.xml` and the dark equivalents in `values-night/themes.xml` using the same style names, for example:

```xml
<style name="Theme.VitaSense.OliveEmber" parent="Base.Theme.VitaSense">
    <item name="colorPrimary">@color/vs_olive_ember_light_primary</item>
    <item name="colorSecondary">@color/vs_olive_ember_light_secondary</item>
    <item name="colorSurface">@color/vs_olive_ember_light_surface</item>
    <item name="android:windowBackground">@color/vs_olive_ember_light_background</item>
    <item name="colorOutline">@color/vs_olive_ember_light_border</item>
    <item name="android:textColorPrimary">@color/vs_olive_ember_light_text_primary</item>
    <item name="android:textColorSecondary">@color/vs_olive_ember_light_text_secondary</item>
    <item name="vsColorPrimaryStrong">@color/vs_olive_ember_light_primary_strong</item>
    <item name="vsColorPrimarySoft">@color/vs_olive_ember_light_primary_soft</item>
    <item name="vsColorSecondaryAccent">@color/vs_olive_ember_light_secondary</item>
    <item name="vsColorSurfaceAlt">@color/vs_olive_ember_light_surface_alt</item>
    <item name="vsColorWeekSignalTile">@color/vs_olive_ember_light_signal_tile</item>
    <item name="vsColorAlert">@color/vs_alert_red</item>
</style>
```

Update `MainActivity.kt` before `super.onCreate()`:

```kotlin
val themeMode = appContainer.settingsRepository.getThemeMode()
val themeFamily = appContainer.settingsRepository.getThemeFamily()
setTheme(ThemeFamilyStyleResolver.styleFor(themeFamily))
applyTheme(themeMode)
```

- [ ] **Step 4: Run the resolver test and app compile gate**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeFamilyStyleResolverTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebug'
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolver.kt app/src/main/java/org/wit/vitasense/ui/theme/ThemeAttrColorResolver.kt app/src/test/java/org/wit/vitasense/ui/theme/ThemeFamilyStyleResolverTest.kt app/src/main/res/values/attrs.xml app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/java/org/wit/vitasense/MainActivity.kt
git commit -m "feat: add theme family styles"
```

## Task 3: Redesign Theme Controls In Settings

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing settings view-model test**

Create `SettingsViewModelTest.kt`:

```kotlin
package org.wit.vitasense.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.SettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun exposes_theme_family_and_persists_family_changes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(FakeHealthRepository(), repository)

        repository.themeFamily.value = ThemeFamily.OLIVE_EMBER
        advanceUntilIdle()

        assertEquals(ThemeFamily.OLIVE_EMBER, viewModel.themeFamily.value)

        viewModel.setThemeFamily(ThemeFamily.SUNLIT_MEADOW)
        advanceUntilIdle()

        assertEquals(ThemeFamily.SUNLIT_MEADOW, repository.themeFamily.value)
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 2: Run the settings view-model test to verify it fails**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.settings.SettingsViewModelTest'
```

Expected:

- compile failure because `themeFamily` flow and `setThemeFamily` do not exist yet

- [ ] **Step 3: Implement the minimal settings UI upgrade**

Update `SettingsViewModel.kt`:

```kotlin
val themeFamily: StateFlow<ThemeFamily> =
    settingsRepository.observeThemeFamily().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeFamily.DEFAULT,
    )

fun setThemeFamily(family: ThemeFamily) {
    viewModelScope.launch {
        settingsRepository.setThemeFamily(family)
    }
}
```

Update `SettingsFragment.kt` click handlers:

```kotlin
binding.themeDefaultButton.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.DEFAULT) }
binding.themeOliveEmberButton.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.OLIVE_EMBER) }
binding.themeSunlitMeadowButton.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.SUNLIT_MEADOW) }
binding.lightThemeButton.setOnClickListener { viewModel.setThemeMode(ThemeMode.LIGHT) }
binding.darkThemeButton.setOnClickListener { viewModel.setThemeMode(ThemeMode.DARK) }
```

Redesign `fragment_settings.xml` to include:

- a `MaterialButtonToggleGroup` for theme family
- a `MaterialButtonToggleGroup` for mode
- a small horizontal strip of preview swatches per family

Add strings:

```xml
<string name="theme_family_default">Default</string>
<string name="theme_family_olive_ember">Olive Ember</string>
<string name="theme_family_sunlit_meadow">Sunlit Meadow</string>
<string name="settings_theme_family_section">Theme Family</string>
<string name="settings_theme_mode_section">Mode</string>
<string name="settings_theme_status_format">%1$s / %2$s</string>
```

- [ ] **Step 4: Run the settings view-model test and compile settings UI**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.settings.SettingsViewModelTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebug'
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt app/src/main/res/layout/fragment_settings.xml app/src/main/res/values/strings.xml app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add multi-theme settings controls"
```

## Task 4: Guard Against Hard-Coded Family Colors And Migrate Shared XML Surfaces

**Files:**
- Create: `app/src/test/java/org/wit/vitasense/ui/theme/ThemeSemanticColorRegressionTest.kt`
- Modify: `app/src/main/res/layout/fragment_dashboard.xml`
- Modify: `app/src/main/res/layout/fragment_trends.xml`
- Modify: `app/src/main/res/layout/fragment_assessment.xml`
- Modify: `app/src/main/res/layout/fragment_mood.xml`
- Modify: `app/src/main/res/layout/fragment_placeholder_page.xml`
- Modify: `app/src/main/res/layout/item_dashboard_trend_page.xml`
- Modify: `app/src/main/res/layout/item_mood_record.xml`
- Modify: `app/src/main/res/layout/item_weekly_aggregate.xml`
- Modify: `app/src/main/res/layout/item_weekly_detail_card.xml`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/res/drawable/bg_trends_summary_tile.xml`
- Modify: `app/src/main/res/drawable/bg_trends_anomaly_chip.xml`
- Modify: `app/src/main/res/drawable/bg_floating_tab_bar.xml`

- [ ] **Step 1: Write the failing semantic color regression test**

Create `ThemeSemanticColorRegressionTest.kt` to reject hard-coded family colors in user-facing XML:

```kotlin
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
```

- [ ] **Step 2: Run the regression test to verify it fails**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeSemanticColorRegressionTest'
```

Expected:

- failure because the listed layouts and drawables still contain `@color/vs_primary_*` or fixed text colors

- [ ] **Step 3: Migrate shared XML and drawable colors to theme attrs**

Update layout and drawable references to semantic attrs, for example:

```xml
android:textColor="?android:attr/textColorPrimary"
android:background="?attr/colorSurface"
app:cardBackgroundColor="?attr/colorSurface"
app:strokeColor="?attr/colorOutline"
android:textColor="?android:attr/textColorSecondary"
```

For custom semantic attrs, use:

```xml
android:background="?attr/vsColorSurfaceAlt"
android:textColor="?attr/vsColorPrimaryStrong"
<solid android:color="?attr/vsColorWeekSignalTile" />
<solid android:color="?attr/vsColorPrimarySoft" />
```

Minimum migration scope in this task:

- top-level page headings and helper text
- settings cards
- anomaly chip background
- weekly signal tile background
- floating tab board background and labels
- mood and trend list item accent chips

- [ ] **Step 4: Run the regression test and both compile gates**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeSemanticColorRegressionTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebug'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebugAndroidTest'
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```powershell
git add app/src/test/java/org/wit/vitasense/ui/theme/ThemeSemanticColorRegressionTest.kt app/src/main/res/layout/fragment_dashboard.xml app/src/main/res/layout/fragment_trends.xml app/src/main/res/layout/fragment_assessment.xml app/src/main/res/layout/fragment_mood.xml app/src/main/res/layout/fragment_placeholder_page.xml app/src/main/res/layout/item_dashboard_trend_page.xml app/src/main/res/layout/item_mood_record.xml app/src/main/res/layout/item_weekly_aggregate.xml app/src/main/res/layout/item_weekly_detail_card.xml app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/main/res/drawable/bg_trends_summary_tile.xml app/src/main/res/drawable/bg_trends_anomaly_chip.xml app/src/main/res/drawable/bg_floating_tab_bar.xml
git commit -m "refactor: theme shared xml surfaces"
```

## Task 5: Migrate Custom Views And Screen Logic To Semantic Theme Colors

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/trends/TrendsFragment.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/chart/SimpleLineChartView.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/trends/MonthlyInsightChartView.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/trends/RecoveryHeatmapView.kt`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Extend the failing smoke/regression coverage for runtime theme colors**

Add one smoke assertion to `MainActivitySmokeTest.kt` so the selected tab content and indicator still come from themed resolution paths rather than fixed legacy resources. Use the current theme's resolved `colorSurface` and selected text color as the expected source of truth.

```kotlin
val typedValue = TypedValue()
activity.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
assertTrue("Expected active tab indicator to be attached", activity.findViewById<View>(R.id.bottomTabIndicator).isShown)
assertEquals(1, activity.findViewById<TextView>(R.id.tabAssessmentLabel).maxLines)
```

The test should fail initially if runtime theme code still bypasses attrs or if new helper usage breaks the existing tab configuration.

- [ ] **Step 2: Run the narrowest verification that shows the gap**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebugAndroidTest'
```

Expected:

- compile failure or stale references in custom view color branches until the attr-based helper is wired through

- [ ] **Step 3: Replace direct family-bound runtime colors with attr-based resolution**

Use `ThemeAttrColorResolver.color(context, attrRes)` in the affected Kotlin files.

Examples:

```kotlin
val accent = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimaryStrong)
val softAccent = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimarySoft)
val secondaryText = ThemeAttrColorResolver.color(context, android.R.attr.textColorSecondary)
```

Minimum runtime migration scope:

- `MainActivity` selected/unselected tab colors and indicator color
- `DashboardFragment` trend dots
- `TrendsFragment` mini-trend accent colors and insight card accents
- `SimpleLineChartView` palette factory
- `MonthlyInsightChartView` line, point, tooltip, grid, and anomaly marker palette
- `RecoveryHeatmapView` gradient, border, tooltip, and selected state palette

- [ ] **Step 4: Run final verification**

Run:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeFamilyStyleResolverTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.settings.SettingsViewModelTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:testDebugUnitTest' '--tests' 'org.wit.vitasense.ui.theme.ThemeSemanticColorRegressionTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebug'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:assembleDebugAndroidTest'
```

Expected:

- all four targeted unit tests pass
- `:app:assembleDebug` succeeds
- `:app:assembleDebugAndroidTest` succeeds

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/MainActivity.kt app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt app/src/main/java/org/wit/vitasense/ui/trends/TrendsFragment.kt app/src/main/java/org/wit/vitasense/ui/common/chart/SimpleLineChartView.kt app/src/main/java/org/wit/vitasense/ui/trends/MonthlyInsightChartView.kt app/src/main/java/org/wit/vitasense/ui/trends/RecoveryHeatmapView.kt app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git commit -m "feat: apply multi-theme palettes across charts"
```

## Task 6: Manual Visual Verification

**Files:**
- No code changes required

- [ ] **Step 1: Launch the app on an emulator or device**

Confirm each of these combinations:

- `Default / Light`
- `Default / Dark`
- `Olive Ember / Light`
- `Olive Ember / Dark`
- `Sunlit Meadow / Light`
- `Sunlit Meadow / Dark`

- [ ] **Step 2: Verify theme-switch behavior**

Confirm:

- switching family updates the current page immediately
- switching mode updates the current page immediately
- the selected theme remains after app relaunch

- [ ] **Step 3: Verify visual coverage**

Confirm on Home, Trends, Assessment, Mood, and Settings:

- page background changes with the active family
- cards and alternate surfaces change with the active family
- floating tab colors adapt to the active family
- chart lines, dots, heatmap, and tooltip panels adapt to the active family
- text remains readable in both light and dark mode
- anomaly red remains visually distinct from theme accents

- [ ] **Step 4: Commit any final polish if needed**

```powershell
git add -A
git commit -m "polish: tune multi-theme visuals"
```
