# Family Health Score Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in Family health score sharing switch so each member can choose whether family members see their daily total health score.

**Architecture:** Extend the existing Family status snapshot instead of creating a separate sharing subsystem. The backend stores and serializes only score summary fields, while Android reads the latest local risk score, sends it through `FamilyStatusSnapshot`, and renders a score row plus a current-user switch in member cards.

**Tech Stack:** Kotlin, XML Views, ViewBinding, Coroutines/StateFlow, Room DAO flows through `HealthRepository`, `HttpURLConnection`, FastAPI, SQLite, Python stdlib tests, existing Gradle/JUnit compile gates.

---

## Scope Check

This plan touches backend and Android because the Android UI cannot safely show shared score state without a server contract. The extension is still one cohesive feature: Family status snapshot gains optional health-score summary fields with opt-in visibility.

The plan deliberately excludes detailed metrics, history, ranking, charts, and owner controls over other members' sharing.

---

## File Map

### Python API

- Modify `python_auth_api/main.py`
  - Add score fields to `FamilyStatusRequest`.
  - Add migration helper for `family_status_snapshots` score columns.
  - Store optional score fields in `POST /api/v1/families/{family_id}/status`.
  - Return score fields in `GET /api/v1/families/me`.
  - Never return raw health metric fields.
- Modify `python_auth_api/family_endpoints_test.py`
  - Add tests for opt-in score visibility, opt-out hiding, and privacy response shape.

### Android Models And Repository

- Modify `app/src/main/java/org/wit/vitasense/model/FamilyModels.kt`
  - Add score fields to `FamilyMember` and `FamilyStatusSnapshot`.
  - Parse score fields from JSON.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt`
  - Include score fields in status payload.
- Modify `app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt`
  - Assert score fields parse correctly and default to hidden.
- Modify `app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt`
  - Assert status payload includes only allowed score summary fields.

### Android ViewModel And UI

- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
  - Add health score display model fields and current-user toggle state.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
  - Map shared score fields to `Health Score 82`, `No score today`, or `Health score not shared`.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`
  - Inject `HealthRepository`.
  - Build score snapshot from latest local risk only when sharing is enabled.
  - Add `setShareHealthScore(enabled: Boolean)`.
- Modify `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
  - Pass `appContainer.healthRepository` into `FamilyViewModel`.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`
  - Bind health score row and current-user switch.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt`
  - Wire switch callback to `FamilyViewModel`.
- Modify `app/src/main/res/layout/item_family_member.xml`
  - Add score row and switch controls.
- Modify `app/src/main/res/values/strings.xml`
  - Add `family_share_health_score` for the current-user switch.
- Modify `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`
  - Assert score display states.
- Modify `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`
  - Assert toggle behavior and snapshot fields.
- Update any existing fake `HealthRepository` / `FamilyMember` / `FamilyStatusSnapshot` constructors in tests.

---

## Shared Field Contract

Use these exact wire keys:

```text
share_health_score
health_score
health_score_label
health_score_updated_at
```

Use these Kotlin property names:

```kotlin
val shareHealthScore: Boolean
val healthScore: Int?
val healthScoreLabel: String?
val healthScoreUpdatedAt: Long?
```

Use these labels:

```text
Health score not shared
No score today
Health Score 82
Stable
Watch today
Needs support
```

Score label thresholds:

```text
score >= 80 -> Stable
score >= 60 -> Watch today
score < 60 -> Needs support
```

---

### Task 1: Backend Score Fields And Privacy

**Files:**
- Modify: `python_auth_api/main.py`
- Modify: `python_auth_api/family_endpoints_test.py`

- [ ] **Step 1: Add failing backend test for score opt-in and opt-out**

Append this test to `python_auth_api/family_endpoints_test.py`:

```python
def test_family_health_score_sharing_is_opt_in_and_hides_details():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()
        client = TestClient(module.app)

        token_owner, owner = register(client, "score-owner")
        token_member, member = register(client, "score-member")

        created = client.post(
            "/api/v1/families",
            json={"name": "Score Family"},
            headers=auth_headers(token_owner),
        )
        assert created.status_code == 200, created.text
        family = created.json()["family"]
        joined = client.post(
            "/api/v1/families/join",
            json={"invite_code": family["invite_code"]},
            headers=auth_headers(token_member),
        )
        assert joined.status_code == 200, joined.text

        shared = client.post(
            f"/api/v1/families/{family['id']}/status",
            json={
                "mood_type": "CALM",
                "mood_note": "steady",
                "status_label": "Checked in today",
                "updated_at": 1770000000000,
                "share_health_score": True,
                "health_score": 82,
                "health_score_label": "Stable",
                "health_score_updated_at": 1770000000000,
                "rmssd": 40,
                "heart_rate": 60,
                "sleep_minutes": 420,
            },
            headers=auth_headers(token_member),
        )
        assert shared.status_code == 200, shared.text

        body = client.get("/api/v1/families/me", headers=auth_headers(token_owner)).json()
        member_card = next(item for item in body["family"]["members"] if item["user_id"] == member["id"])
        assert member_card["share_health_score"] is True
        assert member_card["health_score"] == 82
        assert member_card["health_score_label"] == "Stable"
        assert member_card["health_score_updated_at"] == 1770000000000
        raw = str(body).lower()
        assert "rmssd" not in raw
        assert "heart_rate" not in raw
        assert "sleep_minutes" not in raw
        assert "total_score" not in raw
        assert "anomaly_flags" not in raw

        hidden = client.post(
            f"/api/v1/families/{family['id']}/status",
            json={
                "mood_type": "CALM",
                "mood_note": "steady",
                "status_label": "Checked in today",
                "updated_at": 1770000001000,
                "share_health_score": False,
                "health_score": 91,
                "health_score_label": "Stable",
                "health_score_updated_at": 1770000001000,
            },
            headers=auth_headers(token_member),
        )
        assert hidden.status_code == 200, hidden.text

        body = client.get("/api/v1/families/me", headers=auth_headers(token_owner)).json()
        member_card = next(item for item in body["family"]["members"] if item["user_id"] == member["id"])
        assert member_card["share_health_score"] is False
        assert member_card["health_score"] is None
        assert member_card["health_score_label"] is None
        assert member_card["health_score_updated_at"] is None
