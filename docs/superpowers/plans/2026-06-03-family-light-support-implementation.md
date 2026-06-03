# Family Light Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight Family feature where signed-in VitaSense users can create one family, join by invite code, view family daily mood/status cards, and send fixed support signals.

**Architecture:** Extend the existing FastAPI service with family tables and authenticated family endpoints. Android adds a server-backed Family repository, Family ViewModel/UI, a Profile entry point, and a Home summary card while keeping detailed health metrics private.

**Tech Stack:** Kotlin, XML Views, ViewBinding, Navigation Component, Coroutines/Flow, `HttpURLConnection`, FastAPI, SQLite, Python stdlib, existing JUnit and FastAPI `TestClient` test style.

---

## Scope Check

The feature touches two dependent subsystems:

- Python API owns family membership, invite codes, status snapshots, and support deduplication.
- Android client owns UI, local mood-to-family snapshot mapping, and navigation.

This is acceptable as one implementation plan because the Android feature cannot work without the API contract, and the API can be verified independently before Android integration.

---

## File Map

### Python API

- Modify `python_auth_api/main.py`: add Family request models, SQLite tables, serializers, invite-code helpers, auth-protected endpoints, and support dedupe logic.
- Create `python_auth_api/family_endpoints_test.py`: validate family creation, invite join, permissions, support enum validation, support dedupe, one-family rule, and privacy response shape.
- Modify `python_auth_api/README.md`: document Family endpoints.

### Android Models And Repository

- Create `app/src/main/java/org/wit/vitasense/model/FamilyModels.kt`: Family data models, result types, support enum, status snapshot, JSON parsing, and error messages.
- Create `app/src/main/java/org/wit/vitasense/repository/FamilyRepository.kt`: interface for family actions.
- Create `app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt`: `HttpURLConnection` client and JSON payload builder.
- Modify `app/src/main/java/org/wit/vitasense/AppContainer.kt`: construct `familyRepository`.
- Modify `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`: inject Family dependencies.

### Android Family UI

- Create `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`: screen state and member-card UI models.
- Create `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`: convert remote models and local mood records into screen state.
- Create `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`: state collection and actions.
- Create `app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt`: render signed-out, no-family, and joined-family states.
- Create `app/src/main/res/layout/fragment_family.xml`: Family screen layout.
- Create `app/src/main/res/layout/item_family_member.xml`: member card row/card layout.
- Create `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`: RecyclerView adapter for member cards.
- Modify `app/src/main/res/navigation/main_nav_graph.xml`: add `familyFragment`.
- Modify `app/src/main/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicy.kt`: keep Family as secondary page with hidden floating tabs.
- Modify `app/src/main/res/values/strings.xml`: add Family copy.

### Android Entry Points

- Modify `app/src/main/res/layout/fragment_profile.xml`: add Family entry card.
- Modify `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`: navigate to Family.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeModels.kt`: add Home Family summary state.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeUiMapper.kt`: map summary state.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt`: include family state.
- Modify `app/src/main/res/layout/fragment_dashboard.xml`: add compact Family summary card.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`: render card and navigate to Family.
- Modify `app/src/main/java/org/wit/vitasense/repository/MoodRepository.kt`: expose latest mood for a date so Family can build a lightweight status snapshot.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt`: implement latest mood lookup without adding a Family dependency.

### Tests

- Create `app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`.
- Modify `app/src/test/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicyTest.kt`.
- Modify or add dashboard/profile tests where existing fakes need `FamilyRepository`.

---

## API Contract

Family endpoints return the existing project style:

```json
{
  "success": true,
  "message": "Family created.",
  "family": {}
}
```

Errors return:

```json
{
  "success": false,
  "message": "Invalid invite code.",
  "code": "invalid_invite_code"
}
```

Family payload shape:

```json
{
  "id": 1,
  "name": "My Family",
  "invite_code": "A1B2C3",
  "current_user_role": "owner",
  "members": [
    {
      "user_id": 7,
      "full_name": "Ava Stone",
      "username": "ava",
      "role": "owner",
      "mood_type": "CALM",
      "mood_note": "steady",
      "status_label": "Checked in today",
      "status_updated_at": 1770000000000,
      "support_count_today": 1,
      "latest_support_type": "proud_of_you",
      "latest_support_sent_at": 1770000000500
    }
  ]
}
```

The Family response must not include `rmssd`, `heart_rate`, `sleep`, `sleep_minutes`, `total_score`, `risk`, `anomaly_flags`, or raw sample fields.

---

### Task 1: Backend Family Schema And Helpers

**Files:**
- Modify: `python_auth_api/main.py`
- Test: `python_auth_api/family_endpoints_test.py`

- [ ] **Step 1: Write failing backend schema/permission tests**

Create `python_auth_api/family_endpoints_test.py`:

```python
import importlib
import tempfile
import time
from pathlib import Path

from fastapi.testclient import TestClient


def unique_user(suffix: str) -> dict:
    stamp = time.time_ns()
    return {
        "full_name": f"Family User {suffix}",
        "email": f"family-{suffix}-{stamp}@example.com",
        "username": f"family-{suffix}-{stamp}",
        "password": "password123",
        "birth_date": "2000-01-02",
    }


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def register(client: TestClient, suffix: str) -> tuple[str, dict]:
    response = client.post("/api/v1/auth/register", json=unique_user(suffix))
    assert response.status_code == 200, response.text
    body = response.json()
    return body["token"], body["user"]


def test_family_create_join_permissions_and_privacy():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()
        client = TestClient(module.app)

        token_owner, owner = register(client, "owner")
        token_member, member = register(client, "member")
        token_outsider, outsider = register(client, "outsider")

        missing = client.get("/api/v1/families/me")
        assert missing.status_code == 401, missing.text

        create = client.post(
            "/api/v1/families",
            json={"name": "Stone Family"},
            headers=auth_headers(token_owner),
        )
        assert create.status_code == 200, create.text
        family = create.json()["family"]
        assert family["name"] == "Stone Family"
        assert family["current_user_role"] == "owner"
        invite_code = family["invite_code"]
        assert len(invite_code) == 6

        duplicate_create = client.post(
            "/api/v1/families",
            json={"name": "Second Family"},
            headers=auth_headers(token_owner),
        )
        assert duplicate_create.status_code == 409, duplicate_create.text
        assert duplicate_create.json()["code"] == "already_in_family"

        join = client.post(
            "/api/v1/families/join",
            json={"invite_code": invite_code.lower()},
            headers=auth_headers(token_member),
        )
        assert join.status_code == 200, join.text
        assert join.json()["family"]["current_user_role"] == "member"

        invalid_join = client.post(
            "/api/v1/families/join",
            json={"invite_code": "BAD999"},
            headers=auth_headers(token_outsider),
        )
        assert invalid_join.status_code == 404, invalid_join.text
        assert invalid_join.json()["code"] == "invalid_invite_code"

        member_remove = client.delete(
            f"/api/v1/families/{family['id']}/members/{owner['id']}",
            headers=auth_headers(token_member),
        )
        assert member_remove.status_code == 403, member_remove.text

        status = client.post(
            f"/api/v1/families/{family['id']}/status",
            json={
                "mood_type": "CALM",
                "mood_note": "steady",
                "status_label": "Checked in today",
                "updated_at": 1770000000000,
            },
            headers=auth_headers(token_member),
        )
        assert status.status_code == 200, status.text

        body = client.get("/api/v1/families/me", headers=auth_headers(token_owner)).json()
        raw = str(body).lower()
        assert "rmssd" not in raw
        assert "heart_rate" not in raw
        assert "sleep_minutes" not in raw
        assert any(item["user_id"] == member["id"] and item["mood_type"] == "CALM" for item in body["family"]["members"])
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```powershell
cd python_auth_api
python family_endpoints_test.py
```

Expected: fails with 404 for `/api/v1/families/me`.

- [ ] **Step 3: Add Family request models to `main.py`**

Add near existing Pydantic models:

```python
class FamilyCreateRequest(BaseModel):
    name: str


class FamilyJoinRequest(BaseModel):
    invite_code: str


class FamilyRenameRequest(BaseModel):
    name: str


class FamilyStatusRequest(BaseModel):
    mood_type: str | None = None
    mood_note: str | None = None
    status_label: str
    updated_at: int


class FamilySupportRequest(BaseModel):
    receiver_user_id: int
    support_type: str
```

- [ ] **Step 4: Add Family tables inside `initialize_database()`**

Add these SQL statements inside the existing `connection.executescript(...)` block after `sessions`:

