import importlib
import tempfile
import time
from pathlib import Path

from fastapi.testclient import TestClient


def unique_user(suffix: str) -> dict:
    return {
        "full_name": f"Sync User {suffix}",
        "email": f"sync-{suffix}-{time.time_ns()}@example.com",
        "username": f"sync-{suffix}-{time.time_ns()}",
        "password": "password123",
        "birth_date": "2000-01-02",
    }


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def register(client: TestClient, suffix: str) -> str:
    response = client.post("/api/v1/auth/register", json=unique_user(suffix))
    assert response.status_code == 200, response.text
    return response.json()["token"]


def main():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()
        client = TestClient(module.app)

        token_a = register(client, "a")
        token_b = register(client, "b")

        missing = client.get("/api/v1/sync/bootstrap")
        assert missing.status_code == 401, missing.text

        payload = {
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

        push = client.post("/api/v1/sync/push", json=payload, headers=auth_headers(token_a))
        assert push.status_code == 200, push.text
        assert push.json()["success"] is True

        stale_settings = {
            "settings": {"theme_mode": "light", "theme_family": "default", "updated_at": 1760000000000},
        }
        stale = client.post("/api/v1/sync/push", json=stale_settings, headers=auth_headers(token_a))
        assert stale.status_code == 200, stale.text

        mood_delete = {
            "mood_records": [
                {
                    "id": "mood-a-1",
                    "date": "2026-06-02",
                    "mood_type": "CALM",
                    "mood_group": "POSITIVE",
                    "note": "steady",
                    "created_at": 1770000000000,
                    "updated_at": 1770000000002,
                    "deleted_at": 1770000000002,
                }
            ]
        }
        delete = client.post("/api/v1/sync/push", json=mood_delete, headers=auth_headers(token_a))
        assert delete.status_code == 200, delete.text

        sleep_tie = {
            "sleep_records": [
                {
                    "id": "sleep-a-2",
                    "date": "2026-06-02",
                    "start_at": 1769971200000,
                    "end_at": 1770000000000,
                    "duration_minutes": 460,
                    "avg_heart_rate": 60.0,
                    "heart_rate_variability_hint": 40.0,
                    "source_batch_id": "demo",
                    "updated_at": 1770000000000,
                    "deleted_at": None,
                }
            ]
        }
        sleep = client.post("/api/v1/sync/push", json=sleep_tie, headers=auth_headers(token_a))
        assert sleep.status_code == 200, sleep.text

        bootstrap_a = client.get("/api/v1/sync/bootstrap", headers=auth_headers(token_a))
        assert bootstrap_a.status_code == 200, bootstrap_a.text
        body_a = bootstrap_a.json()
        assert body_a["settings"]["theme_mode"] == "dark"
        assert len(body_a["heart_rate_samples"]) == 1
        assert body_a["mood_records"][0]["deleted_at"] == 1770000000002
        assert body_a["sleep_records"][0]["id"] == "sleep-a-2"
        assert body_a["sleep_records"][0]["duration_minutes"] == 460

        bootstrap_b = client.get("/api/v1/sync/bootstrap", headers=auth_headers(token_b))
        assert bootstrap_b.status_code == 200, bootstrap_b.text
        body_b = bootstrap_b.json()
        assert body_b["settings"] is None
        assert body_b["mood_records"] == []
        assert body_b["heart_rate_samples"] == []
        assert body_b["sleep_records"] == []

        shared_ids = {
            "mood_records": [
                {
                    "id": "shared-mood-id",
                    "date": "2026-06-03",
                    "mood_type": "HAPPY",
                    "mood_group": "POSITIVE",
                    "note": "account scoped",
                    "created_at": 1770086400000,
                    "updated_at": 1770086400000,
                    "deleted_at": None,
                }
            ],
            "heart_rate_samples": [
                {
                    "id": "shared-hr-id",
                    "sample_timestamp": 1770086400000,
                    "date": "2026-06-03",
                    "heart_rate": 75,
                    "source_batch_id": "shared",
                    "updated_at": 1770086400000,
                }
            ],
            "sleep_records": [
                {
                    "id": "shared-sleep-id",
                    "date": "2026-06-03",
                    "start_at": 1770060000000,
                    "end_at": 1770085200000,
                    "duration_minutes": 420,
                    "avg_heart_rate": 62.0,
                    "heart_rate_variability_hint": 39.0,
                    "source_batch_id": "shared",
                    "updated_at": 1770086400000,
                    "deleted_at": None,
                }
            ],
        }

        shared_a = client.post("/api/v1/sync/push", json=shared_ids, headers=auth_headers(token_a))
        assert shared_a.status_code == 200, shared_a.text
        shared_b = client.post("/api/v1/sync/push", json=shared_ids, headers=auth_headers(token_b))
        assert shared_b.status_code == 200, shared_b.text

        scoped_a = client.get("/api/v1/sync/bootstrap", headers=auth_headers(token_a)).json()
        scoped_b = client.get("/api/v1/sync/bootstrap", headers=auth_headers(token_b)).json()
        assert any(record["id"] == "shared-mood-id" for record in scoped_a["mood_records"])
        assert any(record["id"] == "shared-mood-id" for record in scoped_b["mood_records"])
        assert any(record["id"] == "shared-hr-id" for record in scoped_a["heart_rate_samples"])
        assert any(record["id"] == "shared-hr-id" for record in scoped_b["heart_rate_samples"])
        assert any(record["id"] == "shared-sleep-id" for record in scoped_a["sleep_records"])
        assert any(record["id"] == "shared-sleep-id" for record in scoped_b["sleep_records"])


if __name__ == "__main__":
    main()
