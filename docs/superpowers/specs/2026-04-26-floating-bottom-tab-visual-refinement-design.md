# Floating Bottom Tab Visual Refinement

## Summary

Refine the existing custom floating bottom tab so it reads as a truly independent floating pill instead of a full-width bottom slab. The refinement keeps the current four-destination navigation model and bottom safe spacing, but changes the tab bar's visual footprint, transparency, and selected-state geometry.

This is a focused visual and interaction refinement on top of the already-approved floating tab redesign. It does not change page routing, destination count, or the app's XML + Fragment architecture.

## Goals

- Keep the floating tab visually independent from the page background.
- Make the area outside the tab pill transparent so it does not appear to block content.
- Preserve bottom content safety padding so lists and cards are not obscured.
- Shrink the selected-state dark capsule so it is smaller and rounder, closer to the reference document.
- Retain the existing liquid motion behavior between top-level destinations.

## Non-Goals

- No change to the four top-level destinations.
- No change to fragment content or page information architecture.
- No conversion to Compose.
- No reduction of bottom safe spacing that would cause content to sit under the tab pill.
- No redesign of icons, labels, or the app theme palette beyond the tab component itself.

## Current Problems

The current implementation still reads too much like a full-width bottom bar:

1. The bottom tab include is anchored correctly, but the visible card and board background expand across the full available width.
2. The surrounding area feels occupied even though the user wants only the tab pill itself to be visually present.
3. The selected-state indicator currently fills the full width and height of a tab cell, which makes the active background look oversized and heavy.

## Target Experience

The final component should behave like this:

- The page content still reserves enough bottom space to remain readable and tappable.
- Only the central pill-shaped tab board is visually opaque.
- The left and right areas around the pill remain transparent.
- The selected state appears as a compact rounded capsule nested inside the selected tab cell.
- Taps remain easy even though the visual selected background is smaller than the full tap target.

## Target Architecture

### 1. Transparent Host Layer

The bottom include in `activity_main.xml` remains full-width so it can be constrained cleanly to the activity root and continue participating in inset handling.

Inside that include, the root container becomes a transparent host whose responsibility is only:

- bottom anchoring
- centering the actual tab pill
- exposing a stable measured height for bottom safe padding

This host layer must not draw any full-width background.

### 2. Centered Pill Board

The visible tab board becomes a centered inner card with these properties:

- `wrap_content` width
- rounded pill silhouette
- theme-colored surface fill
- subtle outline
- existing elevation/shadow behavior

The board should look like a self-contained floating object rather than a stretched navigation strip.

To support a `wrap_content` pill, the four tab cells will no longer depend on weight-based full-width expansion. Instead, they will use consistent fixed visual widths so the board sizes to its content and stays centered.

## Tab Cell Geometry

The new tab cell geometry should follow these rules:

- each tab cell keeps a stable tap target sized for usability
- visual width is fixed and uniform across all four tabs
- icon and label remain center-aligned
- the selected background is visually inset inside the cell instead of occupying the full cell box

Recommended first-pass dimensions:

- tab cell width: `76dp`
- tab cell height: `60dp`
- board horizontal inner padding: `8dp`
- board vertical inner padding: `6dp`

These values are chosen to make the pill narrower and less blocky than the current implementation while keeping all four labels legible.

## Selected-State Indicator Refinement

`LiquidTabIndicatorView` remains the drawing layer for the animated selected background, but the target bounds must change.

Instead of snapping and animating to the full `left/right` bounds of a tab view, the indicator should animate to an inset visual bounds model:

- horizontal inset per selected cell
- vertical inset from the top and bottom of the indicator canvas
- full pill corner radius derived from the reduced indicator height

Recommended first-pass indicator geometry:

- horizontal inset: `10dp`
- vertical inset: `7dp`
- resulting visual height: `46dp` inside a `60dp` tab cell

This produces a smaller, rounder active capsule without reducing the actual click target.

## Insets And Content Safety

The user explicitly chose to keep bottom safe spacing.

That means:

- `MainActivity` continues to pad the `FragmentContainerView` based on the measured floating tab host height, bottom margin, and system bar inset
- no page content should become hidden under the floating tab
- the refinement is visual only, not an overlap-first layout change

## Navigation And Interaction

The navigation model stays unchanged:

- `Home`
- `Trends`
- `Assessment`
- `Mood`

The refinement must not alter:

- tab destination mapping
- current `Quick Mood Log` navigation behavior
- destination synchronization logic
- back-stack synchronization between visible fragment and selected tab

The only interaction-level change is that the active-state drawing bounds become smaller while the tap target remains comfortable.

## File Impact

### Primary Files To Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- `app/src/main/res/drawable/bg_floating_tab_bar.xml`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/LiquidTabIndicatorView.kt`

### Possible Supporting Files

- `app/src/main/res/values/dimens.xml` if shared dimensions are extracted
- `app/src/main/res/values/themes.xml` only if the current tab surface or outline tokens require adjustment

No new navigation destinations or graph files are required.

## Implementation Notes

1. Keep the outer include full-width for anchoring and inset math.
2. Move visible background responsibility from the full-width container to a centered inner pill card.
3. Replace equal-weight full-width tab distribution with fixed-width tab cells.
4. Update indicator drawing so its rendered rect uses smaller visual bounds than the full tab cell.
5. Keep all selection, animation, and destination-sync logic intact unless required for the new geometry.

## Risks And Mitigations

### Risk: the narrower pill causes label crowding

Mitigation:

- use uniform fixed tab widths sized around current label lengths
- keep current icon and text sizing unless visual overflow appears during verification

### Risk: reducing the selected capsule also reduces perceived tap affordance

Mitigation:

- shrink only the drawn indicator, not the actual clickable tab container

### Risk: transparent surroundings reveal awkward spacing below content

Mitigation:

- preserve current bottom padding logic
- verify the floating host height still matches the visually occupied area closely enough

### Risk: indicator animation becomes visually misaligned after changing geometry

Mitigation:

- derive animated target bounds from the selected tab view plus explicit insets rather than hardcoded screen coordinates

## Verification Strategy

At minimum, verify:

- the tab bar visually reads as a centered floating pill
- the surrounding left and right area remains transparent
- the selected dark capsule is visibly smaller and rounder than before
- all four tabs still navigate correctly
- `Quick Mood Log -> Mood -> Home` still works
- page content remains readable and tappable above the bottom safe area
- `assembleDebug` still succeeds in the current offline environment

## Acceptance Criteria

- The user can clearly see that only the tab pill itself is opaque.
- The selected tab background no longer fills an entire tab cell.
- The selected background appears compact, rounded, and visually closer to the reference document.
- The bottom tab remains independently floating above page content.
- Existing top-level navigation behavior remains intact.
