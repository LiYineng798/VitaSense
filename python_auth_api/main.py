from __future__ import annotations

import hashlib
import json
import secrets
import sqlite3
import time
import urllib.error
import urllib.request
from collections.abc import Iterator
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Header
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel

APP_DIR = Path(__file__).resolve().parent
DB_PATH = APP_DIR / "auth.db"

app = FastAPI(title="VitaSense Auth API")
urlopen_for_ai = urllib.request.urlopen


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
    share_health_score: bool = False
    health_score: int | None = None
    health_score_label: str | None = None
    health_score_updated_at: int | None = None


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


class AiChatMessagePayload(BaseModel):
    role: str
    content: str


class AiChatRequest(BaseModel):
    provider: str
    base_url: str
    model: str
    api_key: str
    messages: list[AiChatMessagePayload]
    health_context: dict[str, Any] = {}


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
                share_health_score INTEGER NOT NULL DEFAULT 0,
                health_score INTEGER,
                health_score_label TEXT,
                health_score_updated_at INTEGER,
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
        ensure_family_status_score_columns(connection)
        migrate_family_supports_table(connection)
        migrate_sync_tables(connection)


def table_exists(connection: sqlite3.Connection, table_name: str) -> bool:
    return connection.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
        (table_name,),
    ).fetchone() is not None


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


def family_supports_has_composite_member_foreign_keys(connection: sqlite3.Connection) -> bool:
    if not table_exists(connection, "family_supports"):
        return True

    foreign_key_groups: dict[int, list[sqlite3.Row]] = {}
    for row in connection.execute("PRAGMA foreign_key_list(family_supports)").fetchall():
        foreign_key_groups.setdefault(row["id"], []).append(row)

    required_mappings = [
        {"family_id": "family_id", "sender_user_id": "user_id"},
        {"family_id": "family_id", "receiver_user_id": "user_id"},
    ]
    actual_mappings: list[dict[str, str]] = []
    for rows in foreign_key_groups.values():
        if rows[0]["table"] != "family_members":
            continue
        actual_mappings.append({row["from"]: row["to"] for row in rows})

    return all(mapping in actual_mappings for mapping in required_mappings)


def migrate_family_supports_table(connection: sqlite3.Connection) -> None:
    if not family_supports_has_composite_member_foreign_keys(connection):
        rebuild_family_supports(connection)


