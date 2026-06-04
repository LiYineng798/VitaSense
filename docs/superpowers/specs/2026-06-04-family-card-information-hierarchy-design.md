# Family Card Information Hierarchy Design

## Goal

Make the Family page easier to scan by reorganizing each member card into clear information zones. The user should be able to tell at a glance:

- Who the member is.
- Whether they checked in today.
- Whether their health score is shared, hidden, or unavailable.
- What support activity exists today.
- Which actions are available.

## Current Problem

The member card currently stacks identity, mood, status, support summary, health score, privacy switch, support actions, and management actions as similar-looking text rows. This makes the card dense even when the amount of data is reasonable, because the UI does not separate status data from controls.

## Chosen Approach

Use a sectioned member card layout. Keep all existing data and behavior, but change the visual grouping.

Each card has four zones:

1. Identity zone
   - Avatar initial.
   - Display name.
   - Role label.

2. Today status zone
   - Two compact status blocks:
     - Mood.
     - Health.
   - Mood block shows the mood label and status label.
   - Health block shows `Health Score 82`, `No score today`, or `Health score not shared`, plus the score label when available.

3. Support zone
   - Shows today's support summary.
   - Shows latest support type when present.
   - Shows support buttons only for other members.

4. Controls zone
   - Shows the health score sharing switch only on the current user's own card.
   - Shows owner-only remove action at the bottom with weaker visual priority.

## Layout Rules

- The identity row remains at the top.
- Mood and Health should be visually paired so users understand they are status data, not actions.
- Support buttons remain below status information.
- The health sharing switch must not appear on other members' cards.
- Remove member remains separated from daily status and support actions.
- Text must remain readable on narrow mobile widths; status blocks may stack vertically if horizontal space is too tight.

## UI Model Changes

Add display-specific fields to `FamilyMemberUiModel` only as needed for clearer labels:

- `moodSectionTitle`
- `healthSectionTitle`
- `supportSectionTitle`
- optionally separate support detail text if the current combined string becomes harder to bind cleanly.

Existing fields for mood, status, health score, support, sharing switch, and permissions remain the source of truth.

## Behavior

No backend behavior changes.

No repository changes.

No change to privacy behavior:

- Health score sharing remains opt-in.
- Only the current user can toggle their own sharing.
- Hidden score state still displays as `Health score not shared`.
- A shared user without a local score still displays as `No score today`.

## Testing

Update mapper or UI-model tests only if new display fields are added.

Run:

- `.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin`
- `.\gradlew.bat --no-daemon :app:assembleDebug`

Manual visual check:

- Current user's card shows the switch in the controls zone.
- Other members do not show the switch.
- Mood and Health are visually distinct from support buttons.
- Remove member is visually separated from daily status.

## Out Of Scope

- Collapsible member cards.
- Family-level aggregate summary.
- New backend fields.
- New health metrics beyond total score.
- Ranking, charts, history, or trend display.