```

- [ ] **Step 2: Run backend test and verify it fails**

Run:

```powershell
cd python_auth_api
python family_endpoints_test.py
```

Expected: fails because `share_health_score` / `health_score` fields are absent from the response.

- [ ] **Step 3: Extend `FamilyStatusRequest`**

In `python_auth_api/main.py`, update `FamilyStatusRequest` to:

```python
class FamilyStatusRequest(BaseModel):
    mood_type: str | None = None
    mood_note: str | None = None
    status_label: str
    updated_at: int
    share_health_score: bool = False
    health_score: int | None = None
    health_score_label: str | None = None
    health_score_updated_at: int | None = None
```

- [ ] **Step 4: Add schema migration helper**

In `python_auth_api/main.py`, add these helpers near existing migration helpers:

```python
def table_columns(connection: sqlite3.Connection, table_name: str) -> set[str]:
    return {str(row["name"]) for row in connection.execute(f"PRAGMA table_info({table_name})").fetchall()}


def ensure_family_status_score_columns(connection: sqlite3.Connection) -> None:
    if not table_exists(connection, "family_status_snapshots"):
        return
    columns = table_columns(connection, "family_status_snapshots")
    if "share_health_score" not in columns:
        connection.execute("ALTER TABLE family_status_snapshots ADD COLUMN share_health_score INTEGER NOT NULL DEFAULT 0")
    if "health_score" not in columns:
        connection.execute("ALTER TABLE family_status_snapshots ADD COLUMN health_score INTEGER")
    if "health_score_label" not in columns:
        connection.execute("ALTER TABLE family_status_snapshots ADD COLUMN health_score_label TEXT")
    if "health_score_updated_at" not in columns:
        connection.execute("ALTER TABLE family_status_snapshots ADD COLUMN health_score_updated_at INTEGER")
