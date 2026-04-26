# Floating Bottom Tab Redesign

## Summary

Replace the current `BottomNavigationView` with a floating custom bottom tab that matches the structure and motion model defined in `floating-bottom-tab-implementation.md`, while preserving the app's four existing top-level destinations:

- `Home`
- `Trends`
- `Assessment`
- `Mood`

The redesign is limited to the tab bar's structure, rendering, navigation wiring, and animation behavior. It does not add new destinations, secondary states, or extra navigation modes.

## Goals

- Replace the system bottom navigation with a floating XML-based tab bar.
- Follow the document's layout layering model:
  - floating outer card
  - inner board
  - custom liquid indicator layer
  - foreground tab cells
- Preserve the current top-level destinations and their meaning.
- Fix the current coupling issue where `Quick Mood Log` relies on a concrete bottom navigation widget.
- Use the existing VitaSense theme palette instead of the reference document's black-and-white tokens.
- Keep the implementation compatible with the existing `XML + Fragment + Navigation` architecture.

## Non-Goals

- No change to page information architecture.
- No addition of new tabs, badges, nested actions, or long-press behaviors.
- No migration to Compose.
- No redesign of page content outside the bottom tab area.

## Current State

The app currently uses:

- `app/src/main/res/layout/activity_main.xml`
  - vertical layout with a `FragmentContainerView`
  - a standard `BottomNavigationView` anchored at the bottom
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
  - `setupWithNavController(...)` for bottom navigation wiring
- `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
  - direct access to `BottomNavigationView` for the `Quick Mood Log` shortcut

This creates two problems:

1. The bottom bar cannot match the floating liquid-tab implementation described in the reference document.
2. `DashboardFragment` is coupled to a specific widget type, which previously caused incorrect top-level navigation state behavior.

## Target Architecture

### Layout

`activity_main.xml` will be restructured to use a root `ConstraintLayout` with:

- a `FragmentContainerView` filling the main content area
- a bottom `include` for `view_floating_bottom_tabs.xml`

The bottom tab include will be fixed to the activity root with:

- start margin `20dp`
- end margin `20dp`
- bottom margin `16dp`

The tab bar must stay outside scrolling content and visually float above the screen content.

### Floating Tab View Hierarchy

`view_floating_bottom_tabs.xml` will contain:

1. `MaterialCardView`
2. inner `FrameLayout`
3. board background drawable
4. `LiquidTabIndicatorView`
5. horizontal foreground container with four equal-width tab cells

Each tab cell will follow the reference proportions:

- height `64dp`
- weight-based equal width
- icon size `18dp`
- icon-label spacing `4dp`
- text size `11sp`
- bold label
- `8dp` inner padding

### Navigation State

The bottom tab will no longer depend on `setupWithNavController(...)`.

Instead, `MainActivity` will own:

- the `NavController`
- the currently selected top-level bottom destination
- the floating tab view references

A dedicated top-level destination model will represent:

- `HOME`
- `TRENDS`
- `ASSESSMENT`
- `MOOD`

Each model value maps to the existing navigation graph destinations:

- `HOME -> R.id.dashboardFragment`
- `TRENDS -> R.id.trendsFragment`
- `ASSESSMENT -> R.id.assessmentFragment`
- `MOOD -> R.id.moodFragment`

### Navigation Flow

All tab presses will flow through one activity-owned entry point:

- `selectBottomDestination(destination: BottomTabDestination, animate: Boolean = true)`

This function is responsible for:

1. ignoring repeated selection of the current top-level destination
2. navigating with top-level `NavOptions`
3. updating selected visual state
4. animating the indicator

`DashboardFragment` will no longer read or mutate a `BottomNavigationView`.
Instead, it will call an activity API for top-level navigation when the `Quick Mood Log` shortcut is pressed.

This keeps fragments independent of the specific bottom-bar implementation.

### Insets And Content Safety

Because the new bottom bar floats above content, the activity must reserve bottom space for the content container.

The reserved bottom area must account for:

- the floating tab height
- its bottom margin
- bottom system bar insets

This prevents content from being obscured by the floating tab.

## Liquid Indicator Design

### Motion Model

The redesign will keep the reference document's animation model:

- the indicator is drawn independently from tab cells
- the indicator moves by animating `left` and `right` bounds
- the leading edge moves first
- the trailing edge follows after a delay
- the result is a stretch-and-recover liquid effect

The implementation will not use plain `translationX`.

### Rendering Stack

The indicator is rendered beneath the foreground icons and labels so the pill appears to flow behind the tab content.

The custom rendering pipeline is:

- `IndicatorBounds`
- `LiquidIndicatorMotionPlanner`
- `LiquidIndicatorFrameCalculator`
- `MotionEasing`
- `LiquidTabIndicatorView`

### Frame Driver

The indicator will use a frame-based `Choreographer` loop rather than `ValueAnimator`.

Reason:

- the reference implementation explicitly avoids `ValueAnimator`
- the prior issue described in the reference document involved animator-scale-disabled environments
- frame-based drawing gives deterministic control over each geometry update

### Timing

The initial implementation should keep the reference timing constants:

- leading duration `220ms`
- trailing delay `80ms`
- trailing duration `180ms`
- easing `cubic-bezier(0.4, 0.0, 0.2, 1.0)`

These values should remain fixed for the first implementation pass and change only if visual validation proves a concrete issue.

## Theme Mapping

The component must follow the VitaSense design language rather than the reference document's original black-and-white palette.

Recommended mapping:

- floating board background: `vs_surface`
- floating board stroke: `vs_border_soft`
- selected indicator: `vs_primary_900`
- unselected icon and label: `vs_text_secondary`
- selected icon and label: `white`

If the app is in dark mode, the implementation should use the parallel `vs_dark_*` tokens where appropriate.

## File Plan

### New Files

- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/IndicatorBounds.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorMotionPlanner.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidIndicatorFrameCalculator.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/MotionEasing.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`

