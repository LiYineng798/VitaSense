from __future__ import annotations

import hashlib
import secrets
import sqlite3
import time
from pathlib import Path

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
