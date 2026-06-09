import importlib
import json
import tempfile
from pathlib import Path

from fastapi.testclient import TestClient


class FakeProviderResponse:
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def __iter__(self):
        chunks = [
            b'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n',
            b'data: {"choices":[{"delta":{"content":" there"}}]}\n\n',
            b"data: [DONE]\n\n",
        ]
        return iter(chunks)


def main():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        module = importlib.import_module("main")
        module.DB_PATH = Path(tmp) / "auth.db"
        module.initialize_database()

        captured = {}

        def fake_urlopen(request, timeout):
            captured["url"] = request.full_url
            captured["body"] = json.loads(request.data.decode("utf-8"))
            captured["authorization"] = request.headers["Authorization"]
            return FakeProviderResponse()

        module.urlopen_for_ai = fake_urlopen
        client = TestClient(module.app)

        incomplete = client.post(
            "/api/v1/ai/chat/stream",
            json={
                "provider": "deepseek",
                "base_url": "https://api.deepseek.com",
                "model": "deepseek-chat",
                "api_key": "",
                "messages": [{"role": "user", "content": "How am I doing?"}],
                "health_context": {},
            },
        )
        assert incomplete.status_code == 400, incomplete.text
        assert incomplete.json()["code"] == "missing_api_key"

        response = client.post(
            "/api/v1/ai/chat/stream",
            json={
                "provider": "deepseek",
                "base_url": "https://api.deepseek.com",
                "model": "deepseek-chat",
                "api_key": "sk-test",
                "messages": [{"role": "user", "content": "How am I doing?"}],
                "health_context": {
                    "latest_risk": {"total_score": 82, "risk_level": "low"},
                    "recent_summaries": [{"date": "2026-06-09", "sleep_minutes": 420}],
                    "latest_mood": {"mood_type": "CALM", "note": "steady"},
                },
            },
        )

        assert response.status_code == 200, response.text
        text = response.text
        assert 'data: {"delta": "Hello"}' in text
        assert 'data: {"delta": " there"}' in text
        assert 'data: {"done": true}' in text
        assert captured["url"] == "https://api.deepseek.com/chat/completions"
        assert captured["authorization"] == "Bearer sk-test"
        assert captured["body"]["stream"] is True
        assert captured["body"]["model"] == "deepseek-chat"
        assert captured["body"]["messages"][0]["role"] == "system"
        assert "not a medical diagnosis" in captured["body"]["messages"][0]["content"].lower()


if __name__ == "__main__":
    main()