```sql
CREATE TABLE IF NOT EXISTS families (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE,
    created_by_user_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(created_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS family_members (
    family_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL UNIQUE,
    role TEXT NOT NULL,
    joined_at INTEGER NOT NULL,
    PRIMARY KEY(family_id, user_id),
    FOREIGN KEY(family_id) REFERENCES families(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS family_status_snapshots (
    family_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    mood_type TEXT,
    mood_note TEXT,
    status_label TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(family_id, user_id),
    FOREIGN KEY(family_id, user_id) REFERENCES family_members(family_id, user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS family_supports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    family_id INTEGER NOT NULL,
    sender_user_id INTEGER NOT NULL,
    receiver_user_id INTEGER NOT NULL,
    support_type TEXT NOT NULL,
    support_date TEXT NOT NULL,
    sent_at INTEGER NOT NULL,
    UNIQUE(family_id, sender_user_id, receiver_user_id, support_type, support_date),
    FOREIGN KEY(family_id) REFERENCES families(id) ON DELETE CASCADE,
    FOREIGN KEY(sender_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY(receiver_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_family_supports_receiver_date
ON family_supports(family_id, receiver_user_id, support_date);
```

- [ ] **Step 5: Add helper constants and functions**

Add below `now_millis()` or near existing helpers:

```python
FAMILY_SUPPORT_TYPES = {
    "thinking_of_you",
    "need_anything",
    "take_a_pause",
    "proud_of_you",
}


def today_key(timestamp_millis: int | None = None) -> str:
    seconds = (timestamp_millis or now_millis()) / 1000
    return time.strftime("%Y-%m-%d", time.gmtime(seconds))


def generate_invite_code(connection: sqlite3.Connection) -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    for _ in range(20):
        code = "".join(secrets.choice(alphabet) for _ in range(6))
        existing = connection.execute("SELECT id FROM families WHERE invite_code = ?", (code,)).fetchone()
        if existing is None:
            return code
    raise RuntimeError("Unable to generate unique invite code.")


def get_family_membership(connection: sqlite3.Connection, user_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT families.id AS family_id, families.name, families.invite_code, family_members.role
        FROM family_members
        INNER JOIN families ON families.id = family_members.family_id
        WHERE family_members.user_id = ?
        LIMIT 1
        """.strip(),
        (user_id,),
    ).fetchone()


def require_family_user(authorization: str | None) -> int | JSONResponse:
    user_id = get_user_id_from_authorization(authorization)
    if user_id is None:
        return invalid_response(401, "Missing or invalid session token.")
    return user_id
```

- [ ] **Step 6: Run Python compile**

Run:

```powershell
python -m py_compile python_auth_api/main.py
```

Expected: no output and exit code `0`.

- [ ] **Step 7: Commit**

```powershell
git add python_auth_api/main.py python_auth_api/family_endpoints_test.py
git commit -m "feat: add family backend schema"
```

---

### Task 2: Backend Family Endpoints

**Files:**
- Modify: `python_auth_api/main.py`
- Modify: `python_auth_api/family_endpoints_test.py`
- Modify: `python_auth_api/README.md`

- [ ] **Step 1: Add support endpoint tests**

Append to `python_auth_api/family_endpoints_test.py`:

```python
def test_family_support_validation_and_dedupe():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()
        client = TestClient(module.app)

        token_owner, owner = register(client, "support-owner")
        token_member, member = register(client, "support-member")

        created = client.post("/api/v1/families", json={"name": "Care Team"}, headers=auth_headers(token_owner))
        assert created.status_code == 200, created.text
        family = created.json()["family"]
        joined = client.post("/api/v1/families/join", json={"invite_code": family["invite_code"]}, headers=auth_headers(token_member))
        assert joined.status_code == 200, joined.text

        invalid = client.post(
            f"/api/v1/families/{family['id']}/supports",
            json={"receiver_user_id": member["id"], "support_type": "custom_text"},
            headers=auth_headers(token_owner),
        )
        assert invalid.status_code == 400, invalid.text
        assert invalid.json()["code"] == "invalid_support_type"

        self_support = client.post(
            f"/api/v1/families/{family['id']}/supports",
            json={"receiver_user_id": owner["id"], "support_type": "proud_of_you"},
            headers=auth_headers(token_owner),
        )
        assert self_support.status_code == 400, self_support.text
        assert self_support.json()["code"] == "cannot_support_self"

        sent = client.post(
            f"/api/v1/families/{family['id']}/supports",
            json={"receiver_user_id": member["id"], "support_type": "proud_of_you"},
            headers=auth_headers(token_owner),
        )
        assert sent.status_code == 200, sent.text

        duplicate = client.post(
            f"/api/v1/families/{family['id']}/supports",
            json={"receiver_user_id": member["id"], "support_type": "proud_of_you"},
            headers=auth_headers(token_owner),
        )
        assert duplicate.status_code == 409, duplicate.text
        assert duplicate.json()["code"] == "duplicate_support"

        family_body = client.get("/api/v1/families/me", headers=auth_headers(token_member)).json()["family"]
        member_card = next(item for item in family_body["members"] if item["user_id"] == member["id"])
        assert member_card["support_count_today"] == 1
        assert member_card["latest_support_type"] == "proud_of_you"
```

- [ ] **Step 2: Add serializers to `main.py`**

Add below Family helpers:

```python
def serialize_family(connection: sqlite3.Connection, family_id: int, current_user_id: int) -> dict[str, Any]:
    family = connection.execute(
        "SELECT id, name, invite_code FROM families WHERE id = ?",
        (family_id,),
    ).fetchone()
    role_row = connection.execute(
        "SELECT role FROM family_members WHERE family_id = ? AND user_id = ?",
        (family_id, current_user_id),
    ).fetchone()
    members = connection.execute(
        """
        SELECT users.id AS user_id, users.full_name, users.username, family_members.role,
               family_status_snapshots.mood_type, family_status_snapshots.mood_note,
               family_status_snapshots.status_label, family_status_snapshots.updated_at AS status_updated_at
        FROM family_members
        INNER JOIN users ON users.id = family_members.user_id
        LEFT JOIN family_status_snapshots
          ON family_status_snapshots.family_id = family_members.family_id
         AND family_status_snapshots.user_id = family_members.user_id
        WHERE family_members.family_id = ?
        ORDER BY family_members.role ASC, users.full_name ASC
        """.strip(),
        (family_id,),
    ).fetchall()
    support_date = today_key()
    support_rows = connection.execute(
        """
        SELECT receiver_user_id, COUNT(*) AS support_count_today, MAX(sent_at) AS latest_support_sent_at
        FROM family_supports
        WHERE family_id = ? AND support_date = ?
        GROUP BY receiver_user_id
        """.strip(),
        (family_id, support_date),
    ).fetchall()
    support_by_receiver = {int(row["receiver_user_id"]): row for row in support_rows}
    latest_rows = connection.execute(
        """
        SELECT receiver_user_id, support_type, sent_at
        FROM family_supports
        WHERE family_id = ? AND support_date = ?
        ORDER BY sent_at DESC
        """.strip(),
        (family_id, support_date),
    ).fetchall()
    latest_by_receiver: dict[int, sqlite3.Row] = {}
    for row in latest_rows:
        latest_by_receiver.setdefault(int(row["receiver_user_id"]), row)
    return {
        "id": family["id"],
        "name": family["name"],
        "invite_code": family["invite_code"],
        "current_user_role": role_row["role"],
        "members": [
            {
                "user_id": row["user_id"],
                "full_name": row["full_name"],
                "username": row["username"],
                "role": row["role"],
                "mood_type": row["mood_type"],
                "mood_note": row["mood_note"],
                "status_label": row["status_label"] or "No check-in yet",
                "status_updated_at": row["status_updated_at"],
                "support_count_today": int(support_by_receiver.get(int(row["user_id"]), {"support_count_today": 0})["support_count_today"]) if int(row["user_id"]) in support_by_receiver else 0,
                "latest_support_type": latest_by_receiver.get(int(row["user_id"]))["support_type"] if int(row["user_id"]) in latest_by_receiver else None,
                "latest_support_sent_at": latest_by_receiver.get(int(row["user_id"]))["sent_at"] if int(row["user_id"]) in latest_by_receiver else None,
            }
            for row in members
        ],
    }


def family_response(connection: sqlite3.Connection, family_id: int, current_user_id: int, message: str) -> dict[str, Any]:
    return {
        "success": True,
        "message": message,
        "family": serialize_family(connection, family_id, current_user_id),
    }
```

- [ ] **Step 3: Add create, me, and join endpoints**

Add after auth/sync endpoints:

```python
@app.post("/api/v1/families")
def create_family(payload: FamilyCreateRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    name = payload.name.strip()
    if not name:
        return invalid_response(400, "Family name is required.")
    with get_connection() as connection:
        if get_family_membership(connection, user_id) is not None:
            return JSONResponse(status_code=409, content={"success": False, "code": "already_in_family", "message": "You already belong to a family."})
        now = now_millis()
        invite_code = generate_invite_code(connection)
        cursor = connection.execute(
            "INSERT INTO families(name, invite_code, created_by_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            (name, invite_code, user_id, now, now),
        )
        family_id = int(cursor.lastrowid)
        connection.execute(
            "INSERT INTO family_members(family_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
            (family_id, user_id, "owner", now),
        )
        connection.commit()
        return family_response(connection, family_id, user_id, "Family created.")


@app.get("/api/v1/families/me")
def get_my_family(authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    with get_connection() as connection:
        membership = get_family_membership(connection, user_id)
        if membership is None:
            return {"success": True, "message": "No family joined.", "family": None}
        return family_response(connection, int(membership["family_id"]), user_id, "Family resolved.")


@app.post("/api/v1/families/join")
def join_family(payload: FamilyJoinRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    code = payload.invite_code.strip().upper()
    if not code:
        return invalid_response(400, "Invite code is required.")
    with get_connection() as connection:
        if get_family_membership(connection, user_id) is not None:
            return JSONResponse(status_code=409, content={"success": False, "code": "already_in_family", "message": "You already belong to a family."})
        family = connection.execute("SELECT id FROM families WHERE invite_code = ?", (code,)).fetchone()
        if family is None:
            return JSONResponse(status_code=404, content={"success": False, "code": "invalid_invite_code", "message": "Invalid invite code."})
        now = now_millis()
        family_id = int(family["id"])
        connection.execute(
            "INSERT INTO family_members(family_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
            (family_id, user_id, "member", now),
        )
        connection.commit()
        return family_response(connection, family_id, user_id, "Joined family.")
```

- [ ] **Step 4: Add owner management, status, and support endpoints**

Add:

```python
def require_family_membership(connection: sqlite3.Connection, family_id: int, user_id: int) -> sqlite3.Row | JSONResponse:
    row = connection.execute(
        "SELECT role FROM family_members WHERE family_id = ? AND user_id = ?",
        (family_id, user_id),
    ).fetchone()
    if row is None:
        return JSONResponse(status_code=403, content={"success": False, "code": "not_family_member", "message": "You do not belong to this family."})
    return row


@app.patch("/api/v1/families/{family_id}")
def rename_family(family_id: int, payload: FamilyRenameRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    name = payload.name.strip()
    if not name:
        return invalid_response(400, "Family name is required.")
    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
        if membership["role"] != "owner":
            return JSONResponse(status_code=403, content={"success": False, "code": "permission_denied", "message": "Only the owner can manage this family."})
        connection.execute("UPDATE families SET name = ?, updated_at = ? WHERE id = ?", (name, now_millis(), family_id))
        connection.commit()
        return family_response(connection, family_id, user_id, "Family renamed.")


@app.post("/api/v1/families/{family_id}/invite-code/regenerate")
def regenerate_invite_code(family_id: int, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
        if membership["role"] != "owner":
            return JSONResponse(status_code=403, content={"success": False, "code": "permission_denied", "message": "Only the owner can manage this family."})
        connection.execute("UPDATE families SET invite_code = ?, updated_at = ? WHERE id = ?", (generate_invite_code(connection), now_millis(), family_id))
        connection.commit()
        return family_response(connection, family_id, user_id, "Invite code regenerated.")


@app.delete("/api/v1/families/{family_id}/members/{member_user_id}")
def remove_family_member(family_id: int, member_user_id: int, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
        if membership["role"] != "owner":
            return JSONResponse(status_code=403, content={"success": False, "code": "permission_denied", "message": "Only the owner can manage this family."})
        target = require_family_membership(connection, family_id, member_user_id)
        if isinstance(target, JSONResponse):
            return target
        if target["role"] == "owner":
            return JSONResponse(status_code=400, content={"success": False, "code": "cannot_remove_owner", "message": "Owner cannot be removed."})
        connection.execute("DELETE FROM family_members WHERE family_id = ? AND user_id = ?", (family_id, member_user_id))
        connection.commit()
        return family_response(connection, family_id, user_id, "Member removed.")


@app.delete("/api/v1/families/{family_id}/members/me")
def leave_family(family_id: int, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
        if membership["role"] == "owner":
            return JSONResponse(status_code=400, content={"success": False, "code": "owner_cannot_leave", "message": "Owner cannot leave in this version."})
        connection.execute("DELETE FROM family_members WHERE family_id = ? AND user_id = ?", (family_id, user_id))
        connection.commit()
        return {"success": True, "message": "Left family.", "family": None}


@app.post("/api/v1/families/{family_id}/status")
def upsert_family_status(family_id: int, payload: FamilyStatusRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id
    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
        connection.execute(
            """
            INSERT INTO family_status_snapshots(family_id, user_id, mood_type, mood_note, status_label, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(family_id, user_id) DO UPDATE SET
                mood_type = excluded.mood_type,
                mood_note = excluded.mood_note,
                status_label = excluded.status_label,
                updated_at = excluded.updated_at
            """.strip(),
            (family_id, user_id, payload.mood_type, payload.mood_note, payload.status_label.strip() or "Checked in today", payload.updated_at),
        )
        connection.commit()
        return family_response(connection, family_id, user_id, "Status updated.")


@app.post("/api/v1/families/{family_id}/supports")
def send_family_support(family_id: int, payload: FamilySupportRequest, authorization: str | None = Header(default=None)):
    sender_id = require_family_user(authorization)
    if isinstance(sender_id, JSONResponse):
        return sender_id
    if payload.support_type not in FAMILY_SUPPORT_TYPES:
        return JSONResponse(status_code=400, content={"success": False, "code": "invalid_support_type", "message": "Choose one of the fixed support options."})
    if sender_id == payload.receiver_user_id:
        return JSONResponse(status_code=400, content={"success": False, "code": "cannot_support_self", "message": "You cannot send support to yourself."})
    with get_connection() as connection:
        sender = require_family_membership(connection, family_id, sender_id)
        if isinstance(sender, JSONResponse):
            return sender
        receiver = require_family_membership(connection, family_id, payload.receiver_user_id)
        if isinstance(receiver, JSONResponse):
            return receiver
        now = now_millis()
        try:
            connection.execute(
                """
                INSERT INTO family_supports(family_id, sender_user_id, receiver_user_id, support_type, support_date, sent_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.strip(),
                (family_id, sender_id, payload.receiver_user_id, payload.support_type, today_key(now), now),
            )
            connection.commit()
        except sqlite3.IntegrityError:
            return JSONResponse(status_code=409, content={"success": False, "code": "duplicate_support", "message": "You already sent this support today."})
        return family_response(connection, family_id, sender_id, "Support sent.")
```

- [ ] **Step 5: Update README endpoint list**

Add to `python_auth_api/README.md`:

```markdown
## Family Endpoints

- `POST /api/v1/families`
- `GET /api/v1/families/me`
- `POST /api/v1/families/join`
- `PATCH /api/v1/families/{id}`
- `POST /api/v1/families/{id}/invite-code/regenerate`
- `DELETE /api/v1/families/{id}/members/{user_id}`
- `DELETE /api/v1/families/{id}/members/me`
- `POST /api/v1/families/{id}/status`
- `POST /api/v1/families/{id}/supports`
```

- [ ] **Step 6: Run backend tests**

Run:

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/family_endpoints_test.py
cd python_auth_api
python family_endpoints_test.py
```

Expected: all assertions pass.

- [ ] **Step 7: Commit**

```powershell
git add python_auth_api/main.py python_auth_api/family_endpoints_test.py python_auth_api/README.md
git commit -m "feat: add family backend endpoints"
```

---

### Task 3: Android Family Models And Repository

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/model/FamilyModels.kt`
- Create: `app/src/main/java/org/wit/vitasense/repository/FamilyRepository.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Test: `app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt`

- [ ] **Step 1: Write Family model tests**

Create `FamilyModelsTest.kt`:

```kotlin
package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyModelsTest {
    @Test
    fun parses_family_payload_without_health_metrics() {
        val raw =
            """
            {
              "id": 1,
              "name": "Stone Family",
              "invite_code": "A1B2C3",
              "current_user_role": "owner",
              "members": [
                {
                  "user_id": 7,
                  "full_name": "Ava Stone",
                  "username": "ava",
                  "role": "owner",
                  "mood_type": "CALM",
                  "mood_note": "steady",
                  "status_label": "Checked in today",
                  "status_updated_at": 1770000000000,
                  "support_count_today": 2,
                  "latest_support_type": "proud_of_you",
                  "latest_support_sent_at": 1770000000500
                }
              ]
            }
            """.trimIndent()

        val family = parseFamily(raw)

        assertEquals("Stone Family", family.name)
        assertEquals(FamilyRole.OWNER, family.currentUserRole)
        assertEquals(FamilySupportType.PROUD_OF_YOU, family.members.first().latestSupportType)
        assertFalse(raw.contains("rmssd"))
        assertFalse(raw.contains("heart_rate"))
    }

    @Test
    fun support_type_round_trip_uses_storage_key() {
        assertEquals("take_a_pause", FamilySupportType.TAKE_A_PAUSE.storageKey)
        assertEquals(FamilySupportType.NEED_ANYTHING, FamilySupportType.fromStorageKey("need_anything"))
        assertTrue(familyErrorMessage("invalid_invite_code").contains("Invalid"))
    }
}
```

- [ ] **Step 2: Run test and confirm compile failure**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.FamilyModelsTest"
```