def rebuild_family_supports(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        ALTER TABLE family_supports RENAME TO family_supports_old;

        CREATE TABLE family_supports (
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

        INSERT OR IGNORE INTO family_supports(
            id, family_id, sender_user_id, receiver_user_id, support_type, support_date, sent_at
        )
        SELECT
            family_supports_old.id,
            family_supports_old.family_id,
            family_supports_old.sender_user_id,
            family_supports_old.receiver_user_id,
            family_supports_old.support_type,
            family_supports_old.support_date,
            family_supports_old.sent_at
        FROM family_supports_old
        INNER JOIN families
            ON families.id = family_supports_old.family_id
        INNER JOIN family_members AS sender
            ON sender.family_id = family_supports_old.family_id
            AND sender.user_id = family_supports_old.sender_user_id
        INNER JOIN family_members AS receiver
            ON receiver.family_id = family_supports_old.family_id
            AND receiver.user_id = family_supports_old.receiver_user_id;

        DROP TABLE family_supports_old;

        CREATE INDEX IF NOT EXISTS idx_family_supports_receiver_date
        ON family_supports(family_id, receiver_user_id, support_date);
        """.strip(),
    )


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


def family_error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "success": False,
            "code": code,
            "message": message,
        },
    )


def get_family_membership_for_family(connection: sqlite3.Connection, family_id: int, user_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT family_members.family_id, family_members.user_id, family_members.role
        FROM family_members
        WHERE family_id = ? AND user_id = ?
        LIMIT 1
        """.strip(),
        (family_id, user_id),
    ).fetchone()


def require_family_membership(
    connection: sqlite3.Connection,
    family_id: int,
    user_id: int,
) -> sqlite3.Row | JSONResponse:
    membership = get_family_membership_for_family(connection, family_id, user_id)
    if membership is None:
        return family_error(404, "family_not_found", "Family not found.")
    return membership


def require_family_owner(
    connection: sqlite3.Connection,
    family_id: int,
    user_id: int,
) -> sqlite3.Row | JSONResponse:
    membership = require_family_membership(connection, family_id, user_id)
    if isinstance(membership, JSONResponse):
        return membership
    if membership["role"] != "owner":
        return family_error(403, "permission_denied", "Only the family owner can perform this action.")
    return membership


def serialize_family(connection: sqlite3.Connection, family_id: int, current_user_id: int) -> dict[str, Any]:
    family = connection.execute(
        """
        SELECT id, name, invite_code, created_by_user_id, created_at, updated_at
        FROM families
        WHERE id = ?
        LIMIT 1
        """.strip(),
        (family_id,),
    ).fetchone()
    if family is None:
        raise ValueError("Family not found.")

    current_membership = get_family_membership_for_family(connection, family_id, current_user_id)
    if current_membership is None:
        raise ValueError("Current user is not a family member.")

    support_date = today_key()
    rows = connection.execute(
        """
        SELECT
            family_members.user_id,
            family_members.role,
            family_members.joined_at,
            users.full_name,
            users.username,
            family_status_snapshots.mood_type,
            family_status_snapshots.mood_note,
            family_status_snapshots.status_label,
            family_status_snapshots.updated_at AS status_updated_at,
            family_status_snapshots.share_health_score,
            family_status_snapshots.health_score,
            family_status_snapshots.health_score_label,
            family_status_snapshots.health_score_updated_at,
            (
                SELECT COUNT(*)
                FROM family_supports
                WHERE family_supports.family_id = family_members.family_id
                    AND family_supports.receiver_user_id = family_members.user_id
                    AND family_supports.support_date = ?
            ) AS support_count_today,
            (
                SELECT family_supports.support_type
                FROM family_supports
                WHERE family_supports.family_id = family_members.family_id
                    AND family_supports.receiver_user_id = family_members.user_id
                    AND family_supports.support_date = ?
                ORDER BY family_supports.sent_at DESC, family_supports.id DESC
                LIMIT 1
            ) AS latest_support_type,
            (
                SELECT family_supports.sent_at
                FROM family_supports
                WHERE family_supports.family_id = family_members.family_id
                    AND family_supports.receiver_user_id = family_members.user_id
                    AND family_supports.support_date = ?
                ORDER BY family_supports.sent_at DESC, family_supports.id DESC
                LIMIT 1
            ) AS latest_support_sent_at
        FROM family_members
        INNER JOIN users ON users.id = family_members.user_id
        LEFT JOIN family_status_snapshots
            ON family_status_snapshots.family_id = family_members.family_id
            AND family_status_snapshots.user_id = family_members.user_id
        WHERE family_members.family_id = ?
        ORDER BY family_members.role = 'owner' DESC, family_members.joined_at ASC, users.full_name ASC
        """.strip(),
        (support_date, support_date, support_date, family_id),
    ).fetchall()

    members = []
    for row in rows:
        share_health_score = bool(row["share_health_score"])
        members.append(
            {
            "user_id": row["user_id"],
            "full_name": row["full_name"],
            "username": row["username"],
            "role": row["role"],
            "joined_at": row["joined_at"],
            "mood_type": row["mood_type"],
            "mood_note": row["mood_note"],
            "status_label": row["status_label"],
            "status_updated_at": row["status_updated_at"],
            "support_count_today": row["support_count_today"],
            "latest_support_type": row["latest_support_type"],
            "latest_support_sent_at": row["latest_support_sent_at"],
            "share_health_score": share_health_score,
            "health_score": row["health_score"] if share_health_score else None,
            "health_score_label": row["health_score_label"] if share_health_score else None,
            "health_score_updated_at": row["health_score_updated_at"] if share_health_score else None,
            },
        )

    return {
        "id": family["id"],
        "name": family["name"],
        "invite_code": family["invite_code"],
        "created_by_user_id": family["created_by_user_id"],
        "created_at": family["created_at"],
        "updated_at": family["updated_at"],
        "current_user_role": current_membership["role"],
        "members": members,
    }


def family_response(connection: sqlite3.Connection, family_id: int, current_user_id: int, message: str) -> dict[str, Any]:
    return {
        "success": True,
        "message": message,
        "family": serialize_family(connection, family_id, current_user_id),
    }


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


def validate_ai_chat_payload(payload: AiChatRequest) -> JSONResponse | None:
    if not payload.api_key.strip():
        return ai_error(400, "missing_api_key", "Add an API key in Settings first.")
    if not payload.model.strip():
        return ai_error(400, "missing_model", "Add a model name in Settings first.")
    if not payload.base_url.strip():
        return ai_error(400, "missing_base_url", "Add an AI base URL in Settings first.")
    if payload.provider.strip().lower() not in {"deepseek", "openai_compatible"}:
        return ai_error(400, "unsupported_provider", "The selected AI provider is not supported.")
    if not any(message.role.strip().lower() == "user" and message.content.strip() for message in payload.messages):
        return ai_error(400, "empty_message", "Enter a message before sending.")
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


def build_chat_system_message(health_context: dict[str, Any]) -> str:
    return (
        "You are VitaSense AI Chat. Help the user reflect on their health trends, mood, "
        "stress, and recovery state using the provided VitaSense context. This is health "
        "support and state review, not a medical diagnosis. Encourage practical, low-risk "
        "next steps. For urgent, severe, or dangerous symptoms, recommend professional or "
        "emergency help.\n\n"
        f"Recent VitaSense context JSON: {json.dumps(health_context, ensure_ascii=False)}"
    )


def provider_chat_messages(payload: AiChatRequest) -> list[dict[str, str]]:
    messages = [{"role": "system", "content": build_chat_system_message(payload.health_context)}]
    for message in payload.messages[-20:]:
        role = message.role.strip().lower()
        content = message.content.strip()
        if role in {"user", "assistant"} and content:
            messages.append({"role": role, "content": content})
    return messages


def chat_completion_url(base_url: str) -> str:
    return base_url.strip().rstrip("/") + "/chat/completions"


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


def stream_chat_completion(payload: AiChatRequest) -> Iterator[str]:
    body = json.dumps(
        {
            "model": payload.model.strip(),
            "messages": provider_chat_messages(payload),
            "stream": True,
        },
    ).encode("utf-8")
    request = urllib.request.Request(
        url=chat_completion_url(payload.base_url),
        data=body,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "Authorization": f"Bearer {payload.api_key.strip()}",
        },
        method="POST",
    )
    try:
        with urlopen_for_ai(request, timeout=45) as response:
            for raw_line in response:
                line = raw_line.decode("utf-8").strip()
                if not line.startswith("data:"):
                    continue
                data = line.removeprefix("data:").strip()
                if data == "[DONE]":
                    yield "data: " + json.dumps({"done": True}) + "\n\n"
                    return
                try:
                    provider_event = json.loads(data)
                    delta = provider_event.get("choices", [{}])[0].get("delta", {}).get("content", "")
                except (json.JSONDecodeError, KeyError, IndexError, TypeError):
                    delta = ""
                if delta:
                    yield "data: " + json.dumps({"delta": delta}) + "\n\n"
            yield "data: " + json.dumps({"done": True}) + "\n\n"
    except urllib.error.HTTPError as exc:
        error_response = map_provider_error(exc)
        error_body = json.loads(error_response.body.decode("utf-8"))
        yield "data: " + json.dumps(
            {
                "error": {
                    "code": error_body.get("code", "unexpected_ai_response"),
                    "message": error_body.get("message", "The AI service returned an unexpected response."),
                },
            },
        ) + "\n\n"
    except (urllib.error.URLError, TimeoutError):
        yield "data: " + json.dumps(
            {
                "error": {
                    "code": "ai_network_error",
                    "message": "Unable to reach the AI service. Check your network or base URL.",
                },
            },
        ) + "\n\n"


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