```

Then call it inside `initialize_database()` after table creation and before `connection.commit()`:

```python
ensure_family_status_score_columns(connection)
```

- [ ] **Step 5: Add columns to create-table SQL**

In the `CREATE TABLE IF NOT EXISTS family_status_snapshots` block, add:

```sql
share_health_score INTEGER NOT NULL DEFAULT 0,
health_score INTEGER,
health_score_label TEXT,
health_score_updated_at INTEGER,
```

Place them before the `PRIMARY KEY(family_id, user_id)` line.

- [ ] **Step 6: Store score fields in status upsert**

In `update_family_status()`, replace the current `INSERT INTO family_status_snapshots(...)` statement with:

```python
share_health_score = 1 if payload.share_health_score else 0
health_score = payload.health_score if payload.share_health_score else None
health_score_label = payload.health_score_label if payload.share_health_score else None
health_score_updated_at = payload.health_score_updated_at if payload.share_health_score else None
connection.execute(
    """
    INSERT INTO family_status_snapshots(
        family_id, user_id, mood_type, mood_note, status_label, updated_at,
        share_health_score, health_score, health_score_label, health_score_updated_at
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(family_id, user_id) DO UPDATE SET
        mood_type = excluded.mood_type,
        mood_note = excluded.mood_note,
        status_label = excluded.status_label,
        updated_at = excluded.updated_at,
        share_health_score = excluded.share_health_score,
        health_score = excluded.health_score,
        health_score_label = excluded.health_score_label,
        health_score_updated_at = excluded.health_score_updated_at
    """.strip(),
    (
        family_id,
        user_id,
        payload.mood_type,
        payload.mood_note,
        payload.status_label.strip(),
        payload.updated_at,
        share_health_score,
        health_score,
        health_score_label,
        health_score_updated_at,
    ),
)
```

- [ ] **Step 7: Serialize score fields**

In `serialize_family()`, add these selected columns:

```sql
family_status_snapshots.share_health_score,
family_status_snapshots.health_score,
family_status_snapshots.health_score_label,
family_status_snapshots.health_score_updated_at,
```

When building each member dictionary, add:

```python
share_health_score = bool(row["share_health_score"])
member_payload = {
    # existing fields...
    "share_health_score": share_health_score,
    "health_score": row["health_score"] if share_health_score else None,
    "health_score_label": row["health_score_label"] if share_health_score else None,
    "health_score_updated_at": row["health_score_updated_at"] if share_health_score else None,
}
```

If the serializer currently builds the dictionary inline, use the same rule inside the inline dictionary values.

- [ ] **Step 8: Run backend verification**

Run:

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/family_endpoints_test.py
cd python_auth_api
python family_endpoints_test.py
python sync_endpoints_test.py
python sync_schema_test.py
```

Expected: all commands exit `0`.

- [ ] **Step 9: Commit**

```powershell
git add python_auth_api/main.py python_auth_api/family_endpoints_test.py
git commit -m "feat: add family health score sharing api"
```

---

### Task 2: Android Models And Repository Payload

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/model/FamilyModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt`
- Modify: `app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt`
- Modify: `app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt`
- Update: tests that construct `FamilyMember` or `FamilyStatusSnapshot`

- [ ] **Step 1: Add failing model parse test**

In `FamilyModelsTest.kt`, add:

```kotlin
@Test
fun parses_shared_health_score_fields() {
    val family =
        parseFamily(
            """
            {
              "id": 1,
              "name": "Stone Family",
              "invite_code": "ABC123",
              "current_user_role": "member",
              "members": [
                {
                  "user_id": 2,
                  "full_name": "Ben Stone",
                  "username": "ben",
                  "role": "member",
                  "mood_type": "CALM",
                  "mood_note": "steady",
                  "status_label": "Checked in today",
                  "status_updated_at": 1770000000000,
                  "support_count_today": 0,
                  "share_health_score": true,
                  "health_score": 82,
                  "health_score_label": "Stable",
                  "health_score_updated_at": 1770000000000
                }
              ]
            }
            """.trimIndent(),
        )

    val member = family.members.single()
    assertEquals(true, member.shareHealthScore)
    assertEquals(82, member.healthScore)
    assertEquals("Stable", member.healthScoreLabel)
    assertEquals(1770000000000, member.healthScoreUpdatedAt)
}
```

- [ ] **Step 2: Add failing repository payload test**

In `DefaultFamilyRepositoryTest.kt`, add:

```kotlin
@Test
fun upsertStatus_payload_includes_only_score_summary_fields_when_enabled() =
    runBlocking {
        var capturedBody = ""
        val repository =
            DefaultFamilyRepository(
                baseUrlProvider = { "https://server.np5.top" },
                tokenProvider = { "token" },
                request = { _, _, _, body ->
                    capturedBody = body.orEmpty()
                    FamilyNetworkResponse(200, """{"success":true,"family":null}""")
                },
            )

        repository.upsertStatus(
            familyId = 5,
            snapshot =
                FamilyStatusSnapshot(
                    moodType = "CALM",
                    moodNote = "steady",
                    statusLabel = "Checked in today",
                    updatedAt = 1770000000000,
                    shareHealthScore = true,
                    healthScore = 82,
                    healthScoreLabel = "Stable",
                    healthScoreUpdatedAt = 1770000000000,
                ),
        )

        val payload = JSONObject(capturedBody)
        assertEquals(true, payload.getBoolean("share_health_score"))
        assertEquals(82, payload.getInt("health_score"))
        assertEquals("Stable", payload.getString("health_score_label"))
        assertEquals(1770000000000, payload.getLong("health_score_updated_at"))
        val raw = capturedBody.lowercase()
        assertFalse(raw.contains("rmssd"))
        assertFalse(raw.contains("heart_rate"))
        assertFalse(raw.contains("sleep"))
        assertFalse(raw.contains("anomaly"))
    }
