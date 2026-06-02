import json
import time
import urllib.error
import urllib.request

BASE_URL = "http://127.0.0.1:8000"


def request_json(path: str, payload=None, token: str | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url=f"{BASE_URL}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST" if payload is not None else "GET",
    )
    if token:
        request.add_header("Authorization", f"Bearer {token}")

    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def expect_http_error(path: str, payload):
    request = urllib.request.Request(
        url=f"{BASE_URL}{path}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
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


if __name__ == "__main__":
    main()
