# Cloud Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated cloud persistence so signed-in VitaSense users can restore theme, mood, raw heart-rate, and sleep source data from `https://server.np5.top`.

**Architecture:** Extend the existing FastAPI auth service with per-user sync tables and two authenticated endpoints: `bootstrap` for full restore and `push` for full snapshot upload. Android adds sync metadata to Room source entities, a `CloudSyncRepository` that merges server data locally, and explicit sync triggers after login/session restore, theme changes, mood changes, demo import, and Settings `Sync now`.

**Tech Stack:** Kotlin, XML Views, ViewBinding, Room, Coroutines/Flow, `HttpURLConnection`, FastAPI, SQLite, Python stdlib, existing Nginx/systemd deployment.

---

## File Map

- Modify `python_auth_api/main.py`: add bearer-token user resolver, sync Pydantic models, SQLite sync tables, merge helpers, `/api/v1/sync/bootstrap`, and `/api/v1/sync/push`.
- Modify `python_auth_api/smoke_test.py`: add authenticated sync smoke checks for missing token, per-user isolation, settings timestamp conflict, heart-rate dedupe, mood tombstone, and sleep conflict.
- Create `app/src/main/java/org/wit/vitasense/model/CloudSyncModels.kt`: Android payload/result/error/reason models and JSON parsing/serialization.
- Create `app/src/main/java/org/wit/vitasense/repository/CloudSyncRepository.kt`: sync repository interface.
- Create `app/src/main/java/org/wit/vitasense/data/repository/DefaultCloudSyncRepository.kt`: backend client, local snapshot builder, merge logic, and status persistence.
- Modify `app/src/main/java/org/wit/vitasense/db/entity/MoodRecordEntity.kt`: add `cloudId`, `updatedAt`, and `deletedAt`.
- Modify `app/src/main/java/org/wit/vitasense/db/entity/HeartRateRawSampleEntity.kt`: add `cloudId` and `updatedAt`.
- Modify `app/src/main/java/org/wit/vitasense/db/entity/SleepRecordEntity.kt`: add `cloudId`, `updatedAt`, and `deletedAt`.
- Modify `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`: bump Room version and keep destructive migration fallback.
- Modify `app/src/main/java/org/wit/vitasense/db/dao/MoodRecordDao.kt`: add active-record filtering, full sync list, sync upsert, and tombstone update methods.
- Modify `app/src/main/java/org/wit/vitasense/db/dao/HeartRateRawSampleDao.kt`: add full sync list and insert-ignore methods.
- Modify `app/src/main/java/org/wit/vitasense/db/dao/SleepRecordDao.kt`: add full sync list and sync upsert methods.
- Modify `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`: add sync-status getters, observers, and setters.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`: persist sync status in the existing settings table.
- Modify `app/src/main/java/org/wit/vitasense/AppContainer.kt`: construct `CloudSyncRepository` and inject it into auth, settings, mood, and health repositories.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultAuthRepository.kt`: trigger bootstrap after login and session restore without failing auth.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`: trigger settings push after theme updates.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt`: trigger mood push after add/delete.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultHealthRepository.kt`: trigger health-source push after demo import.
- Modify `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`: expose sync status and manual sync action.
- Modify `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`: bind `Sync now`, progress, success, and error states.
- Modify `app/src/main/res/layout/fragment_settings.xml`: add a compact cloud sync row/card.
- Modify `app/src/main/res/values/strings.xml`: add cloud sync labels and messages.
- Create or modify Android unit tests under `app/src/test/java/org/wit/vitasense/...`.

---

### Task 1: Backend Sync Schema And Auth Helper

**Files:**
- Modify: `python_auth_api/main.py`

- [ ] **Step 1: Add sync Pydantic models near the existing request models**

```python
class SyncSettingsPayload(BaseModel):
    theme_mode: str
    theme_family: str
    updated_at: int


class SyncMoodRecordPayload(BaseModel):
    id: str
    date: str
    mood_type: str
    mood_group: str
    note: str | None = None
    created_at: int
    updated_at: int
    deleted_at: int | None = None


class SyncHeartRateSamplePayload(BaseModel):
    id: str | None = None
    sample_timestamp: int
    date: str
    heart_rate: int
    source_batch_id: str
    updated_at: int


class SyncSleepRecordPayload(BaseModel):
    id: str
    date: str
    start_at: int
    end_at: int
    duration_minutes: int
    avg_heart_rate: float | None = None
    heart_rate_variability_hint: float | None = None
    source_batch_id: str
    updated_at: int
    deleted_at: int | None = None


class SyncPushRequest(BaseModel):
    settings: SyncSettingsPayload | None = None
    mood_records: list[SyncMoodRecordPayload] = []
    heart_rate_samples: list[SyncHeartRateSamplePayload] = []
    sleep_records: list[SyncSleepRecordPayload] = []
```

- [ ] **Step 2: Add the bearer-token helper**

```python
def get_user_id_from_authorization(authorization: str | None) -> int | None:
    if not authorization or not authorization.startswith("Bearer "):
        return None
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        return None
    with get_connection() as connection:
        row = connection.execute(
            "SELECT user_id FROM sessions WHERE token = ?",
            (token,),
        ).fetchone()
    return int(row["user_id"]) if row else None
