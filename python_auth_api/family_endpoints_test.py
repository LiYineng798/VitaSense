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
        assert all("email" not in item for item in body["family"]["members"])
        assert any(item["user_id"] == member["id"] and item["mood_type"] == "CALM" for item in body["family"]["members"])


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
        assert isinstance(member_card["latest_support_sent_at"], int)


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


def test_family_leave_member_and_owner_rules():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()
        client = TestClient(module.app)

        token_owner, _owner = register(client, "leave-owner")
        token_member, _member = register(client, "leave-member")

        created = client.post("/api/v1/families", json={"name": "Leave Team"}, headers=auth_headers(token_owner))
        assert created.status_code == 200, created.text
        family = created.json()["family"]
        joined = client.post("/api/v1/families/join", json={"invite_code": family["invite_code"]}, headers=auth_headers(token_member))
        assert joined.status_code == 200, joined.text

        member_leave = client.delete(
            f"/api/v1/families/{family['id']}/members/me",
            headers=auth_headers(token_member),
        )
        assert member_leave.status_code == 200, member_leave.text
        assert member_leave.json()["family"] is None

        owner_leave = client.delete(
            f"/api/v1/families/{family['id']}/members/me",
            headers=auth_headers(token_owner),
        )
        assert owner_leave.status_code == 400, owner_leave.text
        assert owner_leave.json()["code"] == "owner_cannot_leave"


if __name__ == "__main__":
    test_family_create_join_permissions_and_privacy()
    test_family_support_validation_and_dedupe()
    test_family_health_score_sharing_is_opt_in_and_hides_details()
    test_family_leave_member_and_owner_rules()
