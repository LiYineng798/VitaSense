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

    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module.DB_PATH = Path(tmp) / "legacy-family-supports.db"
        with sqlite3.connect(module.DB_PATH) as connection:
            connection.execute("PRAGMA foreign_keys = OFF")
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

                CREATE TABLE families (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    invite_code TEXT NOT NULL UNIQUE,
                    created_by_user_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(created_by_user_id) REFERENCES users(id) ON DELETE CASCADE
                );

                CREATE TABLE family_members (
                    family_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL UNIQUE,
                    role TEXT NOT NULL,
                    joined_at INTEGER NOT NULL,
                    PRIMARY KEY(family_id, user_id),
                    FOREIGN KEY(family_id) REFERENCES families(id) ON DELETE CASCADE,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                );

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
                    FOREIGN KEY(sender_user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY(receiver_user_id) REFERENCES users(id) ON DELETE CASCADE
                );

                CREATE INDEX idx_family_supports_receiver_date
                ON family_supports(family_id, receiver_user_id, support_date);

                INSERT INTO users(id, full_name, email, username, password_hash, birth_date, created_at)
                VALUES
                    (1, 'Sender', 'sender@example.com', 'sender', 'hash', '2000-01-01', 1),
                    (2, 'Receiver', 'receiver@example.com', 'receiver', 'hash', '2000-01-01', 1),
                    (3, 'Orphan', 'orphan@example.com', 'orphan', 'hash', '2000-01-01', 1);
                INSERT INTO families(id, name, invite_code, created_by_user_id, created_at, updated_at)
                VALUES (10, 'Family', 'INVITE', 1, 1, 1);
                INSERT INTO family_members(family_id, user_id, role, joined_at)
                VALUES (10, 1, 'owner', 1), (10, 2, 'member', 1);
                INSERT INTO family_supports(
                    id, family_id, sender_user_id, receiver_user_id, support_type, support_date, sent_at
                )
                VALUES
                    (100, 10, 1, 2, 'hug', '2026-01-01', 1),
                    (101, 10, 1, 3, 'hug', '2026-01-02', 1);
                """.strip()
            )

        module.initialize_database()

        with module.get_connection() as connection:
            foreign_keys = connection.execute("PRAGMA foreign_key_list(family_supports)").fetchall()
            composite_family_member_keys = [
                row
                for row in foreign_keys
                if row["table"] == "family_members"
            ]
            assert len(composite_family_member_keys) == 4
            assert {row["from"] for row in composite_family_member_keys} == {
                "family_id",
                "sender_user_id",
                "receiver_user_id",
            }
            support_ids = [
                row["id"]
                for row in connection.execute("SELECT id FROM family_supports ORDER BY id").fetchall()
            ]
            assert support_ids == [100]


if __name__ == "__main__":
    main()
