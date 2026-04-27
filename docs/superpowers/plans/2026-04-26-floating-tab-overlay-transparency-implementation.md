# Floating Tab Overlay Transparency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the bottom tab as a full-width transparent overlay with an inner solid board and evenly centered `Home`, `Trends`, `Assessment`, and `Mood` cells, while keeping VitaSense theme colors and existing navigation behavior.

**Architecture:** Keep the current `XML + Fragment + Navigation` app structure and the existing `MainActivity`-owned top-level navigation flow. Revert the narrowed `wrap_content` floating-pill layout back to the document's full-width overlay model, keep the inner board as the only solid visual surface, and preserve the existing liquid-indicator and dynamic bottom-safe-spacing logic unless verification proves a concrete mismatch.

**Tech Stack:** Kotlin, Android Views/XML, Material3, Fragment Navigation, ViewBinding, JUnit4, Espresso, local Gradle 9.3.1 distribution

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

PowerShell and Gradle execution rules for this workspace:

- Quote the instrumentation runner property as its own argument:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest'
```

- Do not run multiple Gradle commands in parallel against this project directory. Parallel runs can corrupt Kotlin incremental caches and create false build failures.
- If a Kotlin daemon/cache error appears after an interrupted or parallel build, recover first with:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
```

Current workspace constraint:

- `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest` can stop before execution if the offline cache is missing `androidx.room:room-testing:2.7.2` or `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0`.
- `:app:assembleDebug` is the reliable compile gate in the current offline environment when run serially.

## File Map

### Modify

- `app/src/main/res/layout/activity_main.xml`
  - Restore the document's `20dp / 20dp / 16dp` floating overlay margins.
- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
  - Replace the narrowed `wrap_content` board structure with a full-width transparent overlay shell and equal-width weighted tab cells.
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
  - Match the document-style board radius while retaining theme colors.
- `app/src/main/res/values/dimens.xml`
  - Remove the fixed-width pill dimensions and normalize board radius/padding/item height for the restored overlay layout.
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
  - Assert the tab uses the document-style full-width overlay structure and equal-weight cells.

### Reuse Without Modification

- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
  - Keeps the dynamic bottom-safe-spacing logic and selected-state color resolution.
- `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometry.kt`
  - Reuses the existing modest active-indicator inset logic unless verification proves a mismatch.
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
  - Keeps the existing liquid-indicator draw/animation behavior.
- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
  - Regression coverage for top-level destination switching.
- `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`
  - Regression coverage for `Quick Mood Log -> Mood -> Home`.

## Task 1: Restore The Document Overlay Layout

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Write the failing instrumentation smoke test**

Replace `MainActivitySmokeTest.kt` with:

```kotlin
package org.wit.vitasense.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun uses_document_overlay_layout_for_the_floating_tabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val includeRoot = activity.findViewById<View>(R.id.floatingBottomTabs)
                val includeParams = includeRoot.layoutParams as ConstraintLayout.LayoutParams
                val card = activity.findViewById<MaterialCardView>(R.id.floatingBottomTabsCard)
                val board = activity.findViewById<View>(R.id.floatingBottomTabsBoard)
                val foreground = activity.findViewById<LinearLayout>(R.id.layoutTabForeground)
                val home = activity.findViewById<View>(R.id.tabHome)
                val trends = activity.findViewById<View>(R.id.tabTrends)
                val assessment = activity.findViewById<View>(R.id.tabAssessment)
                val mood = activity.findViewById<View>(R.id.tabMood)

                val homeParams = home.layoutParams as LinearLayout.LayoutParams
                val trendsParams = trends.layoutParams as LinearLayout.LayoutParams
                val assessmentParams = assessment.layoutParams as LinearLayout.LayoutParams
                val moodParams = mood.layoutParams as LinearLayout.LayoutParams

                assertTrue(card.isShown)
                assertTrue(board.isShown)
                assertEquals(dp(activity, 20), includeParams.marginStart)
                assertEquals(dp(activity, 20), includeParams.marginEnd)
                assertEquals(dp(activity, 16), includeParams.bottomMargin)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, card.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, board.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, foreground.layoutParams.width)
                assertEquals(0, homeParams.width)
                assertEquals(0, trendsParams.width)
                assertEquals(0, assessmentParams.width)
                assertEquals(0, moodParams.width)
                assertEquals(1f, homeParams.weight, 0f)
                assertEquals(1f, trendsParams.weight, 0f)
                assertEquals(1f, assessmentParams.weight, 0f)
                assertEquals(1f, moodParams.weight, 0f)
            }
        }
    }

    @Test
    fun no_longer_contains_the_legacy_bottom_navigation_view() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(isAssignableFrom(BottomNavigationView::class.java)).check(doesNotExist())
    }

    private fun dp(activity: MainActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest'
```