```

If `JSONObject` or `assertFalse` is not imported, add:

```kotlin
import org.json.JSONObject
import org.junit.Assert.assertFalse
```

- [ ] **Step 3: Run compile and confirm failure**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: fails because `shareHealthScore`, `healthScore`, `healthScoreLabel`, and `healthScoreUpdatedAt` do not exist yet.

- [ ] **Step 4: Extend `FamilyMember` and `FamilyStatusSnapshot`**

In `FamilyModels.kt`, update `FamilyMember`:

```kotlin
data class FamilyMember(
    val userId: Long,
    val fullName: String,
    val username: String,
    val role: FamilyRole,
    val moodType: String?,
    val moodNote: String?,
    val statusLabel: String,
    val statusUpdatedAt: Long?,
    val supportCountToday: Int,
    val latestSupportType: FamilySupportType?,
    val latestSupportSentAt: Long?,
    val shareHealthScore: Boolean = false,
    val healthScore: Int? = null,
    val healthScoreLabel: String? = null,
    val healthScoreUpdatedAt: Long? = null,
)
```

Update `FamilyStatusSnapshot`:

```kotlin
data class FamilyStatusSnapshot(
    val moodType: String?,
    val moodNote: String?,
    val statusLabel: String,
    val updatedAt: Long,
    val shareHealthScore: Boolean = false,
    val healthScore: Int? = null,
    val healthScoreLabel: String? = null,
    val healthScoreUpdatedAt: Long? = null,
)
```

- [ ] **Step 5: Parse score fields**

In `parseFamily()`, add fields to `FamilyMember(...)`:

```kotlin
shareHealthScore = member.optBoolean("share_health_score", false),
healthScore = member.optNullableInt("health_score"),
healthScoreLabel = member.optionalString("health_score_label"),
healthScoreUpdatedAt = member.optNullableLong("health_score_updated_at"),
```

Add this helper near `optNullableLong`:

```kotlin
fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) {
        optInt(name)
    } else {
        null
    }
```

- [ ] **Step 6: Include payload fields**

In `DefaultFamilyRepository.upsertStatus()`, extend the `JSONObject()` chain:

```kotlin
.put("share_health_score", snapshot.shareHealthScore)
.put("health_score", if (snapshot.shareHealthScore) snapshot.healthScore else JSONObject.NULL)
.put("health_score_label", if (snapshot.shareHealthScore) snapshot.healthScoreLabel else JSONObject.NULL)
.put("health_score_updated_at", if (snapshot.shareHealthScore) snapshot.healthScoreUpdatedAt else JSONObject.NULL)
```

- [ ] **Step 7: Update constructor call sites**

Run:

```powershell
rg -n "FamilyMember\\(|FamilyStatusSnapshot\\(" app/src/test app/src/main
```

For tests that fail to compile, add named defaults if the constructor call is positional. Prefer named arguments:

```kotlin
shareHealthScore = false,
healthScore = null,
healthScoreLabel = null,
healthScoreUpdatedAt = null,
```

Named constructor calls that already rely on defaults do not need edits.

- [ ] **Step 8: Run Android compile**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/model/FamilyModels.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt app/src/test/java/org/wit/vitasense
git commit -m "feat: add family health score models"
```

---