Expected: compile failure because Family models do not exist.

- [ ] **Step 3: Create `FamilyModels.kt`**

```kotlin
package org.wit.vitasense.model

import org.json.JSONArray
import org.json.JSONObject

enum class FamilyRole(val storageKey: String) {
    OWNER("owner"),
    MEMBER("member"),
    ;

    companion object {
        fun fromStorageKey(raw: String): FamilyRole =
            entries.firstOrNull { it.storageKey == raw.lowercase() } ?: MEMBER
    }
}

enum class FamilySupportType(
    val storageKey: String,
    val displayName: String,
) {
    THINKING_OF_YOU("thinking_of_you", "Thinking of you"),
    NEED_ANYTHING("need_anything", "Need anything?"),
    TAKE_A_PAUSE("take_a_pause", "Take a pause"),
    PROUD_OF_YOU("proud_of_you", "Proud of you"),
    ;

    companion object {
        fun fromStorageKey(raw: String?): FamilySupportType? =
            entries.firstOrNull { it.storageKey == raw.orEmpty().lowercase() }
    }
}

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
)

data class Family(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val currentUserRole: FamilyRole,
    val members: List<FamilyMember>,
)

data class FamilyStatusSnapshot(
    val moodType: String?,
    val moodNote: String?,
    val statusLabel: String,
    val updatedAt: Long,
)

data class FamilyHomeSummary(
    val hasFamily: Boolean = false,
    val updatesToday: Int = 0,
    val supportReceivedToday: Int = 0,
)

sealed interface FamilyResult {
    data class Success(val family: Family?) : FamilyResult
    data class Error(val code: String, val message: String) : FamilyResult
}

fun parseFamily(raw: String): Family = parseFamily(JSONObject(raw))

fun parseFamily(obj: JSONObject): Family {
    val members = obj.optJSONArray("members") ?: JSONArray()
    return Family(
        id = obj.getLong("id"),
        name = obj.getString("name"),
        inviteCode = obj.optString("invite_code"),
        currentUserRole = FamilyRole.fromStorageKey(obj.optString("current_user_role")),
        members =
            (0 until members.length()).map { index ->
                val member = members.getJSONObject(index)
                FamilyMember(
                    userId = member.getLong("user_id"),
                    fullName = member.optString("full_name"),
                    username = member.optString("username"),
                    role = FamilyRole.fromStorageKey(member.optString("role")),
                    moodType = member.optString("mood_type").takeIf { it.isNotBlank() },
                    moodNote = member.optString("mood_note").takeIf { it.isNotBlank() },
                    statusLabel = member.optString("status_label").takeIf { it.isNotBlank() } ?: "No check-in yet",
                    statusUpdatedAt = member.optNullableLong("status_updated_at"),
                    supportCountToday = member.optInt("support_count_today", 0),
                    latestSupportType = FamilySupportType.fromStorageKey(member.optString("latest_support_type")),
                    latestSupportSentAt = member.optNullableLong("latest_support_sent_at"),
                )
            },
    )
}

fun parseFamilyEnvelope(raw: String): FamilyResult {
    val obj = JSONObject(raw.ifBlank { "{}" })
    if (!obj.optBoolean("success", false)) {
        return FamilyResult.Error(
            code = obj.optString("code", "unexpected_response"),
            message = obj.optString("message", "Unexpected server response."),
        )
    }
    val familyObj = obj.optJSONObject("family")
    return FamilyResult.Success(familyObj?.let(::parseFamily))
}

fun familyErrorMessage(code: String): String =
    when (code) {
        "already_in_family" -> "You already belong to a family."
        "invalid_invite_code" -> "Invalid invite code."
        "permission_denied" -> "Only the owner can manage this family."
        "cannot_support_self" -> "You cannot send support to yourself."
        "duplicate_support" -> "You already sent this support today."
        "invalid_support_type" -> "Choose one of the fixed support options."
        "missing_token" -> "Sign in to use Family."
        "network" -> "Unable to reach the server."
        else -> "Unable to update Family right now."
    }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (isNull(name)) null else optLong(name)
```

- [ ] **Step 4: Create `FamilyRepository.kt`**

```kotlin
package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType

interface FamilyRepository {
    fun observeCachedFamily(): Flow<Family?>

    suspend fun refreshFamily(): FamilyResult
    suspend fun createFamily(name: String): FamilyResult
    suspend fun joinFamily(inviteCode: String): FamilyResult
    suspend fun renameFamily(familyId: Long, name: String): FamilyResult
    suspend fun regenerateInviteCode(familyId: Long): FamilyResult
    suspend fun removeMember(familyId: Long, userId: Long): FamilyResult
    suspend fun leaveFamily(familyId: Long): FamilyResult
    suspend fun upsertStatus(familyId: Long, snapshot: FamilyStatusSnapshot): FamilyResult
    suspend fun sendSupport(familyId: Long, receiverUserId: Long, type: FamilySupportType): FamilyResult
}
```

- [ ] **Step 5: Write repository tests**

Create `DefaultFamilyRepositoryTest.kt` with a fake connection like `DefaultAuthRepositoryTest`:

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType

class DefaultFamilyRepositoryTest {
    @Test
    fun createFamilyPostsNameAndCachesFamily() = runBlocking {
        val requests = mutableListOf<FamilyNetworkRequest>()
        val repository = DefaultFamilyRepository(
            baseUrlProvider = { "https://server.np5.top" },
            tokenProvider = { "token-a" },
            request = { method, url, token, body ->
                requests += FamilyNetworkRequest(method, url, token, body)
                FamilyNetworkResponse(200, familyEnvelope())
            },
        )

        val result = repository.createFamily("Stone Family")

        assertTrue(result is FamilyResult.Success)
        assertEquals("POST", requests.single().method)
        assertEquals("https://server.np5.top/api/v1/families", requests.single().url)
        assertTrue(requests.single().body!!.contains("Stone Family"))
        assertEquals("Stone Family", repository.observeCachedFamily().first()!!.name)
    }

    @Test
    fun supportPayloadUsesFixedEnumStorageKey() = runBlocking {
        val requests = mutableListOf<FamilyNetworkRequest>()
        val repository = DefaultFamilyRepository(
            baseUrlProvider = { "https://server.np5.top" },
            tokenProvider = { "token-a" },
            request = { method, url, token, body ->
                requests += FamilyNetworkRequest(method, url, token, body)
                FamilyNetworkResponse(200, familyEnvelope())
            },
        )

        repository.sendSupport(1, 8, FamilySupportType.TAKE_A_PAUSE)

        assertTrue(requests.single().body!!.contains("take_a_pause"))
        assertTrue(!requests.single().body!!.contains("custom"))
    }

    @Test
    fun statusPayloadExcludesDetailedHealthMetrics() = runBlocking {
        val requests = mutableListOf<FamilyNetworkRequest>()
        val repository = DefaultFamilyRepository(
            baseUrlProvider = { "https://server.np5.top" },
            tokenProvider = { "token-a" },
            request = { method, url, token, body ->
                requests += FamilyNetworkRequest(method, url, token, body)
                FamilyNetworkResponse(200, familyEnvelope())
            },
        )

        repository.upsertStatus(
            1,
            FamilyStatusSnapshot("CALM", "steady", "Checked in today", 1770000000000),
        )

        val body = requests.single().body!!
        assertTrue(body.contains("mood_type"))
        assertTrue(!body.contains("rmssd"))
        assertTrue(!body.contains("heart_rate"))
        assertTrue(!body.contains("sleep_minutes"))
    }

    private fun familyEnvelope(): String =
        """
        {
          "success": true,
          "message": "ok",
          "family": {
            "id": 1,
            "name": "Stone Family",
            "invite_code": "A1B2C3",
            "current_user_role": "owner",
            "members": []
          }
        }
        """.trimIndent()
}
```

- [ ] **Step 6: Create `DefaultFamilyRepository.kt`**

```kotlin
package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.model.familyErrorMessage
import org.wit.vitasense.model.parseFamilyEnvelope
import org.wit.vitasense.repository.FamilyRepository

