# Profile Secondary Pages And Tab Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert auth, appearance, and settings/import into true secondary pages with back navigation, hide the floating tab bar on those pages with animation, replace the Home icon, and fix Home/Profile top-level navigation consistency.

**Architecture:** Keep `Home / Trends / Mood / Profile` as the only top-level destinations in the main nav graph. Move Appearance into a new secondary fragment, reuse `SettingsFragment` as the dedicated settings/import secondary page, and let `MainActivity` centrally decide whether the floating tab shell is shown or hidden based on the active destination.

**Tech Stack:** Android XML + Fragments, Navigation Component, ViewBinding, Material 3 widgets, Kotlin Flow, JUnit, Android instrumentation tests.

---

### Task 1: Add secondary destinations and floating-tab visibility rules

**Files:**
- Modify: `app/src/main/res/navigation/main_nav_graph.xml`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

- [ ] **Step 1: Write/extend the failing destination-shell tests**

Add assertions for:
- `appearanceFragment` is not mapped to a bottom tab
- top-level fragments still map correctly
- `MainActivity` hides the floating tab for `authFragment`, `appearanceFragment`, and `settingsFragment`

- [ ] **Step 2: Run the focused tests and verify they fail for the new behavior**

Run:
`./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.navigation.BottomTabDestinationTest`

Expected:
FAIL because `appearanceFragment` does not exist yet or the shell visibility logic is missing.

- [ ] **Step 3: Add the new secondary destination and central shell rules**

Implement:
- new `appearanceFragment` in `main_nav_graph.xml`
- in `MainActivity`, create helpers like:
  - `isTopLevelDestination(destinationId: Int): Boolean`
  - `shouldShowFloatingTabs(destinationId: Int): Boolean`
- animate the floating tab container down on secondary pages and up on top-level pages
- disable interaction while hidden

- [ ] **Step 4: Keep bottom-tab mapping limited to the four top-level destinations**

`BottomTabDestination.fromDestinationId(...)` must still return only:
- `dashboardFragment`
- `trendsFragment`
- `moodFragment`
- `profileFragment`

- [ ] **Step 5: Re-run the focused tests**

Run:
`./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.navigation.BottomTabDestinationTest`

Expected:
PASS

### Task 2: Refine the auth secondary page and Home/Profile top-level navigation

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/auth/AuthFragment.kt`
- Modify: `app/src/main/res/layout/fragment_auth.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`

- [ ] **Step 1: Add a failing test or assertion for Home avatar navigation consistency**

Cover:
- signed-in Home avatar goes to `Profile`
- tapping the Home bottom tab after that returns to `Home`

- [ ] **Step 2: Add back navigation and mode-emphasis behavior to Auth**

Implement:
- a back button row at the top of `fragment_auth.xml`
- back button click -> `findNavController().navigateUp()`
- mode styling logic so:
  - in Login mode, `Register` is visually de-emphasized
  - in Register mode, `Login` is visually de-emphasized

- [ ] **Step 3: Route signed-in Home avatar through top-level navigation**

Replace the direct `findNavController().navigate(R.id.profileFragment)` path in `DashboardFragment` with the same top-level navigator path used by the floating tab shell.

- [ ] **Step 4: Re-run focused auth/navigation tests**

Run:
`./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.dashboard.DashboardViewModelTest`

Expected:
PASS with no regression in Home auth header behavior.

### Task 3: Split Profile into landing, Appearance, and Settings & Import secondary pages

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/profile/AppearanceFragment.kt`
- Create: `app/src/main/res/layout/fragment_appearance.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`
- Modify: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/wit/vitasense/ui/profile/ProfileViewModelTest.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Create the failing navigation/UI expectations**

Cover:
- `Profile` shows Appearance and Settings & Import entries instead of inline controls
- `AppearanceFragment` owns theme selection
- `SettingsFragment` stays responsible for import/clear/data notes

- [ ] **Step 2: Move theme controls into `AppearanceFragment`**

Implement:
- title + back button
- theme summary
- theme family cards
- light/dark toggle
- reuse `SettingsViewModel` or existing theme-setting flows rather than duplicating repository logic

- [ ] **Step 3: Simplify `ProfileFragment` into a landing page**

Implement:
- account summary card
- sign-in/log-out controls
- `Appearance` entry row with chevron
- `Settings & Import` entry row with chevron
- keep privacy/disclaimer summary content lightweight

- [ ] **Step 4: Repurpose `SettingsFragment` as the dedicated secondary page**

Implement:
- title + back button
- demo import buttons
- clear-all-data
- data/privacy/disclaimer sections
- remove theme controls from this page

- [ ] **Step 5: Re-run focused profile/settings tests**

Run:
`./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest --tests org.wit.vitasense.ui.settings.SettingsViewModelTest`

Expected:
PASS

### Task 4: Update tab icon and run regression verification

**Files:**
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- Modify: `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- Modify: any touched files from Tasks 1-3

- [ ] **Step 1: Replace the Home icon**

Change the Home tab icon from the current eye-like drawable to a house-style Android drawable.

- [ ] **Step 2: Re-run the floating-tab and shell smoke coverage**

Run:
`./gradlew.bat :app:assembleDebugAndroidTest`

Expected:
BUILD SUCCESSFUL

- [ ] **Step 3: Run full unit tests and debug build**

Run:
`./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected:
BUILD SUCCESSFUL

- [ ] **Step 4: Do not commit unless the user explicitly asks**

Keep the worktree uncommitted after verification.
