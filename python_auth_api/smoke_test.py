import json
import time
import urllib.error
import urllib.request

BASE_URL = "http://127.0.0.1:8000"


def request_json(path: str, payload=None, token: str | None = None, headers: dict[str, str] | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(
        url=f"{BASE_URL}{path}",
        data=data,
        headers=request_headers,
        method="POST" if payload is not None else "GET",
    )
    if token:
        request.add_header("Authorization", f"Bearer {token}")

    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def expect_http_error(path: str, payload=None, headers: dict[str, str] | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Content-Type": "application/json"}
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(
        url=f"{BASE_URL}{path}",
        data=data,
        headers=request_headers,
        method="POST" if payload is not None else "GET",
    )
    try:
        urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode("utf-8"))
    raise AssertionError("Expected HTTP error response")


def expect_ai_error(payload, expected_status: int, expected_code: str):
    status, body = expect_http_error("/api/v1/ai/advice", payload)
    assert status == expected_status, body
    assert body["success"] is False, body
    assert body["code"] == expected_code, body


def run_ai_validation_checks():
    base_payload = {
        "provider": "deepseek",
        "base_url": "https://api.deepseek.com",
        "model": "deepseek-chat",
        "api_key": "sk-test",
        "health_summary": {
            "date": "2026-06-02",
            "total_score": 82,
            "risk_level": "low",
            "sleep_minutes": 430,
            "rmssd": 35.2,
            "resting_heart_rate": 61.0,
            "avg_heart_rate": 65.0,
            "anomaly_flags": [],
            "rule_suggestion": "Keep the current pace.",
        },
    }
    missing_key = dict(base_payload)
    missing_key["api_key"] = ""
    expect_ai_error(missing_key, 400, "missing_api_key")

    missing_model = dict(base_payload)
    missing_model["model"] = ""
    expect_ai_error(missing_model, 400, "missing_model")

    missing_base_url = dict(base_payload)
    missing_base_url["base_url"] = ""
    expect_ai_error(missing_base_url, 400, "missing_base_url")


def run_sync_checks(token_a: str, token_b: str):
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
    missing_status, missing_body = expect_http_error("/api/v1/sync/bootstrap")
    assert missing_status == 401, missing_body

    push_status, push_body = request_json("/api/v1/sync/push", sync_payload, headers=auth_headers(token_a))
    assert push_status == 200, push_body
    assert push_body["success"] is True

    bootstrap_a_status, bootstrap_a = request_json("/api/v1/sync/bootstrap", headers=auth_headers(token_a))
    assert bootstrap_a_status == 200, bootstrap_a
    assert bootstrap_a["settings"]["theme_mode"] == "dark"
    assert len(bootstrap_a["heart_rate_samples"]) == 1

    bootstrap_b_status, bootstrap_b = request_json("/api/v1/sync/bootstrap", headers=auth_headers(token_b))
    assert bootstrap_b_status == 200, bootstrap_b
    assert bootstrap_b["mood_records"] == []


def main():
    suffix = str(time.time_ns())
    register_payload = {
        "full_name": "Ava Stone",
        "email": f"ava-{suffix}@example.com",
        "username": f"ava-{suffix}",
        "password": "password123",
        "birth_date": "2000-01-02",
    }
    login_payload = {
        "identifier": register_payload["username"],
        "password": "password123",
    }

    register_status, register_body = request_json("/api/v1/auth/register", register_payload)
    assert register_status == 200, register_body
    assert register_body["success"] is True
    token = register_body["token"]

    register_payload_b = {
        "full_name": "Ben Stone",
        "email": f"ben-{suffix}@example.com",
        "username": f"ben-{suffix}",
        "password": "password123",
        "birth_date": "2001-01-02",
    }
    register_b_status, register_b_body = request_json("/api/v1/auth/register", register_payload_b)
    assert register_b_status == 200, register_b_body
    token_b = register_b_body["token"]

    duplicate_status, duplicate_body = expect_http_error("/api/v1/auth/register", register_payload)
    assert duplicate_status == 409, duplicate_body

    login_status, login_body = request_json("/api/v1/auth/login", login_payload)
    assert login_status == 200, login_body
    assert login_body["success"] is True

    bad_login_status, bad_login_body = expect_http_error(
        "/api/v1/auth/login",
        {"identifier": register_payload["username"], "password": "wrong-password"},
    )
    assert bad_login_status == 401, bad_login_body

    me_status, me_body = request_json("/api/v1/auth/me", token=token)
    assert me_status == 200, me_body
    assert me_body["user"]["email"] == register_payload["email"], me_body

    run_ai_validation_checks()
    run_sync_checks(token, token_b)


if __name__ == "__main__":
    main()
