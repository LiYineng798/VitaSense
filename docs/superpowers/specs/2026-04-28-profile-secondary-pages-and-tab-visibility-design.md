# Profile Secondary Pages And Tab Visibility Design

**Date:** 2026-04-28

**Goal**

Refine VitaSense navigation and account-facing UI so that auth, appearance, and settings/import flows become clear secondary pages with explicit back navigation, hidden floating tabs, and corrected top-level tab behavior.

**Scope**

- Add a back affordance to the auth screen.
- Clarify Login/Register mode presentation.
- Replace the Home tab eye icon with a house icon.
- Split Profile content into:
  - a lightweight Profile landing page
  - a dedicated Appearance page
  - a dedicated Settings & Import page
- Hide the floating bottom tab bar on all secondary pages with slide animations.
- Fix incorrect Home/Profile tab behavior after entering Profile from Home.

**Out Of Scope**

- Redesigning Trends, Mood, or Home information architecture.
- Replacing the current floating tab component itself.
- Reworking remote auth contracts or health data storage.

## Current State

- `Home / Trends / Mood / Profile` are the four top-level destinations.
- `AuthFragment` exists as a secondary page but has no explicit back button.
- `ProfileFragment` currently embeds theme controls and settings/import actions in the same page.
- `SettingsFragment` already exists but is not used as the main Profile secondary page.
- `MainActivity` always keeps the floating tab visible and only updates selected state when the current destination is one of the four top-level fragments.
- Home avatar clicks navigate directly with `findNavController().navigate(...)`, which can leave the tab-selected state and destination restoration behavior inconsistent.

## Requirements

### 1. Auth Page Back Navigation

- When the user enters the auth screen from Home or Profile, the screen must provide a visible back button.
- Tapping the back button returns to the previous page.

### 2. Login/Register Mode Clarity

- The user must clearly understand whether they are on Login or Register mode.
- In Login mode:
  - `Login` remains the primary-looking option and visually aligns with the `Sign In` action button.
  - `Register` becomes visually de-emphasized.
- In Register mode:
  - `Register` remains the primary-looking option and visually aligns with the `Create Account` action button.
  - `Login` becomes visually de-emphasized.
- The styling must stay within the current theme system and not introduce off-theme colors.

### 3. Home Tab Icon

- Replace the current Home tab icon with a house icon to create a softer, more familiar feel.

### 4. Appearance As A Dedicated Secondary Page

- `Profile` should no longer show full Appearance controls inline.
- Instead, it should expose a single `Appearance` entry item.
- Tapping it opens a dedicated `Appearance` page with:
  - theme family selection
  - theme mode selection
  - a back button

### 5. Settings & Import As A Dedicated Secondary Page

- `Profile` should expose a single `Settings & Import` entry item.
- Tapping it opens a dedicated page that contains:
  - demo import actions
  - clear-all-data action
  - explanatory notes already associated with settings/import
  - a back button

### 6. Floating Tab Visibility On Secondary Pages

- On secondary pages:
  - `Auth`
  - `Appearance`
  - `Settings & Import`
  the floating tab bar must not be visible.
- Entering any of these pages should animate the floating tab downward out of view.
- Returning from these pages should animate the floating tab upward back into its original position.
- Top-level pages keep the tab visible as they do now.

### 7. Fix Home/Profile Navigation Binding

- When the user is signed in and taps the Home avatar, they may enter `Profile`.
- After that, tapping the `Home` bottom tab must always navigate correctly back to `Home`.
- The current behavior, where the app can remain on `Profile`, must be removed.

## Architecture

### Navigation Structure

Keep the four current top-level destinations:

- `dashboardFragment`
- `trendsFragment`
- `moodFragment`
- `profileFragment`

Use the following secondary destinations:

- existing `authFragment`
- new `appearanceFragment`
- existing `settingsFragment`

This keeps the app’s high-level structure intact while giving `Profile` a cleaner landing page and using the nav graph for true second-level flows.

### Profile Page Strategy

`ProfileFragment` becomes a landing page rather than a settings-heavy page. It should contain:

- account summary card
- sign-in / log-out actions
- `Appearance` entry row/card
- `Settings & Import` entry row/card
- privacy/disclaimer content kept lightweight

Theme controls and import actions move out of this screen.

### Appearance Page Strategy

Create a dedicated `AppearanceFragment` that reuses the current theme-selection logic now split across `Profile` and `Settings`. This page owns:

- theme family cards
- light/dark toggle
- current theme summary
- top back button

### Settings & Import Page Strategy

Repurpose `SettingsFragment` as the dedicated secondary page for:

- demo bundle import
- clear-all-data
- data notes / privacy notes / disclaimer if appropriate
- top back button

The fragment already has most of this functionality and can be simplified around that responsibility.

## Floating Tab Visibility Model

`MainActivity` remains the single controller for the floating tab bar.

It should classify destinations into:

- **Top-level destinations:** show tab
- **Secondary destinations:** hide tab

This classification should be based on destination IDs rather than fragment-specific ad-hoc calls. That keeps the behavior consistent regardless of which screen initiated navigation.

### Show/Hide Behavior

- Enter secondary destination:
  - animate `floatingBottomTabs` translation downward
  - disable interaction while hidden
  - set visibility/gone state when animation completes if needed
- Return to top-level destination:
  - restore visibility
  - animate translation upward to zero
  - restore interaction

The selected tab state must remain tied only to the current top-level destination.

## Correcting The Home/Profile Binding Bug

The Home avatar currently performs a raw fragment navigation to `profileFragment`. That can leave the top-level destination state desynchronized from the bottom-tab state.

The fix is:

- when navigating from Home to Profile as a top-level destination, use the same top-level navigation path the floating tab uses
- reserve plain nav pushes for real secondary destinations like `authFragment`, `appearanceFragment`, and `settingsFragment`

That ensures the nav controller back stack and selected bottom destination remain consistent.

## UI Details

### Auth Header

Add a compact top app bar style row to the auth page with:

- back icon/button on the left
- screen title

### Login/Register Toggle Styling

Use the existing theme colors:

- active mode:
  - text/icon/button color aligned with the current action button style
- inactive mode:
  - lower emphasis via softer text color, outlined/toned button styling, or reduced emphasis tint

The implementation should avoid reversing the semantics. The current mode is the clear one; the other mode is visually muted.

### Profile Landing Entries

Use clear, tap-friendly rows/cards for:

- `Appearance`
- `Settings & Import`

Each row should indicate it leads to a deeper page, for example with a chevron.

## Testing Strategy

- Add navigation unit/instrumentation coverage for:
  - top-level destination mapping
  - Home avatar navigation + Home tab return
  - secondary pages hiding the floating tab
- Add fragment/UI-state tests for:
  - auth mode visual state if feasible at unit level
  - appearance/settings entry visibility and navigation targets
- Re-run existing bottom-tab and main-activity smoke tests because the show/hide behavior changes the shell.

## Risks

- Hiding the floating tab with animation can affect bottom padding/insets if handled by visibility alone. The content inset logic in `MainActivity` must remain stable whether the tab is shown or hidden.
- Reusing `SettingsFragment` for the new secondary page is lower risk than introducing another settings page, but its layout may need trimming so `Profile` and `Settings` do not duplicate the same content.
- The Home/Profile bug should be fixed at the navigation source rather than by patching tab selection state after the fact.
