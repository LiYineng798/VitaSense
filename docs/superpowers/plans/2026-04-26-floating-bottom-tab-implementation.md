# Floating Bottom Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current system bottom navigation with a floating liquid-style bottom tab that preserves the existing `Home`, `Trends`, `Assessment`, and `Mood` destinations, fixes `Quick Mood Log` routing, and matches the approved design.

**Architecture:** Keep the app on `XML + Fragment + Navigation`, but move bottom-tab state ownership into `MainActivity`. Build the floating bar as a custom layout include with explicit tab cells and a custom `LiquidTabIndicatorView`. Use unit-tested motion helpers for the liquid animation and keep fragment-to-tab interaction behind a small `TopLevelNavigator` interface.

**Tech Stack:** Kotlin, Android Views/XML, Fragment Navigation, Material3, ViewBinding, JUnit4, Espresso

---

## File Map

### Create

- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
  - Floating tab board, foreground tab cells, and the custom indicator layer.
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
  - Rounded board background with stroke.
- `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
  - Maps app top-level destinations to nav graph ids and tab view ids.
- `app/src/main/java/org/wit/vitasense/ui/navigation/TopLevelNavigator.kt`
  - Narrow activity-owned navigation contract used by fragments.
- `app/src/main/java/org/wit/vitasense/ui/navigation/IndicatorBounds.kt`
  - Holds indicator `left` and `right` geometry.
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlanner.kt`
  - Produces motion specs for leftward and rightward liquid travel.
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculator.kt`
  - Computes per-frame bounds using durations, delay, and easing.
- `app/src/main/java/org/wit/vitasense/ui/navigation/MotionEasing.kt`
  - Pure Kotlin cubic-bezier easing helper for `fastOutSlowIn`.
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
  - Custom view that draws and animates the indicator with `Choreographer`.
- `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt`
  - Unit tests for destination mapping.
- `app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlannerTest.kt`
  - Unit tests for motion direction and stretch target planning.
- `app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculatorTest.kt`
  - Unit tests for per-frame geometry.
- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
  - Espresso coverage for tab-to-screen navigation.

### Modify

- `app/src/main/res/layout/activity_main.xml`
  - Replace the linear root with a floating-tab-friendly root and include.
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
  - Own nav controller state, bind floating tab clicks, sync selected state, and drive indicator animation.
- `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
  - Replace direct `BottomNavigationView` access with `TopLevelNavigator`.
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
  - Cover floating tab visibility and indicator presence.
- `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`
  - Route through the floating tab id instead of the old system nav.
- `app/src/main/res/values/themes.xml`
  - Remove the old bottom navigation active-indicator style after the custom tab is in place.
- `app/src/main/res/menu/bottom_nav_menu.xml`
  - Delete after the system bottom navigation is removed.
- `app/src/main/res/color/bottom_nav_color.xml`
  - Delete after the system bottom navigation is removed.

## Task 1: Build The Static Floating Tab Shell

**Files:**
- Create: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Create: `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Write the failing instrumentation test**

```kotlin
package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun shows_floating_bottom_tabs() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.floatingBottomTabsCard)).check(matches(isDisplayed()))
        onView(withId(R.id.tabHome)).check(matches(isDisplayed()))
        onView(withId(R.id.tabTrends)).check(matches(isDisplayed()))
        onView(withId(R.id.tabAssessment)).check(matches(isDisplayed()))
        onView(withId(R.id.tabMood)).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected:

```text
FAILURE: There were failing tests.
androidx.test.espresso.NoMatchingViewException: No views in hierarchy found matching: with id: org.wit.vitasense:id/floatingBottomTabsCard
```

- [ ] **Step 3: Write the minimal implementation**

`activity_main.xml`

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

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:visibility="gone"
        android:importantForAccessibility="noHideDescendants"
        app:menu="@menu/bottom_nav_menu" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

`view_floating_bottom_tabs.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/floatingBottomTabsCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:contentDescription="@null"
    app:cardBackgroundColor="@android:color/transparent"
    app:cardCornerRadius="32dp"
    app:cardElevation="10dp">

    <LinearLayout
        android:id="@+id/layoutTabForeground"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_floating_tab_bar"
        android:orientation="horizontal">

        <LinearLayout
            android:id="@+id/tabHome"
            android:layout_width="0dp"
            android:layout_height="64dp"
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
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/tabTrends"
            android:layout_width="0dp"
            android:layout_height="64dp"
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
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/tabAssessment"
            android:layout_width="0dp"
            android:layout_height="64dp"
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
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/tabMood"
            android:layout_width="0dp"
            android:layout_height="64dp"
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
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
```

