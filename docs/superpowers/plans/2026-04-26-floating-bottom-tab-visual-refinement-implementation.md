# Floating Bottom Tab Visual Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing custom floating bottom tab read as a transparent host with a centered floating pill, while shrinking the active-state capsule and preserving current navigation and bottom safe spacing.

**Architecture:** Keep the current `XML + Fragment + Navigation` stack and the existing `MainActivity`-owned top-level navigation model. Refactor the bottom-tab layout into a full-width transparent host plus a wrap-content inner pill, move tab sizing into shared dimension resources, and compute active indicator bounds through a small pure-Kotlin geometry helper so the selected capsule becomes visually inset without changing tap targets.

**Tech Stack:** Kotlin, Android Views/XML, Material3, ViewBinding, Fragment Navigation, JUnit4, Espresso, local Gradle 9.3.1 distribution

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

Current workspace constraint:

- `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest` can stop before execution if the local offline cache is missing `androidx.room:room-testing:2.7.2` or `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0`.
- `:app:assembleDebug` is the reliable compile gate in the current offline environment.
- Keep the test files in sync anyway, then rerun the listed test commands after dependencies are restored.

## File Map

### Create

- `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometry.kt`
  - Pure Kotlin helper that insets raw tab bounds into smaller active-indicator bounds.
- `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometryTest.kt`
  - Unit tests for symmetric inset, clamping, and negative-inset behavior.

### Modify

- `app/src/main/res/layout/activity_main.xml`
  - Keep the bottom include full-width and remove side margins so only the inner pill defines the visible footprint.
- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
  - Turn the root into a transparent host and center a wrap-content floating pill inside it.
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
  - Use the refined pill radius token.
- `app/src/main/res/values/dimens.xml`
  - Add shared dimensions for board radius, board padding, tab item size, and indicator insets.
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
  - Apply vertical inset to the custom indicator, derive horizontally inset bounds, and keep bottom safe spacing behavior unchanged.
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
  - Draw the active capsule with top and bottom inset instead of filling the entire tab cell height.
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
  - Verify the transparent host and inner floating pill are both present.

### Reuse Without Modification

- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
  - Regression coverage for top-level tab navigation.
- `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`
  - Regression coverage for `Quick Mood Log -> Mood -> Home`.

## Task 1: Rebuild The Layout As A Transparent Host Plus Centered Pill

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Write the failing instrumentation smoke test**

Update `MainActivitySmokeTest.kt` to expect the new transparent host and inner board ids:

```kotlin
package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun shows_centered_floating_pill_inside_transparent_host() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.floatingBottomTabsHost)).check(matches(isDisplayed()))
        onView(withId(R.id.floatingBottomTabsCard)).check(matches(isDisplayed()))
        onView(withId(R.id.floatingBottomTabsBoard)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomTabIndicator)).check(matches(isDisplayed()))
        onView(withId(R.id.tabHome)).check(matches(isDisplayed()))
        onView(withId(R.id.tabTrends)).check(matches(isDisplayed()))
        onView(withId(R.id.tabAssessment)).check(matches(isDisplayed()))
        onView(withId(R.id.tabMood)).check(matches(isDisplayed()))
    }

    @Test
    fun no_longer_contains_the_legacy_bottom_navigation_view() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(isAssignableFrom(BottomNavigationView::class.java)).check(doesNotExist())
    }
}
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected in a fully provisioned test cache:

```text
e: Unresolved reference: floatingBottomTabsHost
e: Unresolved reference: floatingBottomTabsBoard
```

If the offline cache is still incomplete, expect the command to stop earlier during dependency resolution for `room-testing` or `kotlinx-coroutines-test`.

- [ ] **Step 3: Write the minimal layout refinement**

Replace `dimens.xml` with:

```xml
<resources>
    <dimen name="vs_trend_chart_height">160dp</dimen>
    <dimen name="vs_bottom_tab_board_radius">36dp</dimen>
    <dimen name="vs_bottom_tab_board_horizontal_padding">8dp</dimen>
    <dimen name="vs_bottom_tab_board_vertical_padding">6dp</dimen>
    <dimen name="vs_bottom_tab_item_width">76dp</dimen>
    <dimen name="vs_bottom_tab_item_height">60dp</dimen>
    <dimen name="vs_bottom_tab_indicator_horizontal_inset">10dp</dimen>
    <dimen name="vs_bottom_tab_indicator_vertical_inset">7dp</dimen>
