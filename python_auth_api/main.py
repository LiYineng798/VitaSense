from __future__ import annotations

import hashlib
import json
import secrets
import sqlite3
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel

APP_DIR = Path(__file__).resolve().parent
DB_PATH = APP_DIR / "auth.db"

app = FastAPI(title="VitaSense Auth API")


class RegisterRequest(BaseModel):
    full_name: str
    email: str
    username: str
    password: str
    birth_date: str


class LoginRequest(BaseModel):
    identifier: str
    password: str


class HealthSummaryPayload(BaseModel):
    date: str
    total_score: int | None = None
    risk_level: str | None = None
    sleep_minutes: int | None = None
    rmssd: float | None = None
    resting_heart_rate: float | None = None
    avg_heart_rate: float | None = None
    anomaly_flags: list[str] = []
    rule_suggestion: str | None = None


class AiAdviceRequest(BaseModel):
    provider: str
    base_url: str
    model: str
    api_key: str
    health_summary: HealthSummaryPayload


def get_connection() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def initialize_database() -> None:
    with get_connection() as connection:
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                birth_date TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """.strip(),
        )


def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


def normalize_email(email: str) -> str:
    return email.strip().lower()


def normalize_username(username: str) -> str:
    return username.strip().lower()


def serialize_user(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "full_name": row["full_name"],
        "email": row["email"],
        "username": row["username"],
        "birth_date": row["birth_date"],
    }


def create_session(connection: sqlite3.Connection, user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    connection.execute(
        "INSERT INTO sessions(token, user_id, created_at) VALUES (?, ?, ?)",
        (token, user_id, int(time.time())),
    )
    return token


def invalid_response(status_code: int, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "success": False,
            "message": message,
        },
    )


def ai_error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "success": False,
            "code": code,
            "message": message,
        },
    )


def validate_ai_payload(payload: AiAdviceRequest) -> JSONResponse | None:
    if not payload.api_key.strip():
        return ai_error(400, "missing_api_key", "Add an API key in Settings first.")
    if not payload.model.strip():
        return ai_error(400, "missing_model", "Add a model name in Settings first.")
    if not payload.base_url.strip():
        return ai_error(400, "missing_base_url", "Add an AI base URL in Settings first.")
    return None


def build_ai_messages(summary: HealthSummaryPayload) -> list[dict[str, str]]:
    system = (
        "You are a wellness support coach for VitaSense. Use only the provided metrics. "
        "Do not diagnose medical conditions. Give practical recovery, sleep, stress, hydration, "
        "and load-management suggestions. Return concise JSON only with keys summary, "
        "recommendations, risk_note, disclaimer."
    )
    user = json.dumps(summary.model_dump(), ensure_ascii=False)
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": f"Generate 2 to 4 practical suggestions from this data: {user}"},
    ]


def parse_advice_text(raw_text: str) -> dict[str, Any]:
    cleaned = raw_text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`")
        cleaned = cleaned.removeprefix("json").strip()
    parsed = json.loads(cleaned)
    recommendations = parsed.get("recommendations")
    if not isinstance(recommendations, list) or not recommendations:
        raise ValueError("missing recommendations")
    return {
        "summary": str(parsed.get("summary", "")).strip(),
        "recommendations": [str(item).strip() for item in recommendations if str(item).strip()],
        "risk_note": str(parsed.get("risk_note", "")).strip(),
        "disclaimer": str(parsed.get("disclaimer", "This is wellness support, not medical diagnosis.")).strip(),
    }


def call_openai_compatible(payload: AiAdviceRequest) -> dict[str, Any]:
    body = json.dumps(
        {
            "model": payload.model.strip(),
            "messages": build_ai_messages(payload.health_summary),
            "temperature": 0.4,
            "response_format": {"type": "json_object"},
        },
    ).encode("utf-8")
    request = urllib.request.Request(
        url=payload.base_url.strip().rstrip("/") + "/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {payload.api_key.strip()}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        provider_body = json.loads(response.read().decode("utf-8"))
    content = provider_body["choices"][0]["message"]["content"]
    return parse_advice_text(content)


def map_provider_error(exc: urllib.error.HTTPError) -> JSONResponse:
    if exc.code in (401, 403):
        return ai_error(401, "invalid_api_key", "The API key is invalid or expired.")
    if exc.code == 404:
        return ai_error(404, "model_unavailable", "The selected model is not available. Check the model name.")
    if exc.code in (402, 429):
        return ai_error(429, "quota_or_rate_limit", "The AI service quota or rate limit was reached.")
    return ai_error(502, "ai_network_error", "Unable to reach the AI service. Check your network or base URL.")


initialize_database()


@app.get("/api/v1/health")
def health():
    return {
        "success": True,
        "message": "ok",
    }


@app.post("/api/v1/auth/register")
def register(payload: RegisterRequest):
    email = normalize_email(payload.email)
    username = normalize_username(payload.username)

    if not payload.full_name.strip() or not email or not username or not payload.password or not payload.birth_date.strip():
        return invalid_response(400, "All fields are required.")

    with get_connection() as connection:
        existing_email = connection.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
        if existing_email is not None:
            return invalid_response(409, "Email is already registered.")

        existing_username = connection.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if existing_username is not None:
            return invalid_response(409, "Username is already taken.")

        cursor = connection.execute(
            """
            INSERT INTO users(full_name, email, username, password_hash, birth_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.strip(),
            (
                payload.full_name.strip(),
                email,
                username,
                hash_password(payload.password),
                payload.birth_date.strip(),
                int(time.time()),
            ),
        )
        user_id = int(cursor.lastrowid)
        user_row = connection.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        token = create_session(connection, user_id)
        connection.commit()

    return {
        "success": True,
        "message": "Registration successful.",
        "token": token,
        "user": serialize_user(user_row),
    }