### Task 3: ViewModel Score Sync And Toggle

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`
- Update: fake `HealthRepository` implementations in tests as needed

- [ ] **Step 1: Add failing ViewModel test for enabled sharing**

In `FamilyViewModelTest.kt`, add imports:

```kotlin
import kotlinx.coroutines.flow.flowOf
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.repository.HealthRepository
```

Add this test:

```kotlin
@Test
fun enabling_health_score_sharing_syncs_latest_score_summary() =
    runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val familyRepository = FakeFamilyRepository()
        familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1))
        val healthRepository = FakeHealthRepository(risk(totalScore = 82, date = "2026-06-03"))
        val viewModel =
            FamilyViewModel(
                authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                familyRepository = familyRepository,
                moodRepository = FakeMoodRepository(),
                healthRepository = healthRepository,
                scope = scope,
            )
        val collector = collectState(viewModel, scope)

        yield()
        viewModel.setShareHealthScore(true)
        yield()

        assertEquals(true, familyRepository.lastStatusSnapshot?.shareHealthScore)
        assertEquals(82, familyRepository.lastStatusSnapshot?.healthScore)
        assertEquals("Stable", familyRepository.lastStatusSnapshot?.healthScoreLabel)

        collector.cancel()
        scope.coroutineContext[Job]?.cancel()
        Unit
    }
```

Add this test:

```kotlin
@Test
fun disabling_health_score_sharing_syncs_hidden_score_state() =
    runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val familyRepository = FakeFamilyRepository()
        familyRepository.seedFamily(familyNamed("Ava Family", currentUserId = 1, shareHealthScore = true))
        val viewModel =
            FamilyViewModel(
                authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
                familyRepository = familyRepository,
                moodRepository = FakeMoodRepository(),
                healthRepository = FakeHealthRepository(risk(totalScore = 91, date = "2026-06-03")),
                scope = scope,
            )
        val collector = collectState(viewModel, scope)

        yield()
        viewModel.setShareHealthScore(false)
        yield()

        assertEquals(false, familyRepository.lastStatusSnapshot?.shareHealthScore)
        assertEquals(null, familyRepository.lastStatusSnapshot?.healthScore)
        assertEquals(null, familyRepository.lastStatusSnapshot?.healthScoreLabel)

        collector.cancel()
        scope.coroutineContext[Job]?.cancel()
        Unit
    }
```

- [ ] **Step 2: Add fake health repository helpers**

In `FamilyViewModelTest.kt`, add:

```kotlin
private class FakeHealthRepository(
    private val latestRisk: RiskAssessmentRecordEntity? = null,
) : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)
    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)
    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> = flowOf(latestRisk)
    override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> = flowOf(emptyList())
    override fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>> = flowOf(emptyList())
    override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> = emptyList()
    override suspend fun importDemoBundle(bundleId: String): ImportOperationResult =
        ImportOperationResult(ImportStatus.SUCCESS, "unused", 0, 0, 0, 0)
    override suspend fun importRawJson(raw: String, sourceName: String): ImportOperationResult =
        ImportOperationResult(ImportStatus.SUCCESS, "unused", 0, 0, 0, 0)
    override suspend fun clearAllData() = Unit
}

