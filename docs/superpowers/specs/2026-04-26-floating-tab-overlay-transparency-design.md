# Floating Tab Overlay Transparency Redesign

## Summary

Replace the current narrowed floating-tab refinement with a 1:1 structural replica of the overlay-transparency model defined in `floating-tab-overlay-transparency-implementation.md`, while keeping the VitaSense theme colors instead of the reference document's white-and-black palette.

This redesign is specifically about the bottom tab's layering, sizing, transparency behavior, and equal-width item placement. It does not change the app's four top-level destinations or the existing `XML + Fragment + Navigation` architecture.

## Goals

- Restore a full-width transparent overlay host for the bottom tab.
- Keep the visible solid board on the inner layer only.
- Make the non-solid area around the board visually transparent so page content remains visible behind it.
- Place `Home`, `Trends`, `Assessment`, and `Mood` in equal-width cells, evenly distributed and visually centered across the tab bar.
- Preserve the current top-level navigation behavior and liquid active-indicator motion.
- Keep VitaSense theme colors instead of switching to the document's literal white/black palette.

## Non-Goals

- No change to the four destinations or their navigation semantics.
- No migration to Compose.
- No touch-through behavior for transparent overlay regions.
- No redesign of fragment content outside the bottom tab area.
- No change to the document's overlay structure just to preserve the previously added narrowed-pill layout.

## Verified Difference From The Current Implementation

The current implementation diverges from the provided document in one critical way:

- it collapses the visible board into a `wrap_content` centered pill with fixed-width items

The document requires:

- a `match_parent` transparent outer overlay
- a `match_parent` inner solid board
- equal-width weighted tab cells spanning the whole floating bar width

That means the current narrowed-pill structure must be removed rather than incrementally adjusted.

## Target Layout Structure

### Activity Root

`activity_main.xml` keeps the same high-level overlay pattern:

- the main content container fills the screen to the bottom edge
- the floating tab include is a sibling layered above the content
- the include stays constrained to the activity bottom

The include margins must match the document structure:

- start margin `20dp`
- end margin `20dp`
- bottom margin `16dp`

This preserves the "floating" air gap while keeping the overlay full-width inside those margins.

### Transparent Outer Shell

`view_floating_bottom_tabs.xml` must use a root `MaterialCardView` with:

- `layout_width="match_parent"`
- transparent Android background
- transparent card background
- rounded corner radius
- elevation/shadow enabled
- no stroke on the outer card

The outer card exists to provide floating shadow and transparent overlay bounds. It must not paint the solid board color.

### Solid Inner Board

Inside the outer card, a `FrameLayout` must:

- use `layout_width="match_parent"`
- hold the board background drawable
- apply inner padding
- host the active indicator and foreground tab row

This inner `FrameLayout` is the only solid visual surface for the tab bar.

## Foreground Tab Placement

The four top-level items must be laid out exactly in the document's structural style:

- foreground row width `match_parent`
- horizontal orientation
- each item width `0dp`
- each item weight `1`
- each item height `64dp`
- icon and label centered vertically and horizontally within the item

This ensures:

- the four items are evenly distributed
- the whole row remains centered across the floating bar
- the tab layout no longer looks like a small centered cluster

The labels remain:

- `Home`
- `Trends`
- `Assessment`
- `Mood`

If `Assessment` becomes visually cramped at the current font size, the first adjustment should be cell height or internal padding, not abandoning the weighted full-width structure.

## Transparency Model

The redesign must preserve the exact transparency behavior described in the document:

1. Main content exists behind the overlay because the content container fills to the bottom.
2. The floating tab is a later-drawn sibling layered above content.
3. The outer overlay shell is transparent.
4. Only the inner board and active indicator actually obscure pixels.

This is visual transparency only.

Transparent parts of the floating tab still belong to a real Android view hierarchy and do not become touch-through regions.

## Safe Bottom Spacing

The document's example uses a fixed content bottom padding value because it is based on a specific `NestedScrollView` page.

VitaSense uses a multi-fragment navigation host, so the implementation must preserve the current dynamic bottom-spacing approach in `MainActivity`:

- measure the floating tab root height
- add the include's bottom margin
- add system bar inset
- apply the result as bottom padding to the navigation host

This is considered structurally equivalent to the document's intent:

- content can remain visible behind the overlay
- last-screen content is not permanently hidden under the floating tab

## Color Mapping

The user chose to keep theme colors rather than copy the document's exact colors.

Required mapping:

- outer overlay shell: transparent
- inner solid board: `?attr/colorSurface`
- board outline: `?attr/colorOutline`
- active indicator: existing VitaSense selected-tab primary color logic
- selected icon/label color: existing selected content color logic
- unselected icon/label color: existing unselected content color logic

Dark mode support must continue to use the current `MainActivity` color resolution logic.

## Indicator Behavior

The liquid indicator motion model remains in place.

The redesign does not remove:

- `LiquidIndicatorMotionPlanner`
- `LiquidIndicatorFrameCalculator`
- `LiquidTabIndicatorView`

However, the indicator's target width must match the restored equal-width full-row geometry.

The implementation may keep a modest inset for the active indicator if needed to preserve visual polish, but it must not visually reintroduce the narrow centered-pill layout that the user rejected.

## File Impact

### Primary Files To Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`

### Navigation Files Expected To Remain Functionally Stable

- `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/HomeQuickMoodNavigationTest.kt`

## Acceptance Criteria

- The floating tab uses a full-width transparent overlay shell rather than a narrowed `wrap_content` pill host.
- The visible solid board is painted only on the inner layer.
- The area outside the solid board remains visually transparent.
- `Home`, `Trends`, `Assessment`, and `Mood` are evenly distributed and centered across the floating tab row.
- Existing navigation behavior remains unchanged.
- The app still compiles with `:app:assembleDebug` in the current environment.

## Risks And Mitigations

### Risk: `Assessment` label feels cramped in equal-width layout

Mitigation:

- keep the weighted full-row layout
- adjust internal padding before changing the structural width strategy

### Risk: reverting from `wrap_content` board to full-width overlay weakens the compact look

Mitigation:

- follow the document literally for structure
- rely on transparent outer shell and inner board styling to preserve floating separation

### Risk: content appears too close to the tab after reverting width strategy

Mitigation:

- keep the current dynamic bottom-spacing logic in `MainActivity`
- verify measured spacing after the overlay is restored

## Verification Strategy

At minimum, verify:

- the app compiles with `:app:assembleDebug`
- the floating tab visually spans the available width inside the `20dp` side margins
- the outer shell remains transparent
- the inner board remains the only solid background
- all four tab items are evenly distributed and centered
- `Quick Mood Log -> Mood -> Home` still works