### Modified Files

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`

Additional resource changes may be needed for drawables, strings, or style cleanup if existing bottom navigation resources become unused.

## Behavioral Requirements

- The app still opens on `Home`.
- Tapping each floating tab opens the matching existing top-level fragment.
- Re-selecting the active top-level tab does not trigger redundant navigation or animation.
- `Quick Mood Log` from `Home` opens `Mood`.
- After entering `Mood` through `Quick Mood Log`, tapping `Home` returns to `Home` normally.
- Back navigation must keep the visual selection synchronized with the visible destination.
- The indicator must snap into place on first layout and animate only on subsequent selection changes.

## Testing Strategy

### Unit Tests

Add unit tests for:

- destination mapping logic
- motion planning direction and stretch-target behavior
- frame calculation at early, delayed, and completed phases
- non-animated behavior when the target equals the current position

### Integration And Behavior Checks

Verify:

- each of the four tabs opens the correct fragment
- `Quick Mood Log -> Mood -> Home` works without getting stuck
- selected tab state stays synchronized after destination changes

### Build Verification

At minimum:

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

## Risks And Mitigations

### Risk: layout overlap with fragment content

Mitigation:

- explicitly manage bottom padding using measured tab height and window insets

### Risk: navigation state drift

Mitigation:

- centralize all top-level tab selection in `MainActivity`
- observe destination changes and resync selection when needed

### Risk: animation looks correct only on one screen size

Mitigation:

- calculate target bounds from actual tab view positions rather than hardcoded widths

### Risk: fragment code remains coupled to view implementation

Mitigation:

- expose a narrow activity-owned navigation API instead of a concrete widget reference

## Implementation Sequence

1. Replace the bottom bar layout with the floating include and static tab structure.
2. Add a top-level navigation abstraction in `MainActivity`.
3. Decouple `DashboardFragment` from `BottomNavigationView`.
4. Add the liquid indicator motion stack.
5. Add and run unit tests for motion and selection behavior.
6. Verify app build and navigation behavior.