`bg_floating_tab_bar.xml`

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <corners android:radius="32dp" />
    <solid android:color="?attr/colorSurface" />
    <stroke
        android:width="1dp"
        android:color="?attr/colorOutline" />
</shape>
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```powershell
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/main/res/drawable/bg_floating_tab_bar.xml app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git commit -m "feat: add floating bottom tab shell"
```

## Task 2: Centralize Top-Level Navigation And Decouple Home

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/TopLevelNavigator.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt`
- Create: `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`

- [ ] **Step 1: Write the failing tests**

`BottomTabDestinationTest.kt`

```kotlin
package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.wit.vitasense.R

class BottomTabDestinationTest {
    @Test
    fun maps_nav_graph_destinations_to_bottom_tabs() {
        assertEquals(BottomTabDestination.HOME, BottomTabDestination.fromDestinationId(R.id.dashboardFragment))
        assertEquals(BottomTabDestination.TRENDS, BottomTabDestination.fromDestinationId(R.id.trendsFragment))
        assertEquals(BottomTabDestination.ASSESSMENT, BottomTabDestination.fromDestinationId(R.id.assessmentFragment))
        assertEquals(BottomTabDestination.MOOD, BottomTabDestination.fromDestinationId(R.id.moodFragment))
        assertNull(BottomTabDestination.fromDestinationId(R.id.settingsFragment))
    }
}
```

`FloatingBottomTabNavigationTest.kt`

```kotlin
package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class FloatingBottomTabNavigationTest {
    @Test
    fun tapping_floating_tabs_opens_matching_top_level_screens() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.tabTrends)).perform(click())
        onView(withText(R.string.trends_title)).check(matches(isDisplayed()))

        onView(withId(R.id.tabAssessment)).perform(click())
        onView(withText(R.string.assessment_title)).check(matches(isDisplayed()))

        onView(withId(R.id.tabMood)).perform(click())
        onView(withText(R.string.mood_title)).check(matches(isDisplayed()))
    }
}
```

`HomeQuickMoodNavigationTest.kt`

```kotlin
@Test
fun quick_mood_navigation_still_allows_returning_home_from_floating_tab() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(withId(R.id.quickMoodButton)).perform(scrollTo(), click())
    onView(withId(R.id.tabHome)).perform(click())

    onView(withId(R.id.scoreLabelText)).check(matches(isDisplayed()))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.BottomTabDestinationTest"
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest
```

Expected:

```text
e: Unresolved reference: BottomTabDestination
androidx.test.espresso.PerformException: Error performing 'single click' on view with id: org.wit.vitasense:id/tabTrends
```

- [ ] **Step 3: Write the minimal implementation**

`BottomTabDestination.kt`

```kotlin
package org.wit.vitasense.ui.navigation

import androidx.annotation.IdRes
import org.wit.vitasense.R

enum class BottomTabDestination(
    @IdRes val navDestinationId: Int,
    @IdRes val tabViewId: Int,
    @IdRes val iconViewId: Int,
    @IdRes val labelViewId: Int,
) {
    HOME(R.id.dashboardFragment, R.id.tabHome, R.id.tabHomeIcon, R.id.tabHomeLabel),
    TRENDS(R.id.trendsFragment, R.id.tabTrends, R.id.tabTrendsIcon, R.id.tabTrendsLabel),
    ASSESSMENT(R.id.assessmentFragment, R.id.tabAssessment, R.id.tabAssessmentIcon, R.id.tabAssessmentLabel),
    MOOD(R.id.moodFragment, R.id.tabMood, R.id.tabMoodIcon, R.id.tabMoodLabel),
    ;

    companion object {
        fun fromDestinationId(@IdRes destinationId: Int): BottomTabDestination? =
            entries.firstOrNull { it.navDestinationId == destinationId }
    }
}
```

`TopLevelNavigator.kt`

```kotlin
package org.wit.vitasense.ui.navigation

interface TopLevelNavigator {
    fun navigateToBottomDestination(destination: BottomTabDestination)
}
```

`MainActivity.kt`

```kotlin
private lateinit var navController: NavController
private var selectedBottomDestination = BottomTabDestination.HOME