private fun risk(
    totalScore: Int,
    date: String,
) = RiskAssessmentRecordEntity(
    date = date,
    totalScore = totalScore,
    riskLevel = "low",
    sleepScore = 30,
    hrvScore = 25,
    restingHrScore = 15,
    avgHrScore = totalScore - 70,
    explanation = "unused",
    suggestionText = "unused",
)
```

Update `familyNamed()` helper signature:

```kotlin
private fun familyNamed(
    name: String,
    currentUserId: Long,
    shareHealthScore: Boolean = false,
): Family =
```

Add these fields to its `FamilyMember`:

```kotlin
shareHealthScore = shareHealthScore,
healthScore = if (shareHealthScore) 82 else null,
healthScoreLabel = if (shareHealthScore) "Stable" else null,
healthScoreUpdatedAt = if (shareHealthScore) 1770000000000 else null,
```

- [ ] **Step 3: Run compile and confirm failure**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: fails because `FamilyViewModel` has no `healthRepository` parameter or `setShareHealthScore()`.

- [ ] **Step 4: Add sharing state to member UI model**

In `FamilyUiModels.kt`, add this field to `FamilyMemberUiModel`:

```kotlin
val shareHealthScore: Boolean,
```

In `FamilyUiMapper.toUiModel()`, pass:

```kotlin
shareHealthScore = shareHealthScore,
```

This field is used by `FamilyViewModel` to preserve the current user's existing sharing state during automatic mood-status sync. Task 4 will add display text and the visible switch.

- [ ] **Step 5: Inject `HealthRepository`**

In `FamilyViewModel.kt`, add:

```kotlin
import kotlinx.coroutines.flow.first
import org.wit.vitasense.repository.HealthRepository
```

Update constructor:

```kotlin
class FamilyViewModel(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val moodRepository: MoodRepository,
    private val healthRepository: HealthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
```

Update `VitaSenseViewModelFactory.kt`:

```kotlin
FamilyViewModel(
    authRepository = appContainer.authRepository,
    familyRepository = appContainer.familyRepository,
    moodRepository = appContainer.moodRepository,
    healthRepository = appContainer.healthRepository,
) as T
```

Update all test `FamilyViewModel(...)` calls to include:

```kotlin
healthRepository = FakeHealthRepository(),
```

- [ ] **Step 6: Add score label helper and toggle action**

In `FamilyViewModel.kt`, add:

```kotlin
fun setShareHealthScore(enabled: Boolean) {
    syncStatusWithHealthScore(enabled)
}

private fun syncStatusWithHealthScore(shareHealthScore: Boolean) {
    val familyId = state.value.familyId ?: return
    runFamilyAction {
        val mood = moodRepository.getLatestMoodForDate(DateUtils.todayString())
        val latestRisk = healthRepository.observeLatestRisk().first()
        val sharedScore = latestRisk?.totalScore?.takeIf { shareHealthScore }
        familyRepository.upsertStatus(
            familyId = familyId,
            snapshot =
                FamilyStatusSnapshot(
                    moodType = mood?.moodType,
                    moodNote = mood?.note,
                    statusLabel = if (mood == null) "No check-in yet" else "Checked in today",
                    updatedAt = System.currentTimeMillis(),
                    shareHealthScore = shareHealthScore,
                    healthScore = sharedScore,
                    healthScoreLabel = sharedScore?.toHealthScoreLabel(),
                    healthScoreUpdatedAt = System.currentTimeMillis().takeIf { sharedScore != null },
                ),
        )
    }
}

private fun Int.toHealthScoreLabel(): String =
    when {
        this >= 80 -> "Stable"
        this >= 60 -> "Watch today"
        else -> "Needs support"
    }
```

Add import:

```kotlin
import org.wit.vitasense.util.DateUtils
```

- [ ] **Step 7: Preserve existing sharing state during automatic mood sync**

In `syncTodayStatus(date: String)`, before building the snapshot, get the current member:

```kotlin
val currentUserId = authRepository.getCurrentUser()?.id
val currentMember = state.value.members.firstOrNull { it.userId == currentUserId }
val shareHealthScore = currentMember?.shareHealthScore ?: false
val latestRisk = healthRepository.observeLatestRisk().first()
val sharedScore = latestRisk?.totalScore?.takeIf { shareHealthScore }
```

Then extend the existing `FamilyStatusSnapshot`:

```kotlin
shareHealthScore = shareHealthScore,
healthScore = sharedScore,
healthScoreLabel = sharedScore?.toHealthScoreLabel(),
healthScoreUpdatedAt = System.currentTimeMillis().takeIf { sharedScore != null },
```

- [ ] **Step 8: Run Android compile**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt
git commit -m "feat: sync family health score preference"
```

---

### Task 4: Family Member Score UI

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt`
- Modify: `app/src/main/res/layout/item_family_member.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`

- [ ] **Step 1: Add failing mapper tests**

In `FamilyUiMapperTest.kt`, add:

```kotlin
@Test
fun member_card_shows_shared_health_score() {
    val state =
        FamilyUiMapper.build(
            currentUserId = 1,
            isSignedIn = true,
            family =
                family(
                    currentUserRole = FamilyRole.MEMBER,
                    members =
                        listOf(
                            member(
                                userId = 2,
                                shareHealthScore = true,
                                healthScore = 82,
                                healthScoreLabel = "Stable",
                                healthScoreUpdatedAt = 1770000000000,
                            ),
                        ),
                ),
            isLoading = false,
            errorMessage = null,
        )

    assertEquals("Health Score 82", state.members.single().healthScoreText)
    assertEquals("Stable", state.members.single().healthScoreDetailText)
    assertEquals(false, state.members.single().showShareHealthScoreSwitch)
}

@Test
fun current_user_card_exposes_health_score_share_switch() {
    val state =
        FamilyUiMapper.build(
            currentUserId = 1,
            isSignedIn = true,
            family =
                family(
                    currentUserRole = FamilyRole.MEMBER,
                    members = listOf(member(userId = 1, shareHealthScore = false)),
                ),
            isLoading = false,
            errorMessage = null,
        )

    assertEquals("Health score not shared", state.members.single().healthScoreText)
    assertEquals("", state.members.single().healthScoreDetailText)
    assertEquals(true, state.members.single().showShareHealthScoreSwitch)
    assertEquals(false, state.members.single().shareHealthScore)
}
```

Update the local test helper `member(...)` to accept:

```kotlin
shareHealthScore: Boolean = false,
healthScore: Int? = null,
healthScoreLabel: String? = null,
healthScoreUpdatedAt: Long? = null,
```

and pass those fields into `FamilyMember(...)`.

- [ ] **Step 2: Run compile and confirm failure**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: fails because `FamilyMemberUiModel` has no health score UI fields.

- [ ] **Step 3: Extend UI model**

In `FamilyUiModels.kt`, add fields to `FamilyMemberUiModel`:

```kotlin
val healthScoreText: String,
val healthScoreDetailText: String,
val showShareHealthScoreSwitch: Boolean,
```

- [ ] **Step 4: Map score UI state**

In `FamilyUiMapper.toUiModel()`, add:

```kotlin
val healthScoreText =
    when {
        !shareHealthScore -> "Health score not shared"
        healthScore != null -> "Health Score $healthScore"
        else -> "No score today"
    }
val healthScoreDetailText =
    when {
        !shareHealthScore -> ""
        healthScore != null -> healthScoreLabel.orEmpty()
        else -> ""
    }
```

Then pass into `FamilyMemberUiModel(...)`:

```kotlin
healthScoreText = healthScoreText,
healthScoreDetailText = healthScoreDetailText,
showShareHealthScoreSwitch = isSelf,
```

- [ ] **Step 5: Add XML controls**

In `item_family_member.xml`, add this block below the status/support summary texts and above support buttons:

```xml
<LinearLayout
    android:id="@+id/healthScoreRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="10dp"
    android:orientation="vertical">

    <TextView
        android:id="@+id/healthScoreText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="?android:attr/textColorPrimary"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/healthScoreDetailText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textColor="?android:attr/textColorSecondary" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/shareHealthScoreSwitch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/family_share_health_score" />
</LinearLayout>
```

In `strings.xml`, add:

```xml
<string name="family_share_health_score">Share health score with family</string>
```

- [ ] **Step 6: Bind adapter**

Change `FamilyMemberAdapter` constructor:

```kotlin
class FamilyMemberAdapter(
    private val onSupport: (Long, FamilySupportType) -> Unit,
    private val onRemove: (Long) -> Unit,
    private val onShareHealthScoreChanged: (Boolean) -> Unit,
) : RecyclerView.Adapter<FamilyMemberAdapter.ViewHolder>() {
```

In `bind(item)`, add:

```kotlin
binding.healthScoreText.text = item.healthScoreText
binding.healthScoreDetailText.text = item.healthScoreDetailText
binding.healthScoreDetailText.isVisible = item.healthScoreDetailText.isNotBlank()
binding.shareHealthScoreSwitch.isVisible = item.showShareHealthScoreSwitch
binding.shareHealthScoreSwitch.setOnCheckedChangeListener(null)
binding.shareHealthScoreSwitch.isChecked = item.shareHealthScore
binding.shareHealthScoreSwitch.setOnCheckedChangeListener { _, isChecked ->
    onShareHealthScoreChanged(isChecked)
}
```

- [ ] **Step 7: Wire Fragment callback**

In `FamilyFragment.kt`, update adapter creation:

```kotlin
adapter =
    FamilyMemberAdapter(
        onSupport = ::sendSupport,
        onRemove = ::removeMember,
        onShareHealthScoreChanged = viewModel::setShareHealthScore,
    )
```

- [ ] **Step 8: Run Android compile**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
./gradlew.bat --no-daemon :app:assembleDebug
```

Expected: both commands exit `0`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/family app/src/main/res/layout/item_family_member.xml app/src/main/res/values/strings.xml app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt
git commit -m "feat: show shared family health scores"
```

---

### Task 5: Full Verification And Deployment Notes

**Files:**
- Modify only files already touched if verification exposes issues.

- [ ] **Step 1: Run backend verification**

Run:

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/family_endpoints_test.py
cd python_auth_api
python family_endpoints_test.py
python sync_endpoints_test.py
python sync_schema_test.py
```

Expected: all commands exit `0`.

- [ ] **Step 2: Run Android compile/build verification**

Run:

```powershell
./gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
./gradlew.bat --no-daemon :app:assembleDebug
```

Expected: both commands exit `0`.

- [ ] **Step 3: Attempt targeted unit tests**

Run:

```powershell
./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "org.wit.vitasense.ui.family.FamilyViewModelTest" --tests "org.wit.vitasense.ui.family.FamilyUiMapperTest" --tests "org.wit.vitasense.data.repository.DefaultFamilyRepositoryTest" --tests "org.wit.vitasense.model.FamilyModelsTest"
```

Expected if local Gradle worker is fixed: tests pass.

If it fails with:

```text
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
```

record it as the known local Gradle Test Executor issue and rely on `compileDebugUnitTestKotlin` plus `assembleDebug`.

- [ ] **Step 4: Privacy scan**

Run:

```powershell
rg -n "rmssd|heart_rate|sleep_minutes|sleepDuration|anomaly_flags|risk_explanation|raw_samples" python_auth_api/main.py app/src/main/java/org/wit/vitasense/model/FamilyModels.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt
```

Expected:

- No Family response or Family payload code exposes detailed fields.
- `health_score` may appear.
- `total_score` should not appear in Family payloads; Android may read `totalScore` locally from risk records.

- [ ] **Step 5: Manual behavior check**

Use the app with two signed-in users in one family:

1. User A opens Family.
2. User A sees their own switch off.
3. User B sees User A as `Health score not shared`.
4. User A imports or has health data producing a score.
5. User A toggles `Share health score with family` on.
6. User B refreshes Family and sees `Health Score <score>` plus `Stable`, `Watch today`, or `Needs support`.
7. User B does not see HRV, heart rate, sleep duration, raw metrics, anomaly flags, or trend charts.
8. User A toggles sharing off.
9. User B refreshes Family and sees `Health score not shared`.

- [ ] **Step 6: Commit verification fixes if needed**

If verification found issues and fixes were made:

```powershell
git add <fixed files>
git commit -m "fix: stabilize family health score sharing"
```

If no fixes were needed, skip this commit.

- [ ] **Step 7: Server update note**

This feature changes `python_auth_api/main.py`, so production server update requires:

```bash
cd /opt/vitasense-auth-api
mkdir -p backup-$(date +%Y%m%d-%H%M%S)
cp -a main.py auth.db backup-$(date +%Y%m%d-%H%M%S)/
python -m py_compile main.py
systemctl restart vitasense-auth-api.service
curl -sS http://127.0.0.1:8001/api/v1/health
```

Do not restart unrelated services such as `/opt/smartgrid-web` or its gunicorn process.

---

## Self-Review

Spec coverage:

- Opt-in per user: Task 3 toggle and Task 4 current-user switch.
- Default off: Task 1 schema default, Task 2 model default, Task 4 UI default.
- Show score/label/update time: Task 1 backend fields, Task 2 model fields, Task 4 UI row.
- Hidden state: Task 1 opt-out serializer, Task 4 `Health score not shared`.
- No score state: Task 4 `No score today`.
- No detailed health metrics: Task 1 backend test, Task 2 payload test, Task 5 privacy scan.
- Server persistence: Task 1 status table columns.
- Android follows Family cache: Task 2 parsing and Task 4 mapper.

Placeholder scan:

- No `TBD`, `TODO`, or unspecified "write tests" steps remain.
- No placeholder or undefined deferred steps remain.
- The only conditional note is the known Gradle Test Executor failure mode, with exact expected text.

Type consistency:

- Wire fields use snake_case: `share_health_score`, `health_score`, `health_score_label`, `health_score_updated_at`.
- Kotlin fields use camelCase: `shareHealthScore`, `healthScore`, `healthScoreLabel`, `healthScoreUpdatedAt`.
- `FamilyStatusSnapshot` and `FamilyMember` use the same four score-sharing fields.
- UI model uses display-specific fields: `healthScoreText`, `healthScoreDetailText`, `shareHealthScore`, `showShareHealthScoreSwitch`.
