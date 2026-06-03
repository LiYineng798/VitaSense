import importlib
import sqlite3
import tempfile
from pathlib import Path


def main():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()

        with module.get_connection() as connection:
            cursor = connection.execute(
                """
                INSERT INTO users(full_name, email, username, password_hash, birth_date, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.strip(),
                ("Ava Stone", "ava@example.com", "ava", module.hash_password("password123"), "2000-01-02", 1),
            )
            user_id = int(cursor.lastrowid)
            token = module.create_session(connection, user_id)
            connection.commit()

            for table in [
                "user_settings",
                "cloud_mood_records",
                "cloud_heart_rate_samples",
                "cloud_sleep_records",
            ]:
                assert connection.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                    (table,),
                ).fetchone(), table

        assert module.get_user_id_from_authorization(f"Bearer {token}") == user_id
        assert module.get_user_id_from_authorization(None) is None
        assert module.get_user_id_from_authorization("Bearer missing") is None

    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module.DB_PATH = Path(tmp) / "legacy-auth.db"
        with sqlite3.connect(module.DB_PATH) as connection:
            connection.executescript(
                """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    birth_date TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );

                CREATE TABLE cloud_mood_records (
                    id TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    mood_type TEXT NOT NULL,
                    mood_group TEXT NOT NULL,
                    note TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER
                );

                CREATE TABLE cloud_heart_rate_samples (
                    id TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    sample_timestamp INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    heart_rate INTEGER NOT NULL,
                    source_batch_id TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                );

                CREATE TABLE cloud_sleep_records (
                    id TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    start_at INTEGER NOT NULL,
                    end_at INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    avg_heart_rate REAL,
                    heart_rate_variability_hint REAL,
                    source_batch_id TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER
                );
                """.strip()
            )

        module.initialize_database()

        with module.get_connection() as connection:
            for table in [
                "cloud_mood_records",
                "cloud_heart_rate_samples",
                "cloud_sleep_records",
            ]:
                id_column = [
                    column
                    for column in connection.execute(f"PRAGMA table_info({table})").fetchall()
                    if column["name"] == "id"
                ][0]
                assert id_column["pk"] == 2, table


if __name__ == "__main__":
    main()