override fun onCreate(savedInstanceState: Bundle?) {
    val navHostFragment =
        supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
    navController = navHostFragment.navController

    bindFloatingTabs()
    renderStaticSelection(selectedBottomDestination)
    navController.addOnDestinationChangedListener { _, destination, _ ->
        BottomTabDestination.fromDestinationId(destination.id)?.let { matched ->
            selectedBottomDestination = matched
            renderStaticSelection(matched)
        }
    }
}

override fun navigateToBottomDestination(destination: BottomTabDestination) {
    if (
        selectedBottomDestination == destination &&
        navController.currentDestination?.id == destination.navDestinationId
    ) {
        return
    }
    selectedBottomDestination = destination
    navController.navigate(
        destination.navDestinationId,
        null,
        navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        },
    )
    renderStaticSelection(destination)
}

private fun bindFloatingTabs() {
    findViewById<View>(R.id.tabHome).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.HOME)
    }
    findViewById<View>(R.id.tabTrends).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.TRENDS)
    }
    findViewById<View>(R.id.tabAssessment).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.ASSESSMENT)
    }
    findViewById<View>(R.id.tabMood).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.MOOD)
    }
}

private fun renderStaticSelection(selected: BottomTabDestination) {
    BottomTabDestination.entries.forEach { destination ->
        val isSelected = destination == selected
        val textColor = if (isSelected) getColor(R.color.white) else getColor(R.color.vs_text_secondary)
        findViewById<ImageView>(destination.iconViewId).imageTintList = ColorStateList.valueOf(textColor)
        findViewById<TextView>(destination.labelViewId).setTextColor(textColor)
    }
}
```

`DashboardFragment.kt`

```kotlin
private fun navigateToBottomDestination(destination: BottomTabDestination) {
    (requireActivity() as TopLevelNavigator).navigateToBottomDestination(destination)
}
```

And update the click handler:

```kotlin
binding.quickMoodButton.setOnClickListener {
    navigateToBottomDestination(BottomTabDestination.MOOD)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.BottomTabDestinationTest"
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/MainActivity.kt app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt app/src/main/java/org/wit/vitasense/ui/navigation/TopLevelNavigator.kt app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt
git commit -m "feat: wire floating bottom tabs to top-level navigation"
```

## Task 3: Implement And Test The Liquid Motion Math

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/IndicatorBounds.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlanner.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculator.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/MotionEasing.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlannerTest.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculatorTest.kt`

- [ ] **Step 1: Write the failing unit tests**

`LiquidIndicatorMotionPlannerTest.kt`

```kotlin
package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidIndicatorMotionPlannerTest {
    private val planner = LiquidIndicatorMotionPlanner()

    @Test
    fun stretches_right_edge_first_when_target_is_to_the_right() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 100f, right = 200f),
            )

        assertTrue(spec.shouldAnimate)
        assertEquals(MotionDirection.RIGHT, spec.direction)
        assertEquals(0f, spec.stretchTarget.left)
        assertEquals(200f, spec.stretchTarget.right)
        assertEquals(100f, spec.finalTarget.left)
    }

    @Test
    fun returns_non_animated_spec_when_target_matches_start() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 0f, right = 100f),
            )

        assertFalse(spec.shouldAnimate)
        assertEquals(MotionDirection.NONE, spec.direction)
    }
}
```

`LiquidIndicatorFrameCalculatorTest.kt`

```kotlin
package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidIndicatorFrameCalculatorTest {
    private val planner = LiquidIndicatorMotionPlanner()
    private val calculator = LiquidIndicatorFrameCalculator()

    @Test
    fun moves_only_the_leading_edge_before_the_trailing_delay_on_rightward_motion() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 0f, right = 100f),
                target = IndicatorBounds(left = 100f, right = 200f),
            )

        val bounds = calculator.boundsAt(spec, elapsedMs = 40L)

        assertEquals(0f, bounds.left)
        assertTrue(bounds.right > 100f)
    }

    @Test
    fun lands_exactly_on_the_final_target_at_the_end_of_the_motion() {
        val spec =
            planner.plan(
                start = IndicatorBounds(left = 100f, right = 200f),
                target = IndicatorBounds(left = 0f, right = 100f),
            )

        val bounds = calculator.boundsAt(spec, elapsedMs = calculator.totalDurationMs)

        assertEquals(0f, bounds.left)
        assertEquals(100f, bounds.right)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorMotionPlannerTest" --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorFrameCalculatorTest"
```

Expected:

```text
e: unresolved reference: LiquidIndicatorMotionPlanner
e: unresolved reference: IndicatorBounds
```

- [ ] **Step 3: Write the minimal implementation**

`IndicatorBounds.kt`

```kotlin
package org.wit.vitasense.ui.navigation

data class IndicatorBounds(
    val left: Float,
    val right: Float,
)
```

`LiquidIndicatorMotionPlanner.kt`

```kotlin
package org.wit.vitasense.ui.navigation

enum class MotionDirection {
    LEFT,
    RIGHT,
    NONE,
}

data class LiquidIndicatorMotionSpec(
    val shouldAnimate: Boolean,
    val direction: MotionDirection,
    val start: IndicatorBounds,
    val stretchTarget: IndicatorBounds,
    val finalTarget: IndicatorBounds,
)

class LiquidIndicatorMotionPlanner {
    fun plan(
        start: IndicatorBounds,
        target: IndicatorBounds,
    ): LiquidIndicatorMotionSpec {
        if (start == target) {
            return LiquidIndicatorMotionSpec(
                shouldAnimate = false,
                direction = MotionDirection.NONE,
                start = start,
                stretchTarget = target,
                finalTarget = target,
            )
        }

        return if (target.left > start.left) {
            LiquidIndicatorMotionSpec(
                shouldAnimate = true,
                direction = MotionDirection.RIGHT,
                start = start,
                stretchTarget = IndicatorBounds(left = start.left, right = target.right),
                finalTarget = target,
            )
        } else {
            LiquidIndicatorMotionSpec(
                shouldAnimate = true,
                direction = MotionDirection.LEFT,
                start = start,
                stretchTarget = IndicatorBounds(left = target.left, right = start.right),
                finalTarget = target,
            )
        }
    }
}
```

`MotionEasing.kt`

```kotlin
package org.wit.vitasense.ui.navigation

object MotionEasing {
    fun fastOutSlowIn(input: Float): Float = cubicBezier(input, 0.4f, 0f, 0.2f, 1f)

    private fun cubicBezier(
        input: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val clamped = input.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(14) {
            val midpoint = (low + high) / 2f
            val estimate = cubicBezierComponent(midpoint, x1, x2)
            if (estimate < clamped) low = midpoint else high = midpoint
        }
        val t = (low + high) / 2f
        return cubicBezierComponent(t, y1, y2)
    }

    private fun cubicBezierComponent(
        t: Float,
        a: Float,
        b: Float,
    ): Float {
        val inverse = 1f - t
        return 3f * inverse * inverse * t * a + 3f * inverse * t * t * b + t * t * t
    }
}
```

`LiquidIndicatorFrameCalculator.kt`

```kotlin
package org.wit.vitasense.ui.navigation

import kotlin.math.max

class LiquidIndicatorFrameCalculator(
    private val leadingDurationMs: Long = 220L,
    private val trailingDelayMs: Long = 80L,
    private val trailingDurationMs: Long = 180L,
) {
    val totalDurationMs: Long = max(leadingDurationMs, trailingDelayMs + trailingDurationMs)

    fun boundsAt(
        spec: LiquidIndicatorMotionSpec,
        elapsedMs: Long,
    ): IndicatorBounds {
        if (!spec.shouldAnimate || spec.direction == MotionDirection.NONE) {
            return spec.finalTarget
        }

        val leadingProgress = easedProgress(elapsedMs, delayMs = 0L, durationMs = leadingDurationMs)
        val trailingProgress = easedProgress(elapsedMs, delayMs = trailingDelayMs, durationMs = trailingDurationMs)

        return when (spec.direction) {
            MotionDirection.RIGHT ->
                IndicatorBounds(
                    left = lerp(spec.start.left, spec.finalTarget.left, trailingProgress),
                    right = lerp(spec.start.right, spec.stretchTarget.right, leadingProgress),
                )

            MotionDirection.LEFT ->
                IndicatorBounds(
                    left = lerp(spec.start.left, spec.stretchTarget.left, leadingProgress),
                    right = lerp(spec.start.right, spec.finalTarget.right, trailingProgress),
                )

            MotionDirection.NONE -> spec.finalTarget
        }
    }

    private fun easedProgress(
        elapsedMs: Long,
        delayMs: Long,
        durationMs: Long,
    ): Float {
        val adjusted = (elapsedMs - delayMs).coerceAtLeast(0L)
        val linear = (adjusted.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return MotionEasing.fastOutSlowIn(linear)
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float,
    ): Float = start + (end - start) * fraction
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorMotionPlannerTest" --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorFrameCalculatorTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/navigation/IndicatorBounds.kt app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlanner.kt app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculator.kt app/src/main/java/org/wit/vitasense/ui/navigation/MotionEasing.kt app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlannerTest.kt app/src/test/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculatorTest.kt
git commit -m "feat: add liquid indicator motion math"
```

## Task 4: Integrate The Custom Indicator View

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Write the failing tests**

Extend `MainActivitySmokeTest.kt`:

```kotlin
@Test
fun shows_liquid_indicator_inside_the_floating_tab_bar() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(withId(R.id.bottomTabIndicator)).check(matches(isDisplayed()))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected:

```text
FAILURE: There were failing tests.
androidx.test.espresso.NoMatchingViewException: No views in hierarchy found matching: with id: org.wit.vitasense:id/bottomTabIndicator
```

- [ ] **Step 3: Write the minimal implementation**

`LiquidTabIndicatorView.kt`

```kotlin
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
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val frameCalculator = LiquidIndicatorFrameCalculator()
    private var currentBounds = IndicatorBounds(0f, 0f)
    private var activeSpec: LiquidIndicatorMotionSpec? = null
    private var animationStartNanos = Long.MIN_VALUE
    private var framePosted = false

    private val frameCallback =
        Choreographer.FrameCallback { frameTimeNanos ->
            val spec = activeSpec ?: return@FrameCallback
            if (animationStartNanos == Long.MIN_VALUE) {
                animationStartNanos = frameTimeNanos
            }
            val elapsedMs = (frameTimeNanos - animationStartNanos) / 1_000_000L
            currentBounds = frameCalculator.boundsAt(spec, elapsedMs)
            invalidate()
            if (elapsedMs >= frameCalculator.totalDurationMs) {
                activeSpec = null
                framePosted = false
            } else {
                postNextFrame()
            }
        }

    fun setIndicatorColor(color: Int) {
        paint.color = color
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
        rect.set(currentBounds.left, 0f, currentBounds.right, height.toFloat())
        val radius = height / 2f
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimation()
    }

    private fun postNextFrame() {
        if (framePosted || !isAttachedToWindow) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun cancelAnimation() {
        if (framePosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            framePosted = false
        }
        activeSpec = null
        animationStartNanos = Long.MIN_VALUE
    }
}
```

Update `view_floating_bottom_tabs.xml` so the card contains a `FrameLayout`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/floatingBottomTabsCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:contentDescription="@null"
    app:cardBackgroundColor="@android:color/transparent"
    app:cardCornerRadius="32dp"
    app:cardElevation="10dp">

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_floating_tab_bar">

        <org.wit.vitasense.ui.navigation.LiquidTabIndicatorView
            android:id="@+id/bottomTabIndicator"
            android:layout_width="match_parent"
            android:layout_height="64dp" />

        <LinearLayout
            android:id="@+id/layoutTabForeground"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/tabHome"
                android:layout_width="0dp"
                android:layout_height="64dp"
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
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabTrends"
                android:layout_width="0dp"
                android:layout_height="64dp"
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
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabAssessment"
                android:layout_width="0dp"
                android:layout_height="64dp"
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
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/tabMood"
                android:layout_width="0dp"
                android:layout_height="64dp"
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
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

        </LinearLayout>

    </FrameLayout>

</com.google.android.material.card.MaterialCardView>
```

Update `MainActivity.kt` to animate and snap the indicator:

```kotlin
private val motionPlanner = LiquidIndicatorMotionPlanner()

private fun bindFloatingTabs() {
    findViewById<View>(R.id.layoutTabForeground).post {
        renderBottomTabs(selectedBottomDestination, animate = false)
    }
    findViewById<View>(R.id.tabHome).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.HOME)
    }
    findViewById<View>(R.id.tabTrends).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.TRENDS)
    }
    findViewById<View>(R.id.tabAssessment).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.ASSESSMENT)
    }
    findViewById<View>(R.id.tabMood).setOnClickListener {
        navigateToBottomDestination(BottomTabDestination.MOOD)
    }
}

private fun renderBottomTabs(
    selected: BottomTabDestination,
    animate: Boolean,
) {
    val indicator = findViewById<LiquidTabIndicatorView>(R.id.bottomTabIndicator)
    indicator.setIndicatorColor(resolveIndicatorColor())

    BottomTabDestination.entries.forEach { destination ->
        val isSelected = destination == selected
        val color = if (isSelected) resolveSelectedTabContentColor() else resolveUnselectedTabContentColor()
        findViewById<ImageView>(destination.iconViewId).imageTintList = ColorStateList.valueOf(color)
        findViewById<TextView>(destination.labelViewId).setTextColor(color)
    }

    val targetView = findViewById<View>(selected.tabViewId)
    val targetBounds = IndicatorBounds(targetView.left.toFloat(), targetView.right.toFloat())
    if (animate) {
        val spec = motionPlanner.plan(indicator.currentBounds(), targetBounds)
        indicator.animateWith(spec)
    } else {
        indicator.snapTo(targetBounds)
    }
}

private fun resolveIndicatorColor(): Int {
    val isNight =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    return ContextCompat.getColor(
        this,
        if (isNight) R.color.vs_dark_primary_500 else R.color.vs_primary_900,
    )
}

private fun resolveSelectedTabContentColor(): Int {
    val isNight =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    return ContextCompat.getColor(
        this,
        if (isNight) R.color.vs_dark_text_primary else R.color.white,
    )
}

private fun resolveUnselectedTabContentColor(): Int {
    val isNight =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    return ContextCompat.getColor(
        this,
        if (isNight) R.color.vs_dark_text_secondary else R.color.vs_text_secondary,
    )
}
```

Replace the destination listener and navigation method with these exact bodies:

```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    BottomTabDestination.fromDestinationId(destination.id)?.let { matched ->
        selectedBottomDestination = matched
        renderBottomTabs(matched, animate = false)
    }
}

override fun navigateToBottomDestination(destination: BottomTabDestination) {
    if (
        selectedBottomDestination == destination &&
        navController.currentDestination?.id == destination.navDestinationId
    ) {
        return
    }
    selectedBottomDestination = destination
    navController.navigate(
        destination.navDestinationId,
        null,
        navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        },
    )
    renderBottomTabs(destination, animate = true)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```powershell
.\gradlew :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorMotionPlannerTest" --tests "org.wit.vitasense.ui.navigation.LiquidIndicatorFrameCalculatorTest"
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/MainActivity.kt app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git commit -m "feat: animate floating bottom tab indicator"
```

## Task 5: Remove The Old System Bottom Navigation And Verify The Build

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- Delete: `app/src/main/res/menu/bottom_nav_menu.xml`
- Delete: `app/src/main/res/color/bottom_nav_color.xml`

- [ ] **Step 1: Write the failing instrumentation regression**

Extend `MainActivitySmokeTest.kt`:

```kotlin
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist

@Test
fun no_longer_contains_the_legacy_bottom_navigation_view() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(withId(R.id.bottomNav)).check(doesNotExist())
}
```

- [ ] **Step 2: Run the regression to verify it fails**

Run:

```powershell
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected:

