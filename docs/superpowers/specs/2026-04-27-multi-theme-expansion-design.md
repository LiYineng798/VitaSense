# Multi-Theme Expansion

## Summary

Expand VitaSense from a single warm theme with `Light / Dark` mode into a two-dimensional theme system:

- theme family
- light or dark mode

The existing warm palette remains the default family. Two new families are added from the user-provided palettes, and each family supports both light and dark variants.

This redesign is not only a settings-page toggle change. It also introduces a semantic color layer so cards, buttons, charts, chips, and the floating tab can all switch palettes coherently instead of staying hard-coded to the current brown palette.

## Goals

- Keep the current theme as the default family.
- Add exactly two new theme families from the provided palette document.
- Let users choose theme family and mode separately.
- Make theme changes apply immediately without restarting the app.
- Ensure the new palettes affect both standard Material surfaces and the app's custom chart/navigation visuals.
- Preserve readability, hierarchy, and emotional tone across all themes.

## Non-Goals

- No migration to Compose.
- No system-following auto mode in this iteration.
- No per-screen custom theme selection.
- No full dynamic color engine that computes arbitrary palettes at runtime.
- No redesign of page layout structure outside what is needed to present the new theme controls clearly.

## User-Provided Theme Families

### Existing Default Family

The current VitaSense warm brown palette remains the default family.

User-facing label:

- `Default`

### New Family A

Source palette:

- `[140, 152, 94]`
- `[250, 83, 21]`
- `[0, 0, 0]`
- `[199, 189, 181]`
- `[214, 210, 207]`

Design interpretation:

- grounded
- more mature
- stronger contrast
- emotional tone combining calm stability with sharp alert energy

User-facing label:

- `Olive Ember`

### New Family B

Source palette:

- `[251, 232, 127]`
- `[164, 213, 233]`
- `[123, 168, 128]`
- `[153, 156, 153]`

Design interpretation:

- softer
- lighter
- more restorative
- emotional tone combining recovery, airiness, and low-pressure reassurance

User-facing label:

- `Sunlit Meadow`

## Theme Model

### State Shape

Theme state is split into two independent settings:

- `ThemeFamily`
- `ThemeMode`

`ThemeFamily` values:

- `DEFAULT`
- `OLIVE_EMBER`
- `SUNLIT_MEADOW`

`ThemeMode` values:

- `LIGHT`
- `DARK`

This produces six concrete combinations:

- `Default Light`
- `Default Dark`
- `Olive Ember Light`
- `Olive Ember Dark`
- `Sunlit Meadow Light`
- `Sunlit Meadow Dark`

### Why Separate Family And Mode

The user explicitly wants theme package selection and light/dark selection to be controlled independently.

This means:

- the repository must persist two settings instead of one
- the UI must expose two selectors instead of a binary toggle
- `MainActivity` must apply theme family and mode together when the app starts and whenever either setting changes

## Resource Strategy

### Semantic Colors Instead Of Family-Bound Names

The current app heavily relies on family-bound resource names such as:

- `vs_primary_700`
- `vs_dark_primary_500`
- `vs_text_primary`
- `vs_dark_text_secondary`

That naming locks the app to one palette family. To support multiple families cleanly, the implementation should introduce semantic theme roles.

Core semantic roles:

- primary accent
- primary accent strong
- primary accent soft
- secondary accent
- background
- surface
- alternate surface
- soft border
- primary text
- secondary text
- tertiary text
- alert red
- week-signal tile
- chart tone soft
- chart tone medium
- chart tone deep

Existing views should gradually resolve these semantics rather than directly assuming the warm-brown family.

### Concrete Theme Styles

The app theme layer should move from:

- one `values/themes.xml` light palette
- one `values-night/themes.xml` dark palette

to a set of concrete family + mode styles derived from the common `Base.Theme.VitaSense`.

Recommended structure:

- `Theme.VitaSense.Default.Light`
- `Theme.VitaSense.Default.Dark`
- `Theme.VitaSense.OliveEmber.Light`
- `Theme.VitaSense.OliveEmber.Dark`
- `Theme.VitaSense.SunlitMeadow.Light`
- `Theme.VitaSense.SunlitMeadow.Dark`

`MainActivity` should choose one of these styles before `super.onCreate`.

## Palette Application Rules

### Default Family

The existing theme remains visually unchanged except where semantic refactoring is required.

This avoids regressions for current users.

### Olive Ember

Use the palette as follows in light mode:

- olive green as the primary base accent
- vivid orange as the action/emphasis accent
- black only for high-contrast anchor moments, not for broad surfaces
- the two neutral beige-gray colors for backgrounds, cards, and borders

Dark mode should preserve the same identity:

- deep olive-charcoal surfaces
- softened warm highlight accents
- orange still available for emphasis without becoming the default background color

