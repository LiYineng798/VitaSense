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


def get_connection() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH)
    connection.execute("PRAGMA foreign_keys = ON")
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
                FOREIGN KEY(family_id, sender_user_id) REFERENCES family_members(family_id, user_id) ON DELETE CASCADE,
                FOREIGN KEY(family_id, receiver_user_id) REFERENCES family_members(family_id, user_id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_family_supports_receiver_date
            ON family_supports(family_id, receiver_user_id, support_date);

            CREATE TABLE IF NOT EXISTS user_settings (
                user_id INTEGER PRIMARY KEY,
                theme_mode TEXT NOT NULL,
                theme_family TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS cloud_mood_records (
                id TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                mood_type TEXT NOT NULL,
                mood_group TEXT NOT NULL,
                note TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                PRIMARY KEY(user_id, id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_cloud_mood_user_date
            ON cloud_mood_records(user_id, date);

            CREATE TABLE IF NOT EXISTS cloud_heart_rate_samples (
                id TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                sample_timestamp INTEGER NOT NULL,
                date TEXT NOT NULL,
                heart_rate INTEGER NOT NULL,
                source_batch_id TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_hr_unique
            ON cloud_heart_rate_samples(user_id, sample_timestamp, heart_rate, source_batch_id);

            CREATE TABLE IF NOT EXISTS cloud_sleep_records (
                id TEXT NOT NULL,
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
                PRIMARY KEY(user_id, id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_sleep_user_date
            ON cloud_sleep_records(user_id, date);
            """.strip(),
        )
        migrate_sync_tables(connection)


def table_exists(connection: sqlite3.Connection, table_name: str) -> bool:
    return connection.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
        (table_name,),
    ).fetchone() is not None


def has_global_id_primary_key(connection: sqlite3.Connection, table_name: str) -> bool:
    for column in connection.execute(f"PRAGMA table_info({table_name})").fetchall():
        if column["name"] == "id":
            return column["pk"] == 1
    return False


def migrate_sync_tables(connection: sqlite3.Connection) -> None:
    if table_exists(connection, "cloud_mood_records") and has_global_id_primary_key(connection, "cloud_mood_records"):
        rebuild_cloud_mood_records(connection)
    if table_exists(connection, "cloud_heart_rate_samples") and has_global_id_primary_key(connection, "cloud_heart_rate_samples"):
        rebuild_cloud_heart_rate_samples(connection)
    if table_exists(connection, "cloud_sleep_records") and has_global_id_primary_key(connection, "cloud_sleep_records"):
        rebuild_cloud_sleep_records(connection)


def rebuild_cloud_mood_records(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        ALTER TABLE cloud_mood_records RENAME TO cloud_mood_records_old;

        CREATE TABLE cloud_mood_records (
            id TEXT NOT NULL,
            user_id INTEGER NOT NULL,
            date TEXT NOT NULL,
            mood_type TEXT NOT NULL,
            mood_group TEXT NOT NULL,
            note TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            PRIMARY KEY(user_id, id),
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        INSERT OR REPLACE INTO cloud_mood_records(
            id, user_id, date, mood_type, mood_group, note, created_at, updated_at, deleted_at
        )
        SELECT id, user_id, date, mood_type, mood_group, note, created_at, updated_at, deleted_at
        FROM cloud_mood_records_old;

        DROP TABLE cloud_mood_records_old;

        CREATE INDEX IF NOT EXISTS idx_cloud_mood_user_date
        ON cloud_mood_records(user_id, date);
        """.strip(),
    )


def rebuild_cloud_heart_rate_samples(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        ALTER TABLE cloud_heart_rate_samples RENAME TO cloud_heart_rate_samples_old;

        CREATE TABLE cloud_heart_rate_samples (
            id TEXT NOT NULL,
            user_id INTEGER NOT NULL,
            sample_timestamp INTEGER NOT NULL,
            date TEXT NOT NULL,
            heart_rate INTEGER NOT NULL,
            source_batch_id TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(user_id, id),
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        INSERT OR IGNORE INTO cloud_heart_rate_samples(
            id, user_id, sample_timestamp, date, heart_rate, source_batch_id, updated_at
        )
        SELECT id, user_id, sample_timestamp, date, heart_rate, source_batch_id, updated_at
        FROM cloud_heart_rate_samples_old;

        DROP TABLE cloud_heart_rate_samples_old;

        CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_hr_unique
        ON cloud_heart_rate_samples(user_id, sample_timestamp, heart_rate, source_batch_id);
        """.strip(),
    )


def rebuild_cloud_sleep_records(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        ALTER TABLE cloud_sleep_records RENAME TO cloud_sleep_records_old;

        CREATE TABLE cloud_sleep_records (
            id TEXT NOT NULL,
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
            PRIMARY KEY(user_id, id),
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        INSERT OR REPLACE INTO cloud_sleep_records(
            id, user_id, date, start_at, end_at, duration_minutes,
            avg_heart_rate, heart_rate_variability_hint, source_batch_id, updated_at, deleted_at
        )
        SELECT id, user_id, date, start_at, end_at, duration_minutes,
               avg_heart_rate, heart_rate_variability_hint, source_batch_id, updated_at, deleted_at
        FROM cloud_sleep_records_old;

        DROP TABLE cloud_sleep_records_old;

        CREATE UNIQUE INDEX IF NOT EXISTS idx_cloud_sleep_user_date
        ON cloud_sleep_records(user_id, date);
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


def now_millis() -> int:
    return int(time.time() * 1000)


FAMILY_SUPPORT_TYPES = {
    "thinking_of_you",
    "need_anything",
    "take_a_pause",
    "proud_of_you",
}


def today_key(timestamp_millis: int | None = None) -> str:
    seconds = (now_millis() if timestamp_millis is None else timestamp_millis) / 1000
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


def create_session(connection: sqlite3.Connection, user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    connection.execute(
        "INSERT INTO sessions(token, user_id, created_at) VALUES (?, ?, ?)",
        (token, user_id, int(time.time())),
    )
    return token


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
            serialize_sync_row(
                row,
                [
                    "id",
                    "date",
                    "start_at",
                    "end_at",
                    "duration_minutes",
                    "avg_heart_rate",
                    "heart_rate_variability_hint",
                    "source_batch_id",
                    "updated_at",
                    "deleted_at",
                ],
            )
            for row in sleep_rows
        ],
    }


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
                    ON CONFLICT(user_id, id) DO UPDATE SET
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
                "SELECT updated_at, duration_minutes, source_batch_id FROM cloud_sleep_records WHERE user_id = ? AND date = ?",
                (user_id, sleep.date),
            ).fetchone()
            should_write = (
                existing is None
                or sleep.updated_at > existing["updated_at"]
                or (sleep.updated_at == existing["updated_at"] and sleep.source_batch_id != existing["source_batch_id"])
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