```text
FAILURE: There were failing tests.
java.lang.AssertionError: View is present in the hierarchy: with id: org.wit.vitasense:id/bottomNav
```

- [ ] **Step 3: Write the minimal cleanup implementation**

Replace `activity_main.xml` with the final floating-only version:

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

Delete:

```text
app/src/main/res/menu/bottom_nav_menu.xml
app/src/main/res/color/bottom_nav_color.xml
```

Remove the old style from `themes.xml`:

```xml
<style name="Widget.VitaSense.BottomNavigation.ActiveIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:color">@color/vs_primary_100</item>
</style>
```

Replace the window-inset block in `MainActivity.kt` with this exact code so content is not obscured:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

    binding.floatingBottomTabs.root.post {
        val bottomSpacing =
            binding.floatingBottomTabs.root.height +
                (binding.floatingBottomTabs.root.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin +
                systemBars.bottom
        binding.navHost.updatePadding(bottom = bottomSpacing)
    }
    insets
}
```

- [ ] **Step 4: Run the full verification**

Run:

```powershell
.\gradlew :app:testDebugUnitTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest
.\gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/res/values/themes.xml app/src/main/java/org/wit/vitasense/MainActivity.kt app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git rm app/src/main/res/menu/bottom_nav_menu.xml app/src/main/res/color/bottom_nav_color.xml
git commit -m "refactor: remove legacy bottom navigation"
```