@app.post("/api/v1/families")
def create_family(payload: FamilyCreateRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    name = payload.name.strip()
    if not name:
        return family_error(400, "invalid_family_name", "Family name is required.")

    with get_connection() as connection:
        if get_family_membership(connection, user_id) is not None:
            return family_error(409, "already_in_family", "User already belongs to a family.")

        timestamp = now_millis()
        invite_code = generate_invite_code(connection)
        cursor = connection.execute(
            """
            INSERT INTO families(name, invite_code, created_by_user_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.strip(),
            (name, invite_code, user_id, timestamp, timestamp),
        )
        family_id = int(cursor.lastrowid)
        connection.execute(
            """
            INSERT INTO family_members(family_id, user_id, role, joined_at)
            VALUES (?, ?, ?, ?)
            """.strip(),
            (family_id, user_id, "owner", timestamp),
        )
        body = family_response(connection, family_id, user_id, "Family created.")
        connection.commit()

    return body


@app.get("/api/v1/families/me")
def get_my_family(authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    with get_connection() as connection:
        membership = get_family_membership(connection, user_id)
        if membership is None:
            return family_error(404, "family_not_found", "User does not belong to a family.")
        return family_response(connection, int(membership["family_id"]), user_id, "Family resolved.")


@app.post("/api/v1/families/join")
def join_family(payload: FamilyJoinRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    invite_code = payload.invite_code.strip().upper()
    if not invite_code:
        return family_error(404, "invalid_invite_code", "Invite code is invalid.")

    with get_connection() as connection:
        if get_family_membership(connection, user_id) is not None:
            return family_error(409, "already_in_family", "User already belongs to a family.")

        family = connection.execute(
            "SELECT id FROM families WHERE invite_code = ? LIMIT 1",
            (invite_code,),
        ).fetchone()
        if family is None:
            return family_error(404, "invalid_invite_code", "Invite code is invalid.")

        family_id = int(family["id"])
        connection.execute(
            """
            INSERT INTO family_members(family_id, user_id, role, joined_at)
            VALUES (?, ?, ?, ?)
            """.strip(),
            (family_id, user_id, "member", now_millis()),
        )
        body = family_response(connection, family_id, user_id, "Family joined.")
        connection.commit()

    return body


@app.patch("/api/v1/families/{family_id}")
def rename_family(family_id: int, payload: FamilyRenameRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    name = payload.name.strip()
    if not name:
        return family_error(400, "invalid_family_name", "Family name is required.")

    with get_connection() as connection:
        owner = require_family_owner(connection, family_id, user_id)
        if isinstance(owner, JSONResponse):
            return owner
        connection.execute(
            "UPDATE families SET name = ?, updated_at = ? WHERE id = ?",
            (name, now_millis(), family_id),
        )
        body = family_response(connection, family_id, user_id, "Family updated.")
        connection.commit()

    return body


@app.post("/api/v1/families/{family_id}/invite-code/regenerate")
def regenerate_family_invite_code(family_id: int, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    with get_connection() as connection:
        owner = require_family_owner(connection, family_id, user_id)
        if isinstance(owner, JSONResponse):
            return owner
        connection.execute(
            "UPDATE families SET invite_code = ?, updated_at = ? WHERE id = ?",
            (generate_invite_code(connection), now_millis(), family_id),
        )
        body = family_response(connection, family_id, user_id, "Invite code regenerated.")
        connection.commit()

    return body


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
            return family_error(400, "owner_cannot_leave", "The family owner cannot leave in this version.")

        connection.execute(
            "DELETE FROM family_members WHERE family_id = ? AND user_id = ?",
            (family_id, user_id),
        )
        connection.commit()

    return {
        "success": True,
        "message": "Family left.",
        "family": None,
    }


@app.delete("/api/v1/families/{family_id}/members/{member_user_id}")
def remove_family_member(family_id: int, member_user_id: int, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    with get_connection() as connection:
        owner = require_family_owner(connection, family_id, user_id)
        if isinstance(owner, JSONResponse):
            return owner
        member = get_family_membership_for_family(connection, family_id, member_user_id)
        if member is None:
            return family_error(404, "member_not_found", "Family member not found.")
        if member["role"] == "owner":
            return family_error(400, "cannot_remove_owner", "The family owner cannot be removed.")

        connection.execute(
            "DELETE FROM family_members WHERE family_id = ? AND user_id = ?",
            (family_id, member_user_id),
        )
        body = family_response(connection, family_id, user_id, "Family member removed.")
        connection.commit()

    return body


@app.post("/api/v1/families/{family_id}/status")
def update_family_status(family_id: int, payload: FamilyStatusRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    if not payload.status_label.strip():
        return family_error(400, "invalid_status_label", "Status label is required.")

    with get_connection() as connection:
        membership = require_family_membership(connection, family_id, user_id)
        if isinstance(membership, JSONResponse):
            return membership
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
                payload.mood_type.strip() if payload.mood_type is not None else None,
                payload.mood_note.strip() if payload.mood_note is not None else None,
                payload.status_label.strip(),
                payload.updated_at,
                share_health_score,
                health_score,
                health_score_label,
                health_score_updated_at,
            ),
        )
        body = family_response(connection, family_id, user_id, "Family status updated.")
        connection.commit()

    return body


@app.post("/api/v1/families/{family_id}/supports")
def send_family_support(family_id: int, payload: FamilySupportRequest, authorization: str | None = Header(default=None)):
    user_id = require_family_user(authorization)
    if isinstance(user_id, JSONResponse):
        return user_id

    support_type = payload.support_type.strip()
    if support_type not in FAMILY_SUPPORT_TYPES:
        return family_error(400, "invalid_support_type", "Support type is invalid.")
    if payload.receiver_user_id == user_id:
        return family_error(400, "cannot_support_self", "Users cannot send support to themselves.")

    with get_connection() as connection:
        sender = require_family_membership(connection, family_id, user_id)
        if isinstance(sender, JSONResponse):
            return sender
        receiver = get_family_membership_for_family(connection, family_id, payload.receiver_user_id)
        if receiver is None:
            return family_error(404, "receiver_not_found", "Support receiver is not a family member.")

        timestamp = now_millis()
        try:
            connection.execute(
                """
                INSERT INTO family_supports(family_id, sender_user_id, receiver_user_id, support_type, support_date, sent_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.strip(),
                (family_id, user_id, payload.receiver_user_id, support_type, today_key(timestamp), timestamp),
            )
        except sqlite3.IntegrityError:
            return family_error(409, "duplicate_support", "Support has already been sent today.")

        body = family_response(connection, family_id, user_id, "Support sent.")
        connection.commit()

    return body


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


@app.post("/api/v1/ai/chat/stream")
def ai_chat_stream(payload: AiChatRequest):
    validation_error = validate_ai_chat_payload(payload)
    if validation_error is not None:
        return validation_error
    return StreamingResponse(stream_chat_completion(payload), media_type="text/event-stream")