</resources>
```

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
        android:layout_marginBottom="16dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

Replace `view_floating_bottom_tabs.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/floatingBottomTabsHost"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:contentDescription="@null">

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/floatingBottomTabsCard"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        app:cardBackgroundColor="@android:color/transparent"
        app:cardCornerRadius="@dimen/vs_bottom_tab_board_radius"
        app:cardElevation="10dp"
        app:strokeWidth="0dp">

        <FrameLayout
            android:id="@+id/floatingBottomTabsBoard"
            android:layout_width="wrap_content"
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
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal">

                <LinearLayout
                    android:id="@+id/tabHome"
                    android:layout_width="@dimen/vs_bottom_tab_item_width"
                    android:layout_height="@dimen/vs_bottom_tab_item_height"
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
                    android:layout_width="@dimen/vs_bottom_tab_item_width"
                    android:layout_height="@dimen/vs_bottom_tab_item_height"
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
                    android:layout_width="@dimen/vs_bottom_tab_item_width"
                    android:layout_height="@dimen/vs_bottom_tab_item_height"
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
                    android:layout_width="@dimen/vs_bottom_tab_item_width"
                    android:layout_height="@dimen/vs_bottom_tab_item_height"
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

</FrameLayout>
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

- [ ] **Step 4: Run the compile gate and smoke test**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:assembleDebug
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
```

Expected:

```text
:app:assembleDebug -> BUILD SUCCESSFUL
:app:connectedDebugAndroidTest -> BUILD SUCCESSFUL once the test dependency cache is restored and a device/emulator is connected
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/main/res/drawable/bg_floating_tab_bar.xml app/src/main/res/values/dimens.xml app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt
git commit -m "refactor: center floating bottom tab pill"
```

## Task 2: Inset The Active Indicator Without Shrinking Tap Targets

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometry.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometryTest.kt`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`

- [ ] **Step 1: Write the failing unit test**

Create `BottomTabIndicatorGeometryTest.kt`:

```kotlin
package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomTabIndicatorGeometryTest {
    @Test
    fun inset_bounds_shrinks_both_sides_evenly() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 0f, right = 76f),
                horizontalInsetPx = 10f,
            )

        assertEquals(10f, result.left, 0.001f)
        assertEquals(66f, result.right, 0.001f)
    }

    @Test
    fun inset_bounds_clamps_at_half_width() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 20f, right = 60f),
                horizontalInsetPx = 40f,
            )

        assertEquals(40f, result.left, 0.001f)
        assertEquals(40f, result.right, 0.001f)
    }

    @Test
    fun inset_bounds_treats_negative_inset_as_zero() {
        val result =
            BottomTabIndicatorGeometry.insetBounds(
                bounds = IndicatorBounds(left = 12f, right = 88f),
                horizontalInsetPx = -6f,
            )

        assertEquals(12f, result.left, 0.001f)
        assertEquals(88f, result.right, 0.001f)
    }
}
```

- [ ] **Step 2: Run the unit test to verify it fails**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.BottomTabIndicatorGeometryTest"
```

Expected in a fully provisioned test cache:

```text
e: Unresolved reference: BottomTabIndicatorGeometry
```

If the offline cache is still incomplete, expect the command to stop earlier during dependency resolution for `room-testing` or `kotlinx-coroutines-test`.

- [ ] **Step 3: Write the minimal geometry and rendering implementation**

Create `BottomTabIndicatorGeometry.kt`:

```kotlin
package org.wit.vitasense.ui.navigation

object BottomTabIndicatorGeometry {
    fun insetBounds(
        bounds: IndicatorBounds,
        horizontalInsetPx: Float,
    ): IndicatorBounds {
        val width = (bounds.right - bounds.left).coerceAtLeast(0f)
        val safeInset = horizontalInsetPx.coerceAtLeast(0f).coerceAtMost(width / 2f)
        return IndicatorBounds(
            left = bounds.left + safeInset,
            right = bounds.right - safeInset,
        )
    }
}
```