Expected in a fully provisioned test cache:

```text
java.lang.AssertionError: expected:<20dp margin / MATCH_PARENT / weight=1> but was:<0dp margin / WRAP_CONTENT / fixed width>
```

If the offline cache is still incomplete, expect the command to stop earlier during dependency resolution for `room-testing` or `kotlinx-coroutines-test`.

- [ ] **Step 3: Write the minimal overlay-structure implementation**

Replace `activity_main.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/vs_background">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:navGraph="@navigation/main_nav_graph" />

    <include
        android:id="@+id/floatingBottomTabs"
        layout="@layout/view_floating_bottom_tabs"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="20dp"
        android:layout_marginEnd="20dp"
        android:layout_marginBottom="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

Replace `dimens.xml` with:

```xml
<resources>
    <dimen name="vs_trend_chart_height">160dp</dimen>
    <dimen name="vs_bottom_tab_board_radius">32dp</dimen>
    <dimen name="vs_bottom_tab_board_horizontal_padding">8dp</dimen>
    <dimen name="vs_bottom_tab_board_vertical_padding">8dp</dimen>
    <dimen name="vs_bottom_tab_item_height">64dp</dimen>
    <dimen name="vs_bottom_tab_indicator_horizontal_inset">10dp</dimen>
    <dimen name="vs_bottom_tab_indicator_vertical_inset">7dp</dimen>
</resources>
```

Replace `bg_floating_tab_bar.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <corners android:radius="@dimen/vs_bottom_tab_board_radius" />
    <solid android:color="?attr/colorSurface" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorOutline" />
</shape>
```

Replace `view_floating_bottom_tabs.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/floatingBottomTabsCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@android:color/transparent"
    android:contentDescription="@null"
    app:cardBackgroundColor="@android:color/transparent"
    app:cardCornerRadius="@dimen/vs_bottom_tab_board_radius"
    app:cardElevation="10dp"
    app:strokeWidth="0dp">

    <FrameLayout
        android:id="@+id/floatingBottomTabsBoard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_floating_tab_bar"
        android:paddingStart="@dimen/vs_bottom_tab_board_horizontal_padding"
        android:paddingTop="@dimen/vs_bottom_tab_board_vertical_padding"
        android:paddingEnd="@dimen/vs_bottom_tab_board_horizontal_padding"
        android:paddingBottom="@dimen/vs_bottom_tab_board_vertical_padding">

        <org.wit.vitasense.ui.navigation.LiquidTabIndicatorView
            android:id="@+id/bottomTabIndicator"
            android:layout_width="match_parent"
            android:layout_height="@dimen/vs_bottom_tab_item_height" />

        <LinearLayout
            android:id="@+id/layoutTabForeground"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/tabHome"
                android:layout_width="0dp"
                android:layout_height="@dimen/vs_bottom_tab_item_height"
                android:layout_weight="1"
                android:contentDescription="@string/nav_dashboard"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:id="@+id/tabHomeIcon"
                    android:layout_width="18dp"
                    android:layout_height="18dp"
                    android:src="@android:drawable/ic_menu_view" />

                <TextView
                    android:id="@+id/tabHomeLabel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/nav_dashboard"
                    android:textColor="@color/vs_text_secondary"
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabTrends"
                android:layout_width="0dp"
                android:layout_height="@dimen/vs_bottom_tab_item_height"
                android:layout_weight="1"
                android:contentDescription="@string/nav_trends"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:id="@+id/tabTrendsIcon"
                    android:layout_width="18dp"
                    android:layout_height="18dp"
                    android:src="@android:drawable/ic_menu_sort_by_size" />

                <TextView
                    android:id="@+id/tabTrendsLabel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/nav_trends"
                    android:textColor="@color/vs_text_secondary"
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabAssessment"
                android:layout_width="0dp"
                android:layout_height="@dimen/vs_bottom_tab_item_height"
                android:layout_weight="1"
                android:contentDescription="@string/nav_assessment"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:id="@+id/tabAssessmentIcon"
                    android:layout_width="18dp"
                    android:layout_height="18dp"
                    android:src="@android:drawable/ic_menu_info_details" />

                <TextView
                    android:id="@+id/tabAssessmentLabel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/nav_assessment"
                    android:textColor="@color/vs_text_secondary"
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabMood"
                android:layout_width="0dp"
                android:layout_height="@dimen/vs_bottom_tab_item_height"
                android:layout_weight="1"
                android:contentDescription="@string/nav_mood"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp">

                <ImageView
                    android:id="@+id/tabMoodIcon"
                    android:layout_width="18dp"
                    android:layout_height="18dp"
                    android:src="@android:drawable/ic_menu_edit" />

                <TextView
                    android:id="@+id/tabMoodLabel"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/nav_mood"
                    android:textColor="@color/vs_text_secondary"
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

        </LinearLayout>

    </FrameLayout>