data class FamilyNetworkResponse(
    val statusCode: Int,
    val body: String,
)

data class FamilyNetworkRequest(
    val method: String,
    val url: String,
    val token: String,
    val body: String?,
)

class DefaultFamilyRepository(
    private val baseUrlProvider: suspend () -> String,
    private val tokenProvider: suspend () -> String,
    private val request: suspend (method: String, url: String, token: String, body: String?) -> FamilyNetworkResponse = ::defaultFamilyRequest,
) : FamilyRepository {
    private val cachedFamily = MutableStateFlow<Family?>(null)

    override fun observeCachedFamily(): Flow<Family?> = cachedFamily.asStateFlow()

    override suspend fun refreshFamily(): FamilyResult =
        execute("GET", "/api/v1/families/me")

    override suspend fun createFamily(name: String): FamilyResult =
        execute("POST", "/api/v1/families", JSONObject().put("name", name.trim()).toString())

    override suspend fun joinFamily(inviteCode: String): FamilyResult =
        execute("POST", "/api/v1/families/join", JSONObject().put("invite_code", inviteCode.trim()).toString())

    override suspend fun renameFamily(familyId: Long, name: String): FamilyResult =
        execute("PATCH", "/api/v1/families/$familyId", JSONObject().put("name", name.trim()).toString())

    override suspend fun regenerateInviteCode(familyId: Long): FamilyResult =
        execute("POST", "/api/v1/families/$familyId/invite-code/regenerate")

    override suspend fun removeMember(familyId: Long, userId: Long): FamilyResult =
        execute("DELETE", "/api/v1/families/$familyId/members/$userId")

    override suspend fun leaveFamily(familyId: Long): FamilyResult =
        execute("DELETE", "/api/v1/families/$familyId/members/me")

    override suspend fun upsertStatus(familyId: Long, snapshot: FamilyStatusSnapshot): FamilyResult =
        execute(
            "POST",
            "/api/v1/families/$familyId/status",
            JSONObject()
                .put("mood_type", snapshot.moodType)
                .put("mood_note", snapshot.moodNote)
                .put("status_label", snapshot.statusLabel)
                .put("updated_at", snapshot.updatedAt)
                .toString(),
        )

    override suspend fun sendSupport(
        familyId: Long,
        receiverUserId: Long,
        type: FamilySupportType,
    ): FamilyResult =
        execute(
            "POST",
            "/api/v1/families/$familyId/supports",
            JSONObject()
                .put("receiver_user_id", receiverUserId)
                .put("support_type", type.storageKey)
                .toString(),
        )

    private suspend fun execute(
        method: String,
        path: String,
        body: String? = null,
    ): FamilyResult {
        val token = tokenProvider().trim()
        if (token.isBlank()) return FamilyResult.Error("missing_token", familyErrorMessage("missing_token"))
        val baseUrl = baseUrlProvider().trim().removeSuffix("/")
        return try {
            val response = request(method, baseUrl + path, token, body)
            val result = parseFamilyEnvelope(response.body)
            if (result is FamilyResult.Success) {
                cachedFamily.value = result.family
            }
            result
        } catch (_: IOException) {
            FamilyResult.Error("network", familyErrorMessage("network"))
        } catch (_: SecurityException) {
            FamilyResult.Error("network", familyErrorMessage("network"))
        } catch (_: Exception) {
            FamilyResult.Error("unexpected_response", familyErrorMessage("unexpected_response"))
        }
    }
}

private suspend fun defaultFamilyRequest(
    method: String,
    url: String,
    token: String,
    body: String?,
): FamilyNetworkResponse =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            FamilyNetworkResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
```

- [ ] **Step 7: Add repository to `AppContainer`**

Modify `AppContainer.kt`:

```kotlin
import org.wit.vitasense.data.repository.DefaultFamilyRepository
import org.wit.vitasense.repository.FamilyRepository
```

Add:

```kotlin
val familyRepository: FamilyRepository by lazy {
    DefaultFamilyRepository(
        baseUrlProvider = { settingsRepository.getAuthBaseUrl().ifBlank { DEFAULT_AUTH_BASE_URL } },
        tokenProvider = { settingsRepository.getAuthToken() },
    )
}
```

- [ ] **Step 8: Run Android model/repository tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.FamilyModelsTest" --tests "org.wit.vitasense.data.repository.DefaultFamilyRepositoryTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/model/FamilyModels.kt app/src/main/java/org/wit/vitasense/repository/FamilyRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt app/src/main/java/org/wit/vitasense/AppContainer.kt app/src/test/java/org/wit/vitasense/model/FamilyModelsTest.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt
git commit -m "feat: add Android family repository"
```

---

### Task 4: Android Family ViewModel And UI Mapping

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`

- [ ] **Step 1: Write mapper tests**

Create `FamilyUiMapperTest.kt`:

```kotlin
package org.wit.vitasense.ui.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyMember
import org.wit.vitasense.model.FamilyRole
import org.wit.vitasense.model.FamilySupportType

class FamilyUiMapperTest {
    @Test
    fun ownerSeesInviteCodeSupportButtonsAndRemoveForOtherMember() {
        val state = FamilyUiMapper.build(
            currentUserId = 1,
            isSignedIn = true,
            family = family(FamilyRole.OWNER),
            isLoading = false,
            errorMessage = null,
        )

        assertEquals(FamilyScreenMode.JOINED_FAMILY, state.mode)
        assertTrue(state.canManageFamily)
        assertEquals("A1B2C3", state.inviteCode)
        val other = state.members.first { it.userId == 2L }
        assertTrue(other.canSendSupport)
        assertTrue(other.canRemove)
        assertEquals("Proud of you", other.latestSupportText)
    }

    @Test
    fun memberCannotManageOrRemove() {
        val state = FamilyUiMapper.build(
            currentUserId = 2,
            isSignedIn = true,
            family = family(FamilyRole.MEMBER),
            isLoading = false,
            errorMessage = null,
        )

        assertFalse(state.canManageFamily)
        assertTrue(state.canLeaveFamily)
        assertFalse(state.members.first { it.userId == 1L }.canRemove)
    }

    private fun family(role: FamilyRole): Family =
        Family(
            id = 9,
            name = "Stone Family",
            inviteCode = "A1B2C3",
            currentUserRole = role,
            members =
                listOf(
                    FamilyMember(1, "Ava Stone", "ava", FamilyRole.OWNER, "CALM", "steady", "Checked in today", 1770000000000, 0, null, null),
                    FamilyMember(2, "Ben Stone", "ben", FamilyRole.MEMBER, null, null, "No check-in yet", null, 1, FamilySupportType.PROUD_OF_YOU, 1770000000500),
                ),
        )
}
```

- [ ] **Step 2: Create UI models**

```kotlin
package org.wit.vitasense.ui.family

import org.wit.vitasense.model.FamilySupportType

enum class FamilyScreenMode {
    SIGNED_OUT,
    NO_FAMILY,
    JOINED_FAMILY,
}

data class FamilyScreenState(
    val mode: FamilyScreenMode = FamilyScreenMode.SIGNED_OUT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val familyId: Long? = null,
    val familyName: String = "",
    val inviteCode: String = "",
    val canManageFamily: Boolean = false,
    val canLeaveFamily: Boolean = false,
    val members: List<FamilyMemberUiModel> = emptyList(),
)

data class FamilyMemberUiModel(
    val userId: Long,
    val avatarInitial: String,
    val displayName: String,
    val roleLabel: String,
    val moodLabel: String,
    val moodNote: String,
    val statusLabel: String,
    val supportSummary: String,
    val latestSupportText: String,
    val canSendSupport: Boolean,
    val canRemove: Boolean,
    val supportTypes: List<FamilySupportType> = FamilySupportType.entries,
)
```

- [ ] **Step 3: Create mapper**

```kotlin
package org.wit.vitasense.ui.family

import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyRole

object FamilyUiMapper {
    fun build(
        currentUserId: Long?,
        isSignedIn: Boolean,
        family: Family?,
        isLoading: Boolean,
        errorMessage: String?,
    ): FamilyScreenState {
        if (!isSignedIn) {
            return FamilyScreenState(
                mode = FamilyScreenMode.SIGNED_OUT,
                isLoading = isLoading,
                errorMessage = errorMessage,
            )
        }
        if (family == null) {
            return FamilyScreenState(
                mode = FamilyScreenMode.NO_FAMILY,
                isLoading = isLoading,
                errorMessage = errorMessage,
            )
        }
        val canManage = family.currentUserRole == FamilyRole.OWNER
        return FamilyScreenState(
            mode = FamilyScreenMode.JOINED_FAMILY,
            isLoading = isLoading,
            errorMessage = errorMessage,
            familyId = family.id,
            familyName = family.name,
            inviteCode = family.inviteCode,
            canManageFamily = canManage,
            canLeaveFamily = family.currentUserRole == FamilyRole.MEMBER,
            members =
                family.members.map { member ->
                    val isSelf = member.userId == currentUserId
                    FamilyMemberUiModel(
                        userId = member.userId,
                        avatarInitial = member.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        displayName = member.fullName.ifBlank { member.username },
                        roleLabel = if (member.role == FamilyRole.OWNER) "Owner" else "Member",
                        moodLabel = member.moodType?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "No check-in yet",
                        moodNote = member.moodNote.orEmpty(),
                        statusLabel = member.statusLabel,
                        supportSummary =
                            when (member.supportCountToday) {
                                0 -> "No support yet today"
                                1 -> "1 support today"
                                else -> "${member.supportCountToday} supports today"
                            },
                        latestSupportText = member.latestSupportType?.displayName.orEmpty(),
                        canSendSupport = !isSelf,
                        canRemove = canManage && !isSelf && member.role != FamilyRole.OWNER,
                    )
                },
        )
    }
}
```

- [ ] **Step 4: Write ViewModel tests**

Create `FamilyViewModelTest.kt`:

```kotlin
package org.wit.vitasense.ui.family

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.FamilyRepository

class FamilyViewModelTest {
    @Test
    fun signedOutUserSeesSignedOutState() = runBlocking {
        val viewModel = FamilyViewModel(
            authRepository = FakeAuthRepository(null),
            familyRepository = FakeFamilyRepository(),
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        )
        yield()

        assertEquals(FamilyScreenMode.SIGNED_OUT, viewModel.state.value.mode)
    }

    @Test
    fun createFamilyRefreshesState() = runBlocking {
        val familyRepository = FakeFamilyRepository()
        val viewModel = FamilyViewModel(
            authRepository = FakeAuthRepository(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")),
            familyRepository = familyRepository,
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        )

        viewModel.createFamily("Stone Family")
        yield()

        assertEquals("Stone Family", viewModel.state.value.familyName)
        assertTrue(familyRepository.createCalls == 1)
    }
}
```

Add fake classes in the same file:

```kotlin
private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
    private val current = MutableStateFlow(user)
    override fun observeCurrentUser(): Flow<AuthUser?> = current
    override suspend fun getCurrentUser(): AuthUser? = current.value
    override suspend fun register(fullName: String, email: String, username: String, password: String, birthDate: String): AuthResult = AuthResult.Error("unused")
    override suspend fun login(identifier: String, password: String): AuthResult = AuthResult.Error("unused")
    override suspend fun logout() { current.value = null }
}

private class FakeFamilyRepository : FamilyRepository {
    private val family = MutableStateFlow<Family?>(null)
    var createCalls = 0
    override fun observeCachedFamily(): Flow<Family?> = family
    override suspend fun refreshFamily(): FamilyResult = FamilyResult.Success(family.value)
    override suspend fun createFamily(name: String): FamilyResult {
        createCalls++
        family.value = Family(1, name, "A1B2C3", org.wit.vitasense.model.FamilyRole.OWNER, emptyList())
        return FamilyResult.Success(family.value)
    }
    override suspend fun joinFamily(inviteCode: String): FamilyResult = FamilyResult.Success(family.value)
    override suspend fun renameFamily(familyId: Long, name: String): FamilyResult = createFamily(name)
    override suspend fun regenerateInviteCode(familyId: Long): FamilyResult = FamilyResult.Success(family.value)
    override suspend fun removeMember(familyId: Long, userId: Long): FamilyResult = FamilyResult.Success(family.value)
    override suspend fun leaveFamily(familyId: Long): FamilyResult { family.value = null; return FamilyResult.Success(null) }
    override suspend fun upsertStatus(familyId: Long, snapshot: org.wit.vitasense.model.FamilyStatusSnapshot): FamilyResult = FamilyResult.Success(family.value)
    override suspend fun sendSupport(familyId: Long, receiverUserId: Long, type: org.wit.vitasense.model.FamilySupportType): FamilyResult = FamilyResult.Success(family.value)
}
```

- [ ] **Step 5: Create `FamilyViewModel.kt`**

```kotlin
package org.wit.vitasense.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.model.familyErrorMessage
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.FamilyRepository

class FamilyViewModel(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val loading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<FamilyScreenState> =
        combine(
            authRepository.observeCurrentUser(),
            familyRepository.observeCachedFamily(),
            loading,
            errorMessage,
        ) { user, family, isLoading, error ->
            FamilyUiMapper.build(
                currentUserId = user?.id,
                isSignedIn = user != null,
                family = family,
                isLoading = isLoading,
                errorMessage = error,
            )
        }.stateIn(modelScope, SharingStarted.WhileSubscribed(5_000), FamilyScreenState())

    init {
        refresh()
    }

    fun refresh() {
        modelScope.launch {
            if (authRepository.getCurrentUser() == null) return@launch
            loading.value = true
            applyResult(familyRepository.refreshFamily())
            loading.value = false
        }
    }

    fun createFamily(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            errorMessage.value = "Family name is required."
            return
        }
        runFamilyAction { familyRepository.createFamily(trimmed) }
    }

    fun joinFamily(inviteCode: String) {
        val trimmed = inviteCode.trim()
        if (trimmed.isBlank()) {
            errorMessage.value = "Invite code is required."
            return
        }
        runFamilyAction { familyRepository.joinFamily(trimmed) }
    }

    fun renameFamily(name: String) {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.renameFamily(familyId, name) }
    }

    fun regenerateInviteCode() {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.regenerateInviteCode(familyId) }
    }

    fun removeMember(userId: Long) {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.removeMember(familyId, userId) }
    }

    fun leaveFamily() {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.leaveFamily(familyId) }
    }

    fun sendSupport(
        receiverUserId: Long,
        type: FamilySupportType,
    ) {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.sendSupport(familyId, receiverUserId, type) }
    }

    fun upsertStatus(snapshot: FamilyStatusSnapshot) {
        val familyId = state.value.familyId ?: return
        runFamilyAction { familyRepository.upsertStatus(familyId, snapshot) }
    }

    private fun runFamilyAction(block: suspend () -> FamilyResult) {
        modelScope.launch {
            loading.value = true
            applyResult(block())
            loading.value = false
        }
    }

    private fun applyResult(result: FamilyResult) {
        errorMessage.value =
            when (result) {
                is FamilyResult.Success -> null
                is FamilyResult.Error -> result.message.ifBlank { familyErrorMessage(result.code) }
            }
    }
}
```

- [ ] **Step 6: Register ViewModel in factory**

Modify `VitaSenseViewModelFactory.kt`:

```kotlin
import org.wit.vitasense.ui.family.FamilyViewModel
```

Add branch:

```kotlin
modelClass.isAssignableFrom(FamilyViewModel::class.java) ->
    FamilyViewModel(
        authRepository = appContainer.authRepository,
        familyRepository = appContainer.familyRepository,
    ) as T
```

- [ ] **Step 7: Run tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.family.FamilyUiMapperTest" --tests "org.wit.vitasense.ui.family.FamilyViewModelTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/ui/family app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt app/src/test/java/org/wit/vitasense/ui/family
git commit -m "feat: add family screen state"
```

---

### Task 5: Android Family Screen UI And Navigation

**Files:**
- Create: `app/src/main/res/layout/fragment_family.xml`
- Create: `app/src/main/res/layout/item_family_member.xml`
- Create: `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt`
- Modify: `app/src/main/res/navigation/main_nav_graph.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicy.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicyTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

Add:

```xml
<string name="family_title">Family</string>
<string name="family_signed_out_message">Sign in to create or join a family.</string>
<string name="family_create_title">Create a Family</string>
<string name="family_join_title">Join a Family</string>
<string name="family_name_hint">Family name</string>
<string name="family_invite_code_hint">Invite code</string>
<string name="family_create_action">Create Family</string>
<string name="family_join_action">Join</string>
<string name="family_regenerate_code">Regenerate Code</string>
<string name="family_leave">Leave Family</string>
<string name="family_remove">Remove</string>
<string name="family_no_support">No support yet today</string>
<string name="profile_family_button_label">Family</string>
<string name="dashboard_family_title">Family</string>
<string name="dashboard_family_summary_format">%1$d updates today / %2$d support received</string>
```

- [ ] **Step 2: Add navigation destination**

Modify `main_nav_graph.xml`:

```xml
<fragment
    android:id="@+id/familyFragment"
    android:name="org.wit.vitasense.ui.family.FamilyFragment"
    android:label="@string/family_title" />