@app.post("/api/v1/auth/login")
def login(payload: LoginRequest):
    identifier = payload.identifier.strip().lower()
    if not identifier or not payload.password:
        return invalid_response(400, "Identifier and password are required.")

    query = "SELECT * FROM users WHERE email = ? LIMIT 1" if "@" in identifier else "SELECT * FROM users WHERE username = ? LIMIT 1"

    with get_connection() as connection:
        user_row = connection.execute(query, (identifier,)).fetchone()
        if user_row is None or user_row["password_hash"] != hash_password(payload.password):
            return invalid_response(401, "Invalid credentials.")

        token = create_session(connection, int(user_row["id"]))
        connection.commit()

    return {
        "success": True,
        "message": "Login successful.",
        "token": token,
        "user": serialize_user(user_row),
    }


@app.get("/api/v1/auth/me")
def me(authorization: str | None = Header(default=None)):
    if authorization is None or not authorization.startswith("Bearer "):
        return invalid_response(401, "Missing bearer token.")

    token = authorization.removeprefix("Bearer ").strip()
    with get_connection() as connection:
        session_row = connection.execute(
            """
            SELECT users.*
            FROM sessions
            INNER JOIN users ON users.id = sessions.user_id
            WHERE sessions.token = ?
            LIMIT 1
            """.strip(),
            (token,),
        ).fetchone()

    if session_row is None:
        return invalid_response(401, "Invalid session token.")

    return {
        "success": True,
        "message": "Current user resolved.",
        "user": serialize_user(session_row),
    }


@app.post("/api/v1/ai/advice")
def ai_advice(payload: AiAdviceRequest):
    validation_error = validate_ai_payload(payload)
    if validation_error is not None:
        return validation_error

    provider = payload.provider.strip().lower()
    if provider not in {"deepseek", "openai_compatible"}:
        return ai_error(400, "unsupported_provider", "The selected AI provider is not supported.")

    try:
        advice = call_openai_compatible(payload)
    except urllib.error.HTTPError as exc:
        return map_provider_error(exc)
    except (urllib.error.URLError, TimeoutError):
        return ai_error(502, "ai_network_error", "Unable to reach the AI service. Check your network or base URL.")
    except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
        return ai_error(502, "unexpected_ai_response", "The AI service returned an unexpected response.")

    return {
        "success": True,
        "advice": advice,
    }
