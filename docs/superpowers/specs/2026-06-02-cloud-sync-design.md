# Cloud Sync Design

## Goal

Add cloud persistence for signed-in VitaSense users so logging in can restore core user data from the server. The first version syncs theme settings, mood records, raw heart-rate samples, and sleep records. Derived health summaries and risk assessments stay local and are recomputed after synced source data changes.

## Scope

In scope:

- Sync theme mode and theme family.
- Sync mood records.
- Sync raw heart-rate samples.
- Sync sleep records.
- Load cloud data automatically after successful login or session restoration.
- Recompute local derived summaries and risk assessments after cloud data is merged.
- Push local changes after theme changes, mood changes, and demo-data imports.
- Add a manual `Sync now` action in Settings for retry and debugging.

Out of scope for the first version:

- Syncing AI API keys or other sensitive AI provider credentials.
- Syncing `daily_physiology_summary` or `risk_assessment_records`.
- Background real-time sync.
- Multi-device conflict UI.
- Syncing data for signed-out users.
- Syncing arbitrary import logs as source-of-truth data.

## Data Ownership

The server stores canonical user-owned source data. Android keeps a local Room cache so the app remains usable offline.

Source data:

- theme settings
- mood records
- heart-rate raw samples
- sleep records

Derived data:

- daily physiology summaries
- risk assessment records

Derived data is regenerated locally through `HealthRecomputeEngine.recomputeAllDates()` after bootstrap or pull.

## Backend Data Model

The existing Python API already has `users` and `sessions`. Add sync tables keyed by `user_id`.

### user_settings

Columns:

- `user_id INTEGER PRIMARY KEY`
- `theme_mode TEXT NOT NULL`
- `theme_family TEXT NOT NULL`
- `updated_at INTEGER NOT NULL`

### cloud_mood_records

Columns:

- `id TEXT PRIMARY KEY`
- `user_id INTEGER NOT NULL`
- `date TEXT NOT NULL`
- `mood_type TEXT NOT NULL`
- `mood_group TEXT NOT NULL`
- `note TEXT`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`
- `deleted_at INTEGER`

The `id` is a stable client-generated UUID. Deleted mood records remain as tombstones so deletion can sync across devices.

### cloud_heart_rate_samples

Columns:

- `id TEXT PRIMARY KEY`
- `user_id INTEGER NOT NULL`
- `sample_timestamp INTEGER NOT NULL`
- `date TEXT NOT NULL`
- `heart_rate INTEGER NOT NULL`
- `source_batch_id TEXT NOT NULL`
- `updated_at INTEGER NOT NULL`

Add a unique index:

- `(user_id, sample_timestamp, heart_rate, source_batch_id)`

The server may derive `id` from that unique key if the client does not provide one.

### cloud_sleep_records

Columns:

- `id TEXT PRIMARY KEY`
- `user_id INTEGER NOT NULL`
- `date TEXT NOT NULL`
- `start_at INTEGER NOT NULL`
- `end_at INTEGER NOT NULL`
- `duration_minutes INTEGER NOT NULL`
- `avg_heart_rate REAL`
- `heart_rate_variability_hint REAL`
- `source_batch_id TEXT NOT NULL`
- `updated_at INTEGER NOT NULL`
- `deleted_at INTEGER`

Add a unique index:

- `(user_id, date)`

For duplicate sleep records on the same date, the first version keeps the newer `updated_at`. If timestamps are equal, keep the longer sleep duration, matching the existing import behavior.

## Backend API

All sync endpoints require:

`Authorization: Bearer <session-token>`

If the token is missing or invalid, return `401`.

### GET /api/v1/sync/bootstrap

Returns all cloud source data for the current user.

Response:

```json
{
  "success": true,
  "server_time": 1770000000000,
  "settings": {
    "theme_mode": "light",
    "theme_family": "default",
    "updated_at": 1770000000000
  },
  "mood_records": [],
  "heart_rate_samples": [],
  "sleep_records": []
}
```

### POST /api/v1/sync/push

Uploads local source data changes. The first version can accept a full core-data payload and upsert it server-side.

Request:

```json
{
  "settings": {
    "theme_mode": "dark",
    "theme_family": "rose_indigo",
    "updated_at": 1770000000000
  },
  "mood_records": [],
  "heart_rate_samples": [],
  "sleep_records": []
}
```

Response:

```json
{
  "success": true,
  "server_time": 1770000000000,
  "message": "Sync complete."
}
```

### Deferred: POST /api/v1/sync/pull

Do not implement incremental pull in the first version. The first version uses `bootstrap` for server-to-device restore and `push` for device-to-server updates. A later version can add `POST /api/v1/sync/pull` with a `since` timestamp once stable sync metadata has been proven.

## Android Local Schema Changes

Add stable sync metadata to source tables.

### MoodRecordEntity

Add:

- `cloudId: String`
- `updatedAt: Long`
- `deletedAt: Long?`

Use `cloudId` as a UUID generated before insert. Existing local records can receive a UUID during migration or destructive migration fallback.

### HeartRateRawSampleEntity

Add:

- `cloudId: String`
- `updatedAt: Long`

Use a deterministic ID derived from `sampleTimestamp`, `heartRate`, and `sourceBatchId`, or generate a UUID and rely on the existing unique index for dedupe.

### SleepRecordEntity

Add:

- `cloudId: String`
- `updatedAt: Long`
- `deletedAt: Long?`

Use `date` for conflict resolution and `cloudId` for stable cloud identity.

### App Settings

Add local settings keys:

- `last_sync_at`
- `sync_status`
- `sync_error`

Do not sync:

- auth token
- current user JSON
- AI API key
- AI provider base URL/model

Theme mode and family are synced separately through the sync payload.

## Android Architecture

Add a `CloudSyncRepository` interface:

```kotlin
interface CloudSyncRepository {
    suspend fun bootstrapAfterLogin(): CloudSyncResult
    suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult
    suspend fun syncNow(): CloudSyncResult
}
```

Add `DefaultCloudSyncRepository` that depends on:

- `SettingsRepository`
- `HealthRepository` or lower-level DAOs
- `MoodRepository` or `MoodRecordDao`
- `HealthRecomputeEngine`
- auth token from `SettingsRepository`

The repository performs:

1. read token
2. call backend
3. merge cloud data into Room in a transaction
4. recompute derived health content
5. update sync status settings

## Sync Triggers

### Login Success

After `DefaultAuthRepository.persistSession()` succeeds:

1. trigger `CloudSyncRepository.bootstrapAfterLogin()`
2. merge cloud data
3. recompute derived data
4. refresh UI through existing Room Flow observers

If sync fails after login, login still succeeds. Show a Snackbar or Profile/Settings status message: `Signed in, but cloud sync failed. Try Sync now from Settings.`

### Session Restoration

When app launch restores a valid session through `/api/v1/auth/me`, trigger bootstrap once.

### Theme Change

After `setThemeMode()` or `setThemeFamily()`, push local settings snapshot.

### Mood Change

After mood insert or delete, push mood snapshot.

### Demo Data Import

After demo import and recomputation succeed, push heart-rate and sleep records.

### Manual Sync

Add `Sync now` to Settings. It runs bootstrap plus local push using the same merge rules.

## Merge And Conflict Rules

### Settings

Compare `updated_at`.

- Cloud newer: apply cloud theme locally.
- Local newer: keep local theme and push it.
- Same timestamp: keep local value.

### Mood Records

Use `cloudId`.

- New cloud record: insert locally.
- Existing record: keep higher `updatedAt`.
- Tombstone with higher `deletedAt`: delete/hide locally.
- Local delete creates tombstone and pushes it.

### Heart-Rate Samples

Use existing uniqueness:

- `(sampleTimestamp, heartRate, sourceBatchId)`

Merge by insert-ignore. Heart-rate samples are immutable in the first version. No tombstones.

### Sleep Records

Use `date`.

- Keep higher `updatedAt`.
- If equal timestamp, keep longer duration.
- Deleted sleep records are not required in the first version unless local clear-all-data is synced.

### Clear All Data

First version keeps `Delete All Data` local-only. The confirmation copy must mention that cloud data can restore on the next sync. A later version can add `Delete local and cloud` as a separate explicit destructive action.

## Error Handling

Sync errors should not block core app use.

Recommended error messages:

- token missing: `Sign in before syncing data.`
- token invalid: `Session expired. Please sign in again.`
- network error: `Unable to reach the cloud sync service.`
- server error: `Cloud sync is temporarily unavailable.`
- malformed response: `Cloud sync returned an unexpected response.`
- conflict merge error: `Some cloud records could not be merged.`

Store the latest sync status in settings so Settings/Profile can display it.

## Security And Privacy

- All sync endpoints require bearer tokens.
- Health and mood data are sent only for signed-in users.
- Do not log request bodies containing health or mood data on the server.
- Do not sync AI API keys.
- Use HTTPS through `https://server.np5.top`.
- Keep server records scoped by `user_id`.

## Testing

Backend tests:

- bootstrap rejects missing token.
- bootstrap returns only current user's records.
- push upserts settings by timestamp.
- push dedupes heart-rate samples.
- mood tombstone wins over older active record.
- sleep conflict keeps newer timestamp or longer duration on tie.

Android unit tests:

- login success triggers bootstrap.
- bootstrap merges cloud theme into settings.
- bootstrap inserts heart-rate samples without duplicates.
- bootstrap inserts/updates sleep records and recomputes derived summaries.
- mood delete creates tombstone and push payload.
- failed sync does not fail login.

Manual verification:

- Register user A, import demo data, sync.
- Reinstall or clear local data, log in as user A, confirm theme and data restore.
- Log in as user B, confirm user A data does not appear.
- Change theme on one installation, sync, reinstall, confirm theme restores.
- Add mood record, sync, reinstall, confirm mood record restores.