</com.google.android.material.card.MaterialCardView>
```

Replace `MainActivitySmokeTest.kt` with:

```kotlin
package org.wit.vitasense.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun uses_document_overlay_layout_for_the_floating_tabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val includeRoot = activity.findViewById<View>(R.id.floatingBottomTabs)
                val includeParams = includeRoot.layoutParams as ConstraintLayout.LayoutParams
                val card = activity.findViewById<MaterialCardView>(R.id.floatingBottomTabsCard)
                val board = activity.findViewById<View>(R.id.floatingBottomTabsBoard)
                val foreground = activity.findViewById<LinearLayout>(R.id.layoutTabForeground)
                val home = activity.findViewById<View>(R.id.tabHome)
                val trends = activity.findViewById<View>(R.id.tabTrends)
                val assessment = activity.findViewById<View>(R.id.tabAssessment)
                val mood = activity.findViewById<View>(R.id.tabMood)

                val homeParams = home.layoutParams as LinearLayout.LayoutParams
                val trendsParams = trends.layoutParams as LinearLayout.LayoutParams
                val assessmentParams = assessment.layoutParams as LinearLayout.LayoutParams
                val moodParams = mood.layoutParams as LinearLayout.LayoutParams

                assertTrue(card.isShown)
                assertTrue(board.isShown)
                assertEquals(dp(activity, 20), includeParams.marginStart)
                assertEquals(dp(activity, 20), includeParams.marginEnd)
                assertEquals(dp(activity, 16), includeParams.bottomMargin)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, card.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, board.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, foreground.layoutParams.width)
                assertEquals(0, homeParams.width)
                assertEquals(0, trendsParams.width)
                assertEquals(0, assessmentParams.width)
                assertEquals(0, moodParams.width)
                assertEquals(1f, homeParams.weight, 0f)
                assertEquals(1f, trendsParams.weight, 0f)
                assertEquals(1f, assessmentParams.weight, 0f)
                assertEquals(1f, moodParams.weight, 0f)
            }
        }
    }

    @Test
    fun no_longer_contains_the_legacy_bottom_navigation_view() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(isAssignableFrom(BottomNavigationView::class.java)).check(doesNotExist())
    }

    private fun dp(activity: MainActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 4: Run the compile gate and smoke test**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:assembleDebug
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest'
```

Expected:

```text
:app:assembleDebug -> BUILD SUCCESSFUL
:app:connectedDebugAndroidTest -> BUILD SUCCESSFUL once the test dependency cache is restored and a device/emulator is connected
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/main/res/drawable/bg_floating_tab_bar.xml app/src/main/res/values/dimens.xml app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git commit -m "refactor: restore floating tab overlay layout"
```

## Task 2: Re-run Navigation Regressions And Perform Visual Verification

**Files:**
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`

- [ ] **Step 1: Run the existing navigation regressions**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest'
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest'
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the final compile verification**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' --stop
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Perform the manual visual check on a running emulator/device**

Confirm all of the following in the app:

- The bottom tab visually spans the full available width inside the `20dp` side margins.
- The outer overlay shell is transparent outside the inner solid board.
- Only the inner board and the active indicator visually obscure the content behind the tab.
- `Home`, `Trends`, `Assessment`, and `Mood` are evenly distributed and centered across the full tab row.
- The tab still looks like a floating overlay rather than a page footer.
- `Quick Mood Log -> Mood -> Home` still works.