```

- [ ] **Step 3: Update tab visibility test**

Add to `FloatingTabShellDestinationPolicyTest`:

```kotlin
assertFalse(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.familyFragment))
```

No implementation change is required if Family is not added to the top-level set.

- [ ] **Step 4: Create `fragment_family.xml`**

Use one `NestedScrollView` and separate sections that `FamilyFragment` toggles:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:windowBackground">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/familyBackButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/common_back" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/family_title"
            android:textColor="?android:attr/textColorPrimary"
            android:textSize="28sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/familyErrorText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="?attr/vsColorAlert"
            android:visibility="gone" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/signedOutSection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            app:cardBackgroundColor="?attr/colorSurface"
            app:cardCornerRadius="20dp"
            app:strokeColor="?attr/colorOutline"
            app:strokeWidth="1dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="18dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/family_signed_out_message"
                    android:textColor="?android:attr/textColorSecondary" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/signInButton"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/profile_sign_in" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout
            android:id="@+id/noFamilySection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardBackgroundColor="?attr/colorSurface"
                app:cardCornerRadius="20dp"
                app:strokeColor="?attr/colorOutline"
                app:strokeWidth="1dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="18dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/family_create_title"
                        android:textColor="?android:attr/textColorPrimary"
                        android:textStyle="bold" />

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:hint="@string/family_name_hint">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/familyNameInput"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/createFamilyButton"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="@string/family_create_action" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                app:cardBackgroundColor="?attr/colorSurface"
                app:cardCornerRadius="20dp"
                app:strokeColor="?attr/colorOutline"
                app:strokeWidth="1dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="18dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/family_join_title"
                        android:textColor="?android:attr/textColorPrimary"
                        android:textStyle="bold" />

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:hint="@string/family_invite_code_hint">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/inviteCodeInput"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="textCapCharacters" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/joinFamilyButton"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="@string/family_join_action" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/joinedFamilySection"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardBackgroundColor="?attr/colorSurface"
                app:cardCornerRadius="20dp"
                app:strokeColor="?attr/colorOutline"
                app:strokeWidth="1dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="18dp">

                    <TextView
                        android:id="@+id/familyNameText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textColor="?android:attr/textColorPrimary"
                        android:textSize="20sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/inviteCodeText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:textColor="?android:attr/textColorSecondary" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/regenerateCodeButton"
                        style="?attr/materialButtonOutlinedStyle"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:text="@string/family_regenerate_code" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/memberRecyclerView"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:nestedScrollingEnabled="false"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/leaveFamilyButton"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/family_leave" />
        </LinearLayout>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

- [ ] **Step 5: Create `item_family_member.xml`**

Create a `MaterialCardView` with IDs:

- `memberAvatarText`
- `memberNameText`
- `memberRoleText`
- `memberMoodText`
- `memberNoteText`
- `memberStatusText`
- `memberSupportSummaryText`
- `supportThinkingButton`
- `supportNeedAnythingButton`
- `supportTakePauseButton`
- `supportProudButton`
- `removeMemberButton`

Use `?attr/colorSurface`, `?attr/colorOutline`, and `?attr/vsColorPrimarySoft`.

- [ ] **Step 6: Create `FamilyMemberAdapter.kt`**

```kotlin
package org.wit.vitasense.ui.family

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.wit.vitasense.databinding.ItemFamilyMemberBinding
import org.wit.vitasense.model.FamilySupportType

