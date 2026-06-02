import importlib
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


if __name__ == "__main__":
    main()
