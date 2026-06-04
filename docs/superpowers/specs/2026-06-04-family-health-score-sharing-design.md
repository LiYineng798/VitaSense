# Family Health Score Sharing Design

## Goal

Extend the Family feature so each member can optionally share their daily health score with family members.

The feature should make Family feel more informative while preserving user control. A health score is more sensitive than mood, so sharing must be opt-in per user.

## Scope

### Included

- Each signed-in family member can choose whether to share their health score.
- Sharing is off by default.
- When sharing is enabled, other family members can see:
  - today's total health score
  - a short score status label
  - the score update time
- When sharing is disabled, other family members see:
  - `Health score not shared`
- When no score exists for today, family members see:
  - `No score today`
- The current user can always see and change their own sharing state.

### Excluded

- Sharing HRV.
- Sharing heart rate.
- Sharing sleep duration.
- Sharing anomaly flags.
- Sharing risk explanations.
- Sharing raw samples.
- Sharing trend charts.
- Sharing historical score timelines.
- Ranking family members by score.

## Privacy Rules

- Health score sharing must be explicit opt-in.
- Default value is `false`.
- Family API responses must not expose detailed personal health metrics.
- The shared payload must contain only score summary fields.
- Turning sharing off should immediately hide the user's score from future family responses.
- The app should avoid competitive language such as ranking, leaderboard, best, or worst.

## Data Model

Extend the existing family status snapshot with optional score fields:

```text
share_health_score: Boolean
health_score: Int?
health_score_label: String?
health_score_updated_at: Long?
```

The backend should store these fields with the family status snapshot because they are part of the user's shared family status, not part of full cloud sync.

## Score Label

The label should be short and non-diagnostic:

- `Stable` for high scores.
- `Watch today` for medium scores.
- `Needs support` for low scores.
- `No score today` when no score exists.
- `Health score not shared` when sharing is disabled.

Exact thresholds can reuse the existing score/risk logic if available, but the Family UI should not expose the underlying explanation or raw metrics.

## UI Design

### Family Member Card

Add a health score row below the mood/status area:

- If member shares score and has today's score:
  - `Health Score 82`
  - `Stable`
  - `Updated today`
- If member shares score but has no score:
  - `No score today`
- If member does not share:
  - `Health score not shared`

### Current User Control

On the current user's member card or family header, show a switch:

```text
Share health score with family
```

This switch controls only the current user's sharing preference. Owners cannot force other members to share their score.

## Sync Timing

The app should update the current user's shared health score snapshot:

- when opening the Family page
- when Home has a new latest risk/score available
- after importing or syncing health data if the user's share switch is enabled

If the user turns sharing off, the app should send a status update with `share_health_score = false` and no score value.

## Backend API

Reuse:

```text
POST /api/v1/families/{family_id}/status
GET /api/v1/families/me
```

The status endpoint should accept the new optional fields. The family serializer should return score fields only according to the member's own sharing flag.

## Android Architecture

Update existing components rather than creating a separate feature stack:

- `FamilyStatusSnapshot`
  - add score sharing fields
- `FamilyMember`
  - add parsed score sharing fields
- `FamilyUiMapper`
  - map score fields into a display row
- `FamilyViewModel`
  - read latest local health score
  - include score fields when syncing family status
  - expose a toggle action for the current user's sharing preference
- `DefaultFamilyRepository`
  - include new fields in status payload
- `FamilyFragment`
  - render score row and current-user switch

## Error Handling

- If score update fails, keep the existing Family error message pattern.
- If the user toggles sharing while offline, show a concise network error.
- Do not show stale shared score if the backend confirms sharing is disabled.

## Tests

### Backend

- status endpoint accepts `share_health_score = true` with score fields.
- status endpoint accepts `share_health_score = false` and hides score.
- family response excludes raw health fields.
- family response shows score only when sharing is enabled.

### Android

- status payload includes score only when sharing is enabled.
- Family UI shows `Health score not shared` by default.
- Family UI shows `Health Score 82` when sharing is enabled.
- toggling sharing off hides score in UI after refresh.
- no detailed health fields are included in Family models or payloads.

## Decisions

- The share switch belongs in the current user's member card, close to the score row it controls.
- The sharing preference is persisted on the server. Android follows the Family cache returned by the server and does not introduce a separate local preference source.