Update `LiquidTabIndicatorView.kt`:

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
    private val indicatorRect = RectF()
    private val indicatorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val frameCalculator = LiquidIndicatorFrameCalculator()

    private var currentBounds = IndicatorBounds(0f, 0f)
    private var activeSpec: LiquidIndicatorMotionSpec? = null
    private var animationStartNanos = Long.MIN_VALUE
    private var frameCallbackPosted = false
    private var verticalInsetPx = 0f

    private val frameCallback =
        Choreographer.FrameCallback { frameTimeNanos ->
            frameCallbackPosted = false
            val spec = activeSpec ?: return@FrameCallback

            if (animationStartNanos == Long.MIN_VALUE) {
                animationStartNanos = frameTimeNanos
            }

            val elapsedMs = (frameTimeNanos - animationStartNanos) / 1_000_000L
            currentBounds = frameCalculator.boundsAt(spec, elapsedMs)
            invalidate()

            if (elapsedMs >= frameCalculator.totalDurationMs) {
                activeSpec = null
            } else {
                postNextFrame()
            }
        }

    fun setIndicatorColor(color: Int) {
        indicatorPaint.color = color
        invalidate()
    }

    fun setVerticalInsetPx(insetPx: Float) {
        verticalInsetPx = insetPx.coerceAtLeast(0f)
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
        val safeInset = verticalInsetPx.coerceAtMost(height / 2f)
        val top = safeInset
        val bottom = (height.toFloat() - safeInset).coerceAtLeast(top)
        indicatorRect.set(currentBounds.left, top, currentBounds.right, bottom)
        val radius = (bottom - top) / 2f
        canvas.drawRoundRect(indicatorRect, radius, radius, indicatorPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimation()
    }

    private fun postNextFrame() {
        if (frameCallbackPosted || !isAttachedToWindow) {
            return
        }
        frameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun cancelAnimation() {
        if (frameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameCallbackPosted = false
        }
        activeSpec = null
        animationStartNanos = Long.MIN_VALUE
    }
}
```

Update `MainActivity.kt` by adding these properties near the existing field declarations:

```kotlin
private val motionPlanner = LiquidIndicatorMotionPlanner()
private val bottomTabIndicatorHorizontalInsetPx by lazy {
    resources.getDimension(R.dimen.vs_bottom_tab_indicator_horizontal_inset)
}
private val bottomTabIndicatorVerticalInsetPx by lazy {
    resources.getDimension(R.dimen.vs_bottom_tab_indicator_vertical_inset)
}
```

Replace `bindFloatingTabs()` with:

```kotlin
private fun bindFloatingTabs() {
    findViewById<LiquidTabIndicatorView>(R.id.bottomTabIndicator)
        .setVerticalInsetPx(bottomTabIndicatorVerticalInsetPx)

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
```

Add this helper under `renderBottomTabs(...)`:

```kotlin
private fun indicatorBoundsFor(destination: BottomTabDestination): IndicatorBounds {
    val targetView = findViewById<View>(destination.tabViewId)
    val rawBounds = IndicatorBounds(targetView.left.toFloat(), targetView.right.toFloat())
    return BottomTabIndicatorGeometry.insetBounds(
        bounds = rawBounds,
        horizontalInsetPx = bottomTabIndicatorHorizontalInsetPx,
    )
}
```

Replace the target-bounds block inside `renderBottomTabs(...)` with:

```kotlin
val targetBounds = indicatorBoundsFor(selected)
if (animate) {
    val spec = motionPlanner.plan(indicator.currentBounds(), targetBounds)
    indicator.animateWith(spec)
} else {
    indicator.snapTo(targetBounds)
}
```

- [ ] **Step 4: Run the unit test and compile gate**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.BottomTabIndicatorGeometryTest"
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:assembleDebug
```

Expected:

```text
:app:testDebugUnitTest -> BUILD SUCCESSFUL once the test dependency cache is restored
:app:assembleDebug -> BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometry.kt app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabIndicatorGeometryTest.kt app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt app/src/main/java/org/wit/vitasense/MainActivity.kt
git commit -m "refactor: shrink floating tab active indicator"
```

## Task 3: Re-run Navigation Regressions And Perform Visual Verification

**Files:**
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`

- [ ] **Step 1: Run the existing navigation regressions**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.MainActivitySmokeTest
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.FloatingBottomTabNavigationTest
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.wit.vitasense.ui.HomeQuickMoodNavigationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the final compile verification**

Run after the command preamble:

```powershell
& 'D:\1\yidong\mid_1\project\.tmp\gradle-9.3.1-local\bin\gradle.bat' :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Perform the manual visual check on a running emulator/device**

Confirm all of the following in the app:

- The bottom tab only looks opaque in the center pill area.
- The left and right area around the pill is visually transparent.
- The selected dark capsule is shorter and narrower than the tab cell.
- The selected dark capsule remains rounded during animation.
- `Home`, `Trends`, `Assessment`, and `Mood` still navigate correctly.
- `Quick Mood Log -> Mood -> Home` still works.
- Page content remains fully readable above the floating tab.