```

- [ ] **Step 3: Extend `initialize_database()` with sync tables and indexes**

Add these statements inside the existing `connection.executescript(...)` block after `sessions`:

```sql
CREATE TABLE IF NOT EXISTS user_settings (
    user_id INTEGER PRIMARY KEY,
    theme_mode TEXT NOT NULL,
    theme_family TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cloud_mood_records (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    mood_type TEXT NOT NULL,
    mood_group TEXT NOT NULL,
    note TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cloud_mood_user_date
ON cloud_mood_records(user_id, date);

CREATE TABLE IF NOT EXISTS cloud_heart_rate_samples (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    sample_timestamp INTEGER NOT NULL,
    date TEXT NOT NULL,
    heart_rate INTEGER NOT NULL,
    source_batch_id TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_hr_unique
ON cloud_heart_rate_samples(user_id, sample_timestamp, heart_rate, source_batch_id);

CREATE TABLE IF NOT EXISTS cloud_sleep_records (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    avg_heart_rate REAL,
    heart_rate_variability_hint REAL,
    source_batch_id TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_sleep_user_date
ON cloud_sleep_records(user_id, date);
```

- [ ] **Step 4: Run a local import check**

Run:

```powershell
python -m py_compile python_auth_api/main.py
```

Expected: no output and exit code `0`.

- [ ] **Step 5: Commit**

```powershell
git add python_auth_api/main.py
git commit -m "feat: add cloud sync backend schema"
```

---

### Task 2: Backend Bootstrap And Push Endpoints

**Files:**
- Modify: `python_auth_api/main.py`
- Modify: `python_auth_api/smoke_test.py`

- [ ] **Step 1: Add backend row serialization and upsert helpers**

Add these helpers below `serialize_user()`:

```python
def now_millis() -> int:
    return int(time.time() * 1000)


def serialize_sync_settings(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    return {
        "theme_mode": row["theme_mode"],
        "theme_family": row["theme_family"],
        "updated_at": row["updated_at"],
    }


def serialize_sync_row(row: sqlite3.Row, fields: list[str]) -> dict[str, Any]:
    return {field: row[field] for field in fields}


def stable_heart_rate_id(user_id: int, sample: SyncHeartRateSamplePayload) -> str:
    raw = f"{user_id}:{sample.sample_timestamp}:{sample.heart_rate}:{sample.source_batch_id}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
```

- [ ] **Step 2: Implement `/api/v1/sync/bootstrap`**

Add this endpoint near the auth endpoints:

```python
@app.get("/api/v1/sync/bootstrap")
def sync_bootstrap(authorization: str | None = Header(default=None)):
    user_id = get_user_id_from_authorization(authorization)
    if user_id is None:
        return invalid_response(401, "Missing or invalid session token.")

    with get_connection() as connection:
        settings = connection.execute(
            "SELECT theme_mode, theme_family, updated_at FROM user_settings WHERE user_id = ?",
            (user_id,),
        ).fetchone()
        mood_rows = connection.execute(
            """
            SELECT id, date, mood_type, mood_group, note, created_at, updated_at, deleted_at
            FROM cloud_mood_records
            WHERE user_id = ?
            ORDER BY created_at ASC
            """,
            (user_id,),
        ).fetchall()
        hr_rows = connection.execute(
            """
            SELECT id, sample_timestamp, date, heart_rate, source_batch_id, updated_at
            FROM cloud_heart_rate_samples
            WHERE user_id = ?
            ORDER BY sample_timestamp ASC
            """,
            (user_id,),
        ).fetchall()
        sleep_rows = connection.execute(
            """
            SELECT id, date, start_at, end_at, duration_minutes, avg_heart_rate,
                   heart_rate_variability_hint, source_batch_id, updated_at, deleted_at
            FROM cloud_sleep_records
            WHERE user_id = ?
            ORDER BY date ASC
            """,
            (user_id,),
        ).fetchall()

    return {
        "success": True,
        "server_time": now_millis(),
        "settings": serialize_sync_settings(settings),
        "mood_records": [
            serialize_sync_row(row, ["id", "date", "mood_type", "mood_group", "note", "created_at", "updated_at", "deleted_at"])
            for row in mood_rows
        ],
        "heart_rate_samples": [
            serialize_sync_row(row, ["id", "sample_timestamp", "date", "heart_rate", "source_batch_id", "updated_at"])
            for row in hr_rows
        ],
        "sleep_records": [
            serialize_sync_row(row, ["id", "date", "start_at", "end_at", "duration_minutes", "avg_heart_rate", "heart_rate_variability_hint", "source_batch_id", "updated_at", "deleted_at"])
            for row in sleep_rows
        ],
    }
```

- [ ] **Step 3: Implement `/api/v1/sync/push`**

Add this endpoint after `sync_bootstrap()`:

```python
@app.post("/api/v1/sync/push")
def sync_push(payload: SyncPushRequest, authorization: str | None = Header(default=None)):
    user_id = get_user_id_from_authorization(authorization)
    if user_id is None:
        return invalid_response(401, "Missing or invalid session token.")

    with get_connection() as connection:
        if payload.settings is not None:
            existing = connection.execute(
                "SELECT updated_at FROM user_settings WHERE user_id = ?",
                (user_id,),
            ).fetchone()
            if existing is None or payload.settings.updated_at > existing["updated_at"]:
                connection.execute(
                    """
                    INSERT INTO user_settings(user_id, theme_mode, theme_family, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(user_id) DO UPDATE SET
                        theme_mode = excluded.theme_mode,
                        theme_family = excluded.theme_family,
                        updated_at = excluded.updated_at
                    """,
                    (user_id, payload.settings.theme_mode, payload.settings.theme_family, payload.settings.updated_at),
                )

        for mood in payload.mood_records:
            existing = connection.execute(
                "SELECT updated_at, deleted_at FROM cloud_mood_records WHERE id = ? AND user_id = ?",
                (mood.id, user_id),
            ).fetchone()
            incoming_delete = mood.deleted_at or 0
            existing_delete = (existing["deleted_at"] or 0) if existing else 0
            should_write = existing is None or mood.updated_at > existing["updated_at"] or incoming_delete > existing_delete
            if should_write:
                connection.execute(
                    """
                    INSERT INTO cloud_mood_records(id, user_id, date, mood_type, mood_group, note, created_at, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        date = excluded.date,
                        mood_type = excluded.mood_type,
                        mood_group = excluded.mood_group,
                        note = excluded.note,
                        created_at = excluded.created_at,
                        updated_at = excluded.updated_at,
                        deleted_at = excluded.deleted_at
                    """,
                    (mood.id, user_id, mood.date, mood.mood_type, mood.mood_group, mood.note, mood.created_at, mood.updated_at, mood.deleted_at),
                )

        for sample in payload.heart_rate_samples:
            sample_id = sample.id or stable_heart_rate_id(user_id, sample)
            connection.execute(
                """
                INSERT OR IGNORE INTO cloud_heart_rate_samples(id, user_id, sample_timestamp, date, heart_rate, source_batch_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (sample_id, user_id, sample.sample_timestamp, sample.date, sample.heart_rate, sample.source_batch_id, sample.updated_at),
            )

        for sleep in payload.sleep_records:
            existing = connection.execute(
                "SELECT updated_at, duration_minutes FROM cloud_sleep_records WHERE user_id = ? AND date = ?",
                (user_id, sleep.date),
            ).fetchone()
            should_write = (
                existing is None
                or sleep.updated_at > existing["updated_at"]
                or (sleep.updated_at == existing["updated_at"] and sleep.duration_minutes > existing["duration_minutes"])
            )
            if should_write:
                connection.execute(
                    """
                    INSERT INTO cloud_sleep_records(id, user_id, date, start_at, end_at, duration_minutes,
                                                    avg_heart_rate, heart_rate_variability_hint, source_batch_id, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(user_id, date) DO UPDATE SET
                        id = excluded.id,
                        start_at = excluded.start_at,
                        end_at = excluded.end_at,
                        duration_minutes = excluded.duration_minutes,
                        avg_heart_rate = excluded.avg_heart_rate,
                        heart_rate_variability_hint = excluded.heart_rate_variability_hint,
                        source_batch_id = excluded.source_batch_id,
                        updated_at = excluded.updated_at,
                        deleted_at = excluded.deleted_at
                    """,
                    (
                        sleep.id,
                        user_id,
                        sleep.date,
                        sleep.start_at,
                        sleep.end_at,
                        sleep.duration_minutes,
                        sleep.avg_heart_rate,
                        sleep.heart_rate_variability_hint,
                        sleep.source_batch_id,
                        sleep.updated_at,
                        sleep.deleted_at,
                    ),
                )

    return {"success": True, "server_time": now_millis(), "message": "Sync complete."}
```

- [ ] **Step 4: Add smoke-test helpers and sync checks**

In `python_auth_api/smoke_test.py`, add a helper:

```python
def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
```

Add a sync smoke test after the existing auth flow creates tokens:

```python
sync_payload = {
    "settings": {"theme_mode": "dark", "theme_family": "rose_indigo", "updated_at": 1770000000000},
    "mood_records": [
        {
            "id": "mood-a-1",
            "date": "2026-06-02",
            "mood_type": "CALM",
            "mood_group": "POSITIVE",
            "note": "steady",
            "created_at": 1770000000000,
            "updated_at": 1770000000000,
            "deleted_at": None,
        }
    ],
    "heart_rate_samples": [
        {
            "id": "hr-a-1",
            "sample_timestamp": 1770000000000,
            "date": "2026-06-02",
            "heart_rate": 72,
            "source_batch_id": "demo",
            "updated_at": 1770000000000,
        },
        {
            "id": "hr-a-duplicate",
            "sample_timestamp": 1770000000000,
            "date": "2026-06-02",
            "heart_rate": 72,
            "source_batch_id": "demo",
            "updated_at": 1770000000001,
        },
    ],
    "sleep_records": [
        {
            "id": "sleep-a-1",
            "date": "2026-06-02",
            "start_at": 1769971200000,
            "end_at": 1769996400000,
            "duration_minutes": 420,
            "avg_heart_rate": 61.0,
            "heart_rate_variability_hint": 38.0,
            "source_batch_id": "demo",
            "updated_at": 1770000000000,
            "deleted_at": None,
        }
    ],
}
expect_status("bootstrap missing token", request_json("GET", "/api/v1/sync/bootstrap"), 401)
expect_success("sync push user A", request_json("POST", "/api/v1/sync/push", sync_payload, auth_headers(token_a)))
bootstrap_a = expect_success("bootstrap user A", request_json("GET", "/api/v1/sync/bootstrap", headers=auth_headers(token_a)))
assert bootstrap_a["settings"]["theme_mode"] == "dark"
assert len(bootstrap_a["heart_rate_samples"]) == 1
bootstrap_b = expect_success("bootstrap user B", request_json("GET", "/api/v1/sync/bootstrap", headers=auth_headers(token_b)))
assert bootstrap_b["mood_records"] == []
```

Use the actual token variable names already present in the smoke script.

- [ ] **Step 5: Run backend checks**

Run:

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/smoke_test.py
python python_auth_api/smoke_test.py
```

Expected: Python compilation succeeds; smoke test prints successful auth and sync checks.

- [ ] **Step 6: Commit**

```powershell
git add python_auth_api/main.py python_auth_api/smoke_test.py
git commit -m "feat: add cloud sync backend endpoints"
```

---

### Task 3: Android Sync Models And Room Metadata

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/model/CloudSyncModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/entity/MoodRecordEntity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/entity/HeartRateRawSampleEntity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/entity/SleepRecordEntity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`
- Test: `app/src/test/java/org/wit/vitasense/model/CloudSyncModelsTest.kt`

- [ ] **Step 1: Write failing model tests**

Create `CloudSyncModelsTest.kt`:

```kotlin
package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncModelsTest {
    @Test
    fun heartRateCloudIdIsDeterministic() {
        assertEquals(
            "hr_7b9f8b8a5a9ac7dfec54d325a5a3f310660ec6eb580a7b9134ac3f5e7ebc37d7",
            deterministicHeartRateCloudId(
                sampleTimestamp = 1_770_000_000_000L,
                heartRate = 72,
                sourceBatchId = "demo",
            ),
        )
    }

    @Test
    fun mapsSyncErrorsToUserMessages() {
        assertEquals("Sign in before syncing data.", cloudSyncErrorMessage("missing_token"))
        assertEquals("Session expired. Please sign in again.", cloudSyncErrorMessage("unauthorized"))
        assertEquals("Unable to reach the cloud sync service.", cloudSyncErrorMessage("network"))
    }
}
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.CloudSyncModelsTest" --no-daemon
```

Expected: compile failure because `CloudSyncModels.kt` does not exist.

- [ ] **Step 3: Add `CloudSyncModels.kt`**

Create:

```kotlin
package org.wit.vitasense.model

import java.security.MessageDigest

data class CloudSyncSettings(
    val themeMode: String,
    val themeFamily: String,
    val updatedAt: Long,
)

data class CloudSyncMoodRecord(
    val id: String,
    val date: String,
    val moodType: String,
    val moodGroup: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class CloudSyncHeartRateSample(
    val id: String,
    val sampleTimestamp: Long,
    val date: String,
    val heartRate: Int,
    val sourceBatchId: String,
    val updatedAt: Long,
)

data class CloudSyncSleepRecord(
    val id: String,
    val date: String,
    val startAt: Long,
    val endAt: Long,
    val durationMinutes: Int,
    val avgHeartRate: Double?,
    val heartRateVariabilityHint: Double?,
    val sourceBatchId: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class CloudSyncSnapshot(
    val settings: CloudSyncSettings?,
    val moodRecords: List<CloudSyncMoodRecord>,
    val heartRateSamples: List<CloudSyncHeartRateSample>,
    val sleepRecords: List<CloudSyncSleepRecord>,
)

data class CloudSyncResult(
    val success: Boolean,
    val message: String,
    val serverTime: Long? = null,
)

enum class SyncReason {
    LOGIN,
    SESSION_RESTORE,
    THEME_CHANGED,
    MOOD_CHANGED,
    DEMO_IMPORT,
    MANUAL,
}

fun deterministicHeartRateCloudId(
    sampleTimestamp: Long,
    heartRate: Int,
    sourceBatchId: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$sampleTimestamp:$heartRate:$sourceBatchId".toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
    return "hr_$digest"
}

fun cloudSyncErrorMessage(code: String): String =
    when (code) {
        "missing_token" -> "Sign in before syncing data."
        "unauthorized" -> "Session expired. Please sign in again."
        "network" -> "Unable to reach the cloud sync service."
        "server" -> "Cloud sync is temporarily unavailable."
        "malformed" -> "Cloud sync returned an unexpected response."
        "merge" -> "Some cloud records could not be merged."
        else -> "Cloud sync failed. Try again later."
    }
```

If the hash expected in the test differs after implementation, update the expected value to the real output from the deterministic function and keep it stable.

- [ ] **Step 4: Add sync metadata to Room entities**

Update `MoodRecordEntity` constructor:

```kotlin
data class MoodRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = java.util.UUID.randomUUID().toString(),
    val date: String,
    val moodType: String,
    val moodGroup: String,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
)
```

Update `HeartRateRawSampleEntity` constructor:

```kotlin
data class HeartRateRawSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = deterministicHeartRateCloudId(sampleTimestamp, heartRate, sourceBatchId),
    val sampleTimestamp: Long,
    val date: String,
    val heartRate: Int,
    val sourceBatchId: String,
    val updatedAt: Long = sampleTimestamp,
)
```

Add import:

```kotlin
import org.wit.vitasense.model.deterministicHeartRateCloudId
```

Update `SleepRecordEntity` constructor:

```kotlin
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = java.util.UUID.randomUUID().toString(),
    val date: String,
    val startAt: Long,
    val endAt: Long,
    val durationMinutes: Int,
    val avgHeartRate: Double? = null,
    val heartRateVariabilityHint: Double? = null,
    val sourceBatchId: String,
    val updatedAt: Long = endAt,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 5: Bump Room version**

Update `AppDatabase.kt`:

```kotlin
version = 3,
```

Keep the existing `.fallbackToDestructiveMigration()` in `AppContainer.kt`; this app already uses that migration strategy.

- [ ] **Step 6: Run model tests**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.CloudSyncModelsTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/model/CloudSyncModels.kt app/src/main/java/org/wit/vitasense/db/entity/MoodRecordEntity.kt app/src/main/java/org/wit/vitasense/db/entity/HeartRateRawSampleEntity.kt app/src/main/java/org/wit/vitasense/db/entity/SleepRecordEntity.kt app/src/main/java/org/wit/vitasense/db/AppDatabase.kt app/src/test/java/org/wit/vitasense/model/CloudSyncModelsTest.kt
git commit -m "feat: add Android sync metadata models"
```

---

### Task 4: Android DAO Sync Access

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/db/dao/MoodRecordDao.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/dao/HeartRateRawSampleDao.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/dao/SleepRecordDao.kt`

- [ ] **Step 1: Add mood sync DAO methods**

Add imports if missing:

```kotlin
import androidx.room.Query
import androidx.room.Upsert
```

Add methods:

```kotlin
@Query("SELECT * FROM mood_records WHERE deletedAt IS NULL ORDER BY createdAt DESC")
fun observeActiveMoodRecords(): Flow<List<MoodRecordEntity>>

@Query("SELECT * FROM mood_records ORDER BY createdAt ASC")
suspend fun getAllForSync(): List<MoodRecordEntity>

@Query("SELECT * FROM mood_records WHERE cloudId = :cloudId LIMIT 1")
suspend fun getByCloudId(cloudId: String): MoodRecordEntity?

@Upsert
suspend fun upsertForSync(entity: MoodRecordEntity)

@Query("UPDATE mood_records SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
suspend fun markDeleted(id: Long, deletedAt: Long)
```

If an existing visible-list query returns deleted records, update it to include `WHERE deletedAt IS NULL`.

- [ ] **Step 2: Add heart-rate sync DAO methods**

Add:

```kotlin
@Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp ASC")
suspend fun getAllForSync(): List<HeartRateRawSampleEntity>

@Query(
    """
    SELECT * FROM heart_rate_raw_samples
    WHERE sampleTimestamp = :sampleTimestamp
      AND heartRate = :heartRate
      AND sourceBatchId = :sourceBatchId
    LIMIT 1
    """,
)
suspend fun findDuplicate(
    sampleTimestamp: Long,
    heartRate: Int,
    sourceBatchId: String,
): HeartRateRawSampleEntity?

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertIgnore(entity: HeartRateRawSampleEntity): Long
```

- [ ] **Step 3: Add sleep sync DAO methods**

Add:

```kotlin
@Query("SELECT * FROM sleep_records WHERE deletedAt IS NULL ORDER BY date ASC")
suspend fun getAllActiveForSync(): List<SleepRecordEntity>

@Query("SELECT * FROM sleep_records WHERE date = :date LIMIT 1")
suspend fun getByDate(date: String): SleepRecordEntity?

@Upsert
suspend fun upsertForSync(entity: SleepRecordEntity)
```

If existing queries that power UI should hide deleted sleep records, add `deletedAt IS NULL` to those queries.

- [ ] **Step 4: Run compile checks**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: tests compile and either pass or reveal call-site updates required by the new entity constructor order.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/db/dao/MoodRecordDao.kt app/src/main/java/org/wit/vitasense/db/dao/HeartRateRawSampleDao.kt app/src/main/java/org/wit/vitasense/db/dao/SleepRecordDao.kt
git commit -m "feat: add Room sync DAO access"
```

---

### Task 5: Android CloudSyncRepository

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/repository/CloudSyncRepository.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultCloudSyncRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultCloudSyncRepositoryTest.kt`

- [ ] **Step 1: Write repository tests around a fake transport**

Create `DefaultCloudSyncRepositoryTest.kt` with tests for:

```kotlin
@Test
fun missingTokenReturnsErrorWithoutNetworkCall() = runTest { /* expect missing_token */ }

@Test
fun bootstrapAppliesNewerCloudThemeAndRecomputes() = runTest { /* fake bootstrap response, assert settings changed */ }

@Test
fun pushSendsLocalSnapshotWithNoAiKey() = runTest { /* assert JSON contains mood/hr/sleep and not ai_api_key */ }

@Test
fun failedBootstrapStoresSyncErrorButDoesNotThrow() = runTest { /* fake 500, assert result.success false */ }
```

Use existing fake DAO/repository patterns from current unit tests; keep fake network as a small function parameter on the repository rather than introducing a new library.

- [ ] **Step 2: Add sync status methods to `SettingsRepository`**

Add:

```kotlin
fun observeLastSyncAt(): Flow<Long?>
fun observeSyncStatus(): Flow<String>
fun observeSyncError(): Flow<String>
suspend fun getLastSyncAt(): Long?
suspend fun getSyncStatus(): String
suspend fun getSyncError(): String
suspend fun setSyncStatus(status: String, error: String? = null, syncedAt: Long? = null)
```

- [ ] **Step 3: Implement sync status persistence in `DefaultSettingsRepository`**

Use keys:

```kotlin
private const val KEY_LAST_SYNC_AT = "last_sync_at"
private const val KEY_SYNC_STATUS = "sync_status"
private const val KEY_SYNC_ERROR = "sync_error"
```

Store `last_sync_at` as a stringified `Long`, default `sync_status` to `idle`, and default `sync_error` to an empty string.

- [ ] **Step 4: Add `CloudSyncRepository` interface**

```kotlin
package org.wit.vitasense.repository

import org.wit.vitasense.model.CloudSyncResult
import org.wit.vitasense.model.SyncReason

interface CloudSyncRepository {
    suspend fun bootstrapAfterLogin(): CloudSyncResult
    suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult
    suspend fun syncNow(): CloudSyncResult
}
```

- [ ] **Step 5: Implement `DefaultCloudSyncRepository`**

Constructor shape:

```kotlin
class DefaultCloudSyncRepository(
    private val baseUrl: String,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val moodRecordDao: MoodRecordDao,
    private val heartRateDao: HeartRateRawSampleDao,
    private val sleepRecordDao: SleepRecordDao,
    private val recomputeEngine: HealthRecomputeEngine,
    private val request: suspend (method: String, path: String, token: String, body: String?) -> NetworkResponse = ::defaultRequest,
) : CloudSyncRepository
```

Required behavior:

```kotlin
override suspend fun bootstrapAfterLogin(): CloudSyncResult {
    val token = settingsRepository.getAuthToken()
    if (token.isBlank()) return fail("missing_token")
    return runCatching {
        settingsRepository.setSyncStatus("syncing")
        val response = request("GET", "/api/v1/sync/bootstrap", token, null)
        if (response.statusCode == 401) return fail("unauthorized")
        if (response.statusCode !in 200..299) return fail("server")
        val snapshot = parseBootstrapResponse(response.body)
        mergeSnapshot(snapshot)
        recomputeEngine.recomputeAllDates()
        settingsRepository.setSyncStatus("synced", syncedAt = System.currentTimeMillis())
        CloudSyncResult(true, "Cloud sync complete.", snapshot.serverTime)
    }.getOrElse {
        fail("network")
    }
}

override suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult {
    val token = settingsRepository.getAuthToken()
    if (token.isBlank()) return fail("missing_token")
    return runCatching {
        settingsRepository.setSyncStatus("syncing")
        val body = buildPushPayload(reason)
        val response = request("POST", "/api/v1/sync/push", token, body)
        if (response.statusCode == 401) return fail("unauthorized")
        if (response.statusCode !in 200..299) return fail("server")
        settingsRepository.setSyncStatus("synced", syncedAt = System.currentTimeMillis())
        CloudSyncResult(true, "Cloud sync complete.")
    }.getOrElse {
        fail("network")
    }
}

override suspend fun syncNow(): CloudSyncResult {
    val bootstrap = bootstrapAfterLogin()
    if (!bootstrap.success) return bootstrap
    return pushLocalSnapshot(SyncReason.MANUAL)
}
```

Merge rules:

```kotlin
private suspend fun mergeMood(record: CloudSyncMoodRecord) {
    val existing = moodRecordDao.getByCloudId(record.id)
    if (existing == null || record.updatedAt > existing.updatedAt || (record.deletedAt ?: 0L) > (existing.deletedAt ?: 0L)) {
        moodRecordDao.upsertForSync(record.toEntity(existing?.id ?: 0L))
    }
}

private suspend fun mergeHeartRate(sample: CloudSyncHeartRateSample) {
    heartRateDao.insertIgnore(sample.toEntity())
}

private suspend fun mergeSleep(record: CloudSyncSleepRecord) {
    val existing = sleepRecordDao.getByDate(record.date)
    val shouldKeepCloud = existing == null ||
        record.updatedAt > existing.updatedAt ||
        (record.updatedAt == existing.updatedAt && record.durationMinutes > existing.durationMinutes)
    if (shouldKeepCloud) sleepRecordDao.upsertForSync(record.toEntity(existing?.id ?: 0L))
}
```

Implement JSON using `org.json.JSONObject`/`JSONArray` to match existing Android networking style.

- [ ] **Step 6: Run repository tests**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultCloudSyncRepositoryTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/repository/CloudSyncRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultCloudSyncRepository.kt app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultCloudSyncRepositoryTest.kt
git commit -m "feat: add Android cloud sync repository"
```

---

### Task 6: Sync Triggers

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAuthRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultHealthRepository.kt`
- Test: existing repository tests plus new trigger tests.

- [ ] **Step 1: Inject `CloudSyncRepository` in `AppContainer`**

Add:

```kotlin
import org.wit.vitasense.data.repository.DefaultCloudSyncRepository
import org.wit.vitasense.repository.CloudSyncRepository
```

Add lazy property before dependent repositories:

```kotlin
val cloudSyncRepository: CloudSyncRepository by lazy {
    DefaultCloudSyncRepository(
        baseUrl = DEFAULT_AUTH_BASE_URL,
        settingsRepository = settingsRepository,
        database = database,
        moodRecordDao = database.moodRecordDao(),
        heartRateDao = database.heartRateRawSampleDao(),
        sleepRecordDao = database.sleepRecordDao(),
        recomputeEngine = recomputeEngine,
    )
}
```

- [ ] **Step 2: Trigger bootstrap after auth success without failing auth**

Update `DefaultAuthRepository` constructor:

```kotlin
class DefaultAuthRepository(
    private val settingsRepository: SettingsRepository,
    private val cloudSyncRepository: CloudSyncRepository? = null,
)
```

After successful `persistSession(...)` in login/register/session restore:

```kotlin
runCatching { cloudSyncRepository?.bootstrapAfterLogin() }
```

For session restore, pass `SyncReason.SESSION_RESTORE` only if the repository exposes a reason-specific method; otherwise use `bootstrapAfterLogin()` and store the reason in logs only.

- [ ] **Step 3: Trigger theme pushes**

Update `DefaultSettingsRepository` constructor:

```kotlin
class DefaultSettingsRepository(
    private val appSettingDao: AppSettingDao,
    private val cloudSyncRepositoryProvider: (() -> CloudSyncRepository?)? = null,
)
```

After `setThemeMode()` and `setThemeFamily()` persist:

```kotlin
runCatching {
    cloudSyncRepositoryProvider?.invoke()?.pushLocalSnapshot(SyncReason.THEME_CHANGED)
}
```

Use a provider lambda to avoid eager circular initialization in `AppContainer`.

- [ ] **Step 4: Trigger mood pushes**

Update `DefaultMoodRepository` constructor:

```kotlin
class DefaultMoodRepository(
    private val moodRecordDao: MoodRecordDao,
    private val cloudSyncRepositoryProvider: (() -> CloudSyncRepository?)? = null,
)
```

After add and delete operations:

```kotlin
runCatching {
    cloudSyncRepositoryProvider?.invoke()?.pushLocalSnapshot(SyncReason.MOOD_CHANGED)
}
```

For delete, prefer `markDeleted(id, System.currentTimeMillis())` over hard deletion so tombstones sync.

- [ ] **Step 5: Trigger demo-import pushes**

Update `DefaultHealthRepository` constructor with the same provider shape and call after import plus recompute:

```kotlin
runCatching {
    cloudSyncRepositoryProvider?.invoke()?.pushLocalSnapshot(SyncReason.DEMO_IMPORT)
}
```

- [ ] **Step 6: Add trigger tests**

Add tests:

```kotlin
@Test
fun loginSucceedsWhenBootstrapFails() = runTest { /* fake sync returns error, auth result still success */ }

@Test
fun themeChangePushesLocalSnapshot() = runTest { /* assert SyncReason.THEME_CHANGED */ }

@Test
fun moodDeleteCreatesTombstoneAndPushes() = runTest { /* assert deletedAt set and SyncReason.MOOD_CHANGED */ }
```

- [ ] **Step 7: Run Android unit tests**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/AppContainer.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultAuthRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultHealthRepository.kt app/src/test/java/org/wit/vitasense
git commit -m "feat: trigger cloud sync from app changes"
```

---

### Task 7: Settings Manual Sync UI

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt` if Settings dependencies are factory-created there.

- [ ] **Step 1: Add strings**

```xml
<string name="settings_cloud_sync_title">Cloud sync</string>
<string name="settings_cloud_sync_subtitle_idle">Sync theme and health source data after signing in.</string>
<string name="settings_cloud_sync_subtitle_syncing">Syncing cloud data...</string>
<string name="settings_cloud_sync_subtitle_synced">Last synced: %1$s</string>
<string name="settings_cloud_sync_button">Sync now</string>
<string name="settings_cloud_sync_error">Cloud sync failed: %1$s</string>
```

- [ ] **Step 2: Add Settings layout controls**

Add a compact row near the auth/account section in `fragment_settings.xml`:

```xml
<LinearLayout
    android:id="@+id/cloudSyncSection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="16dp">

    <TextView
        android:id="@+id/cloudSyncTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/settings_cloud_sync_title"
        android:textAppearance="@style/TextAppearance.MaterialComponents.Subtitle1" />

    <TextView
        android:id="@+id/cloudSyncSubtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="@string/settings_cloud_sync_subtitle_idle"
        android:textAppearance="@style/TextAppearance.MaterialComponents.Body2" />

    <Button
        android:id="@+id/syncNowButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/settings_cloud_sync_button" />

    <ProgressBar
        android:id="@+id/cloudSyncProgress"
        style="?android:attr/progressBarStyleSmall"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:visibility="gone" />
</LinearLayout>
```

Adapt styles to the existing Settings layout if it already uses custom text appearances or Material widgets.

- [ ] **Step 3: Add ViewModel state and action**

In `SettingsViewModel`, add state fields:

```kotlin
data class CloudSyncUiState(
    val status: String = "idle",
    val error: String = "",
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
)
```

Inject `CloudSyncRepository` and add:

```kotlin
fun syncNow() {
    if (_cloudSyncUiState.value.isSyncing) return
    viewModelScope.launch {
        _cloudSyncUiState.value = _cloudSyncUiState.value.copy(isSyncing = true, status = "syncing", error = "")
        val result = cloudSyncRepository.syncNow()
        _cloudSyncUiState.value = if (result.success) {
            CloudSyncUiState(status = "synced", lastSyncAt = System.currentTimeMillis())
        } else {
            CloudSyncUiState(status = "error", error = result.message)
        }
    }
}
```

- [ ] **Step 4: Bind UI state in `SettingsFragment`**

Add click binding:

```kotlin
binding.syncNowButton.setOnClickListener {
    viewModel.syncNow()
}
```

Collect state and render:

```kotlin
binding.cloudSyncProgress.isVisible = state.isSyncing
binding.syncNowButton.isEnabled = !state.isSyncing
binding.cloudSyncSubtitle.text = when {
    state.isSyncing -> getString(R.string.settings_cloud_sync_subtitle_syncing)
    state.status == "error" -> getString(R.string.settings_cloud_sync_error, state.error)
    state.lastSyncAt != null -> getString(R.string.settings_cloud_sync_subtitle_synced, formatSyncTime(state.lastSyncAt))
    else -> getString(R.string.settings_cloud_sync_subtitle_idle)
}
```

Use the project’s existing date/time formatter if one exists; otherwise add a private formatter using `java.text.DateFormat.getDateTimeInstance()`.

- [ ] **Step 5: Run UI compile tests**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --no-daemon
./gradlew.bat :app:assembleDebug --no-daemon
```

Expected: unit tests pass and debug APK builds.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt app/src/main/res/layout/fragment_settings.xml app/src/main/res/values/strings.xml app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt
git commit -m "feat: add manual cloud sync settings UI"
```

---

### Task 8: Backend Deploy And End-To-End Verification

**Files:**
- Modify only if needed from verification feedback: `python_auth_api/main.py`, `python_auth_api/smoke_test.py`, Android files from earlier tasks.

- [ ] **Step 1: Run full local verification**

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/smoke_test.py
python python_auth_api/smoke_test.py
$env:GRADLE_USER_HOME='D:\1\yidong\mid_1\project\.gradle-user-home'
./gradlew.bat :app:testDebugUnitTest --no-daemon
./gradlew.bat :app:assembleDebug --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 2: Upload backend files to the server**

Use the existing server path and do not modify `smartgrid.np5.top`:

```powershell
scp python_auth_api/main.py root@103.23.148.85:/opt/vitasense-auth-api/main.py
scp python_auth_api/smoke_test.py root@103.23.148.85:/opt/vitasense-auth-api/smoke_test.py
```

- [ ] **Step 3: Restart only the VitaSense service**

```powershell
ssh root@103.23.148.85 "systemctl restart vitasense-auth-api.service && systemctl status vitasense-auth-api.service --no-pager"
```

Expected: `Active: active (running)`.

- [ ] **Step 4: Verify deployed sync auth behavior**

```powershell
Invoke-RestMethod -Method Get -Uri "https://server.np5.top/api/v1/sync/bootstrap"
```

Expected: HTTP 401 JSON with `success: false`.

Then register/login a test user or reuse the smoke script against `https://server.np5.top`, push a small sync payload, and bootstrap it back. Confirm a second user does not receive the first user's records.

- [ ] **Step 5: Manual Android verification**

1. Install debug APK.
2. Register or log in as user A.
3. Change theme and import demo health data.
4. Tap Settings `Sync now`; expect progress indicator then success timestamp.
5. Clear app data or reinstall.
6. Log in as user A; expect theme, mood, heart-rate, and sleep data to restore.
7. Log in as user B; expect user A data absent.
8. Use `Delete All Data`; confirm it is local-only and cloud data can restore on next sync.

- [ ] **Step 6: Commit any verification fixes**

```powershell
git status --short
git add <only files changed by cloud sync work>
git commit -m "fix: stabilize cloud sync verification"
```

Skip this commit if no fixes were needed.

---

## Self-Review

- Spec coverage: backend tables/endpoints, authenticated user scoping, Android Room metadata, bootstrap after login/session restore, push after theme/mood/demo import, manual Settings sync, conflict rules, sync status/error messages, no AI key sync, no background sync, no incremental pull, and local-only Delete All Data are all mapped to tasks.
- Placeholder scan: no task depends on undefined "later" work; where existing code shape may vary, the plan gives concrete constructor/method shapes and commands.
- Type consistency: `CloudSyncRepository`, `CloudSyncResult`, `SyncReason`, `CloudSyncSnapshot`, and entity sync fields are introduced before later tasks reference them.
- Risk notes: Room still uses destructive migration, matching current app behavior. `DefaultSettingsRepository` needs a provider lambda for sync pushes to avoid circular `AppContainer` initialization. The deterministic heart-rate test expected hash should be checked once during implementation because the final hash is derived from the exact string format.