class FamilyMemberAdapter(
    private val onSupport: (Long, FamilySupportType) -> Unit,
    private val onRemove: (Long) -> Unit,
) : RecyclerView.Adapter<FamilyMemberAdapter.ViewHolder>() {
    private val items = mutableListOf<FamilyMemberUiModel>()

    fun submitItems(next: List<FamilyMemberUiModel>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FamilyMemberUiModel) {
            binding.memberAvatarText.text = item.avatarInitial
            binding.memberNameText.text = item.displayName
            binding.memberRoleText.text = item.roleLabel
            binding.memberMoodText.text = item.moodLabel
            binding.memberNoteText.text = item.moodNote
            binding.memberNoteText.isVisible = item.moodNote.isNotBlank()
            binding.memberStatusText.text = item.statusLabel
            binding.memberSupportSummaryText.text = item.supportSummary
            binding.supportThinkingButton.isVisible = item.canSendSupport
            binding.supportNeedAnythingButton.isVisible = item.canSendSupport
            binding.supportTakePauseButton.isVisible = item.canSendSupport
            binding.supportProudButton.isVisible = item.canSendSupport
            binding.removeMemberButton.isVisible = item.canRemove
            binding.supportThinkingButton.setOnClickListener { onSupport(item.userId, FamilySupportType.THINKING_OF_YOU) }
            binding.supportNeedAnythingButton.setOnClickListener { onSupport(item.userId, FamilySupportType.NEED_ANYTHING) }
            binding.supportTakePauseButton.setOnClickListener { onSupport(item.userId, FamilySupportType.TAKE_A_PAUSE) }
            binding.supportProudButton.setOnClickListener { onSupport(item.userId, FamilySupportType.PROUD_OF_YOU) }
            binding.removeMemberButton.setOnClickListener { onRemove(item.userId) }
        }
    }
}
```

- [ ] **Step 7: Create `FamilyFragment.kt`**

```kotlin
package org.wit.vitasense.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentFamilyBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class FamilyFragment : Fragment() {
    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FamilyMemberAdapter

    private val viewModel: FamilyViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FamilyMemberAdapter(
            onSupport = viewModel::sendSupport,
            onRemove = viewModel::removeMember,
        )
        binding.familyBackButton.setOnClickListener { findNavController().popBackStack() }
        binding.signInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }
        binding.createFamilyButton.setOnClickListener {
            viewModel.createFamily(binding.familyNameInput.text?.toString().orEmpty())
        }
        binding.joinFamilyButton.setOnClickListener {
            viewModel.joinFamily(binding.inviteCodeInput.text?.toString().orEmpty())
        }
        binding.regenerateCodeButton.setOnClickListener { viewModel.regenerateInviteCode() }
        binding.leaveFamilyButton.setOnClickListener { viewModel.leaveFamily() }
        binding.memberRecyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: FamilyScreenState) {
        binding.familyErrorText.text = state.errorMessage.orEmpty()
        binding.familyErrorText.isVisible = state.errorMessage != null
        binding.signedOutSection.isVisible = state.mode == FamilyScreenMode.SIGNED_OUT
        binding.noFamilySection.isVisible = state.mode == FamilyScreenMode.NO_FAMILY
        binding.joinedFamilySection.isVisible = state.mode == FamilyScreenMode.JOINED_FAMILY
        binding.familyNameText.text = state.familyName
        binding.inviteCodeText.text = state.inviteCode
        binding.regenerateCodeButton.isVisible = state.canManageFamily
        binding.leaveFamilyButton.isVisible = state.canLeaveFamily
        adapter.submitItems(state.members)
    }

    override fun onDestroyView() {
        binding.memberRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
```

- [ ] **Step 8: Run compile and navigation test**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.navigation.FloatingTabShellDestinationPolicyTest"
./gradlew.bat :app:assembleDebug
```

Expected: test passes and debug APK builds.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/res/layout/fragment_family.xml app/src/main/res/layout/item_family_member.xml app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt app/src/main/res/navigation/main_nav_graph.xml app/src/main/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicy.kt app/src/test/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicyTest.kt app/src/main/res/values/strings.xml
git commit -m "feat: add family screen UI"
```

---

### Task 6: Profile And Home Entry Points

**Files:**
- Modify: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`
- Modify: `app/src/main/res/layout/fragment_dashboard.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeUiMapper.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Test: existing dashboard tests plus new mapper checks.

- [ ] **Step 1: Add Profile Family entry card**

Add a card in `fragment_profile.xml` near Appearance and Settings:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/familyEntryCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="?attr/vsColorPrimarySoft"
    app:cardCornerRadius="20dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="18dp"
        android:text="@string/profile_family_button_label"
        android:textColor="?android:attr/textColorPrimary"
        android:textSize="17sp"
        android:textStyle="bold" />
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Bind Profile navigation**

In `ProfileFragment.kt`:

```kotlin
binding.familyEntryCard.setOnClickListener {
    findNavController().navigate(R.id.familyFragment)
}
```

- [ ] **Step 3: Add Dashboard Family UI state**

In `DashboardHomeModels.kt`:

```kotlin
data class DashboardFamilyState(
    val visible: Boolean = false,
    val summaryText: String = "",
)
```

Add `val family: DashboardFamilyState = DashboardFamilyState()` to `DashboardScreenState`.

- [ ] **Step 4: Extend DashboardViewModel combine**

Inject `familyRepository: FamilyRepository` into `DashboardViewModel`.

Combine `familyRepository.observeCachedFamily()` into the state. Build summary:

```kotlin
private fun buildFamilyState(family: Family?): DashboardFamilyState {
    if (family == null) return DashboardFamilyState()
    val updates = family.members.count { it.statusUpdatedAt != null }
    val support = family.members.sumOf { it.supportCountToday }
    return DashboardFamilyState(
        visible = true,
        summaryText = "$updates updates today / $support support received",
    )
}
```

Pass this into `DashboardHomeUiMapper.build` or apply `.copy(family = buildFamilyState(family))` after existing mapping.

- [ ] **Step 5: Add Dashboard card layout**

In `fragment_dashboard.xml`, add a `MaterialCardView` with IDs:

- `familySummaryCard`
- `familySummaryText`

Use:

```xml
android:visibility="gone"
app:cardBackgroundColor="?attr/vsColorPrimarySoft"
app:cardCornerRadius="20dp"
```

- [ ] **Step 6: Bind Dashboard Family card**

In `DashboardFragment.kt` state collection:

```kotlin
binding.familySummaryCard.visibility = if (state.family.visible) View.VISIBLE else View.GONE
binding.familySummaryText.text = state.family.summaryText
binding.familySummaryCard.setOnClickListener {
    findNavController().navigate(R.id.familyFragment)
}
```

- [ ] **Step 7: Update factory**

Pass `appContainer.familyRepository` into `DashboardViewModel`.

- [ ] **Step 8: Run dashboard/profile compile tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.dashboard.DashboardViewModelTest" --tests "org.wit.vitasense.ui.profile.ProfileViewModelTest"
./gradlew.bat :app:assembleDebug
```

Expected: targeted tests pass and debug APK builds.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/res/layout/fragment_profile.xml app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt app/src/main/res/layout/fragment_dashboard.xml app/src/main/java/org/wit/vitasense/ui/dashboard app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt
git commit -m "feat: add family entry points"
```

---

### Task 7: Mood Status Snapshot Upload

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/repository/MoodRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`

- [ ] **Step 1: Add latest-mood API to `MoodRepository`**

Add:

```kotlin
suspend fun getLatestMoodForDate(date: String): MoodRecordEntity?
```

Add this method to `MoodRecordDao.kt`:

```kotlin
@Query("SELECT * FROM mood_records WHERE date = :date AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
suspend fun getLatestForDate(date: String): MoodRecordEntity?
```

- [ ] **Step 2: Inject `MoodRepository` into `FamilyViewModel`**

Update constructor:

```kotlin
private val moodRepository: MoodRepository,
```

- [ ] **Step 3: Add snapshot builder**

In `FamilyViewModel.kt`:

```kotlin
fun syncTodayStatus(date: String) {
    val familyId = state.value.familyId ?: return
    modelScope.launch {
        val mood = moodRepository.getLatestMoodForDate(date)
        val snapshot =
            FamilyStatusSnapshot(
                moodType = mood?.moodType,
                moodNote = mood?.note,
                statusLabel = if (mood == null) "No check-in yet" else "Checked in today",
                updatedAt = System.currentTimeMillis(),
            )
        applyResult(familyRepository.upsertStatus(familyId, snapshot))
    }
}
```

- [ ] **Step 4: Call snapshot sync when Family opens**

First add this method to `DateUtils.kt`:

```kotlin
fun todayString(): String = formatDate(System.currentTimeMillis())
```

Then in `FamilyFragment.onViewCreated`, after collecting a joined-family state, call:

```kotlin
viewModel.syncTodayStatus(DateUtils.todayString())
```

- [ ] **Step 5: Keep Mood save independent from Family**

Keep this minimal for first version: after saving mood, Mood continues writing local data and cloud sync. Family status refreshes the next time Home or Family opens. Do not introduce a circular dependency from MoodRepository to FamilyRepository in the first implementation.

- [ ] **Step 6: Update tests and fakes**

Update `FamilyViewModelTest` fake mood repository to return a current mood and assert `upsertStatus` is called with `CALM`.

- [ ] **Step 7: Run tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.family.FamilyViewModelTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/org/wit/vitasense/repository/MoodRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultMoodRepository.kt app/src/main/java/org/wit/vitasense/db/dao/MoodRecordDao.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt app/src/main/java/org/wit/vitasense/util/DateUtils.kt app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt
git commit -m "feat: sync family status from mood"
```

---

### Task 8: Full Verification

**Files:**
- Modify only files already touched if verification exposes issues.

- [ ] **Step 1: Run backend verification**

Run:

```powershell
python -m py_compile python_auth_api/main.py python_auth_api/family_endpoints_test.py
cd python_auth_api
python family_endpoints_test.py
python sync_endpoints_test.py
```

Expected: all commands pass.

- [ ] **Step 2: Run Android targeted tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.FamilyModelsTest" --tests "org.wit.vitasense.data.repository.DefaultFamilyRepositoryTest" --tests "org.wit.vitasense.ui.family.FamilyUiMapperTest" --tests "org.wit.vitasense.ui.family.FamilyViewModelTest" --tests "org.wit.vitasense.ui.navigation.FloatingTabShellDestinationPolicyTest"
```

Expected: PASS.

- [ ] **Step 3: Run broader Android verification**

Run:

```powershell
./gradlew.bat :app:assembleDebug
```

Expected: debug APK builds.

If `:app:testDebugUnitTest` still fails with `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`, record it as the pre-existing local Gradle test-worker issue and rely on targeted tests plus `assembleDebug`.

- [ ] **Step 4: Manual Android behavior check**

1. Launch app signed out.
2. Open Profile, tap Family.
3. Confirm signed-out state appears and Sign In opens Auth.
4. Sign in as user A.
5. Open Family, create `Stone Family`.
6. Confirm invite code appears and floating tabs are hidden.
7. Sign in as user B on another app install/device or by clearing app data.
8. Join using user A's invite code.
9. Save a Mood entry, open Family, confirm status card updates.
10. Send each fixed support signal to the other user.
11. Confirm duplicate same support type on same day is rejected.
12. Confirm Owner can remove Member.
13. Confirm Home shows Family summary only after joining a family.

- [ ] **Step 5: Privacy check**

Inspect Family network responses in backend tests or logs and confirm no fields contain:

```text
rmssd
heart_rate
sleep_minutes
total_score
risk_level
anomaly_flags
```

- [ ] **Step 6: Commit verification fixes**

If fixes were required:

```powershell
git status --short
git add <only files changed by family work>
git commit -m "fix: stabilize family light support"
```

Skip this commit if no fixes were needed.

---

## Self-Review

Spec coverage:

- Create family: Task 2 backend, Task 4/5 Android UI.
- Invite-code join: Task 2 backend, Task 4/5 Android UI.
- One-family rule: Task 1/2 backend tests and endpoint logic.
- Owner/Member roles: Task 2 backend, Task 4 mapper, Task 5 UI.
- Fixed support signals: Task 2 backend, Task 3 repository, Task 5 adapter.
- Duplicate support prevention: Task 2 backend tests.
- No custom support text: Task 2 support enum validation, Task 3 enum-only repository payload.
- Family Profile entry: Task 6.
- Home summary entry: Task 6.
- Hidden floating tabs on Family: Task 5 test.
- Lightweight status from Mood: Task 7.
- No detailed health metrics shared: Task 1/2 backend tests, Task 3 status payload test, Task 8 privacy check.
- No chat, push notifications, multiple families, invite links, email invites, AI advice, owner transfer, or deletion: excluded by absence from tasks and enforced by first-version endpoint set.

Placeholder scan:

- No placeholder or undefined deferred steps remain.
- The only deferred behavior is explicitly scoped: Mood save does not directly push family status; Family/Home refresh handles snapshot sync in this version.

Type consistency:

- `FamilyRole`, `FamilySupportType`, `Family`, `FamilyMember`, `FamilyResult`, and `FamilyStatusSnapshot` are introduced before repository and ViewModel tasks reference them.
- Endpoint storage keys match Android enum storage keys: `thinking_of_you`, `need_anything`, `take_a_pause`, `proud_of_you`.
- The plan keeps `FamilyRepository` independent from `CloudSyncRepository`, matching the approved design.

Risk notes:

- `fragment_family.xml` must include all IDs used by `FamilyFragment`; Step 4 lists the required IDs.
- Existing full unit test execution may still hit the pre-existing Gradle worker issue. Targeted tests and `assembleDebug` are the minimum verification gate unless the local Gradle environment is fixed.