The family should feel emotionally grounded, focused, and slightly more serious than the default theme.

### Sunlit Meadow

Use the palette as follows in light mode:

- pale yellow as the warm atmosphere base
- soft blue and green as paired primary/secondary accents
- the gray-green neutral as the structural balancing tone

Dark mode should preserve the same identity:

- dim meadow-green and blue accents on dark softened surfaces
- yellow used as a restrained highlight, not a large bright panel

The family should feel emotionally lighter, more open, and more restorative than the default theme.

## Settings Experience

### Theme Card Layout

The existing settings page theme card should be redesigned into two sections:

- theme family selector
- mode selector

### Family Selector

The family selector should present:

- `Default`
- `Olive Ember`
- `Sunlit Meadow`

Each option should include:

- theme name
- compact color preview swatches
- visible selected state

The selector should communicate palette personality at a glance, not just via text labels.

### Mode Selector

The mode selector should present:

- `Light`
- `Dark`

This remains independent from family selection.

### Status Text

The current single-line theme status should change from values such as:

- `Light Mode`
- `Dark Mode`

to a combined status such as:

- `Default / Light`
- `Olive Ember / Dark`
- `Sunlit Meadow / Light`

## Persistence And Compatibility

### Storage

The settings repository should store:

- `theme_mode`
- `theme_family`

### Backward Compatibility

Older installs currently persist only `theme_mode`.

Backward-compatible behavior:

- if `theme_family` is missing, treat it as `DEFAULT`
- continue reading old `theme_mode` values without migration failure

This keeps the upgrade path simple and safe.

## Runtime Theme Application

### Activity-Level Theme Selection

`MainActivity` currently applies `AppCompatDelegate.MODE_NIGHT_YES/NO` based on `ThemeMode`.

That is no longer sufficient by itself.

The new logic should:

1. resolve family + mode from settings
2. set the corresponding concrete app theme before `super.onCreate`
3. continue applying the correct night mode for Material behavior
4. react to settings changes so switching theme updates the current activity immediately

### Custom View Color Resolution

Several custom views currently choose colors with direct `R.color` references plus `isNightMode()` checks.

Examples include:

- floating tab indicator and content tinting
- line charts
- monthly insight chart
- recovery heatmap
- trend sparkline accents
- insight cards

These areas should be updated to resolve themed semantic colors rather than only branching on current dark/light brown resources.

This is necessary so the new theme families actually affect the app instead of changing only top-level Material surfaces.

## File Impact

### Primary Files To Modify

- `app/src/main/java/org/wit/vitasense/model/ThemeMode.kt`
- `app/src/main/java/org/wit/vitasense/model/ThemeFamily.kt`
- `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- custom chart and navigation views that currently hard-code family-bound colors

### Likely Additional Support Files

- new theme-specific color resources or semantic attribute mappings
- strings for new family names and selector labels
- tests for settings persistence and theme mapping

## Acceptance Criteria

- The app offers three theme families total: the existing default family plus two new families.
- Users can choose theme family and mode independently.
- Each new family supports both light and dark variants.
- Theme changes apply immediately and persist across app restarts.
- The settings page clearly displays the selected family and mode.
- Cards, floating tab, major buttons, and charts visibly adapt to the chosen family instead of staying fixed to the warm-brown palette.
- Existing users without a stored `theme_family` continue to load safely into the default family.
- The app still compiles with `:app:assembleDebug` and `:app:assembleDebugAndroidTest`.

## Risks And Mitigations

### Risk: Hard-coded warm-brown colors remain in custom views

Mitigation:

- audit custom views and fragment-level color usage
- replace direct family-bound references with semantic theme resolution where the visual effect is user-facing

### Risk: New palettes look decorative but reduce readability

Mitigation:

- reserve darkest tones for text and critical emphasis
- keep background and surface contrast conservative
- avoid using accent colors as large text backgrounds unless contrast remains strong

### Risk: Theme switching logic becomes scattered

Mitigation:

- centralize family + mode mapping in one theme-resolution path
- avoid per-screen special cases unless strictly necessary

### Risk: Dark variants feel like recolored light themes instead of intentional dark themes

Mitigation:

- define dark variants with their own surface and text hierarchy
- preserve family mood while reducing glare and maintaining chart readability

## Verification Strategy

At minimum, verify:

- settings persistence for `theme_family` and `theme_mode`
- correct fallback to `DEFAULT` when only old theme data exists
- settings page shows the selected family and mode correctly
- the app compiles with `:app:assembleDebug`
- the app compiles with `:app:assembleDebugAndroidTest`
- the floating tab updates colors correctly across all three families
- major charts and status cards visibly change palette with the active family
- light and dark variants both remain readable on Home, Trends, Assessment, Mood, and Settings
