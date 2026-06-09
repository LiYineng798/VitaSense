# AI Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a center AI chat entry, a local Room-backed chat experience, and a backend streaming AI proxy that reuses the existing AI provider settings.

**Architecture:** The backend exposes a line-based streaming proxy endpoint. Android stores sessions/messages locally in Room, builds compact recent-health context, streams assistant chunks into the active assistant message, and shows a secondary chat fragment reached from an independent center AI button in the existing floating tab shell.

**Tech Stack:** Kotlin, Android XML Views, Fragment, Navigation Component, ViewBinding, Room, Coroutines/Flow, JUnit 4, FastAPI, Pydantic, SQLite, `urllib.request`.

---

## File Map

Backend:

- Modify `python_auth_api/main.py`: add chat request models, streaming helpers, and `/api/v1/ai/chat/stream`.
- Create `python_auth_api/ai_chat_stream_test.py`: test validation and simulated streaming behavior.

Android model and database:

- Create `app/src/main/java/org/wit/vitasense/model/AiChatModels.kt`: roles, statuses, stream events, request context models, error mapping.
- Create `app/src/main/java/org/wit/vitasense/db/entity/AiChatSessionEntity.kt`.
- Create `app/src/main/java/org/wit/vitasense/db/entity/AiChatMessageEntity.kt`.
- Create `app/src/main/java/org/wit/vitasense/db/dao/AiChatSessionDao.kt`.
- Create `app/src/main/java/org/wit/vitasense/db/dao/AiChatMessageDao.kt`.
- Modify `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`: register entities/DAOs and bump version.

Android data layer:

- Create `app/src/main/java/org/wit/vitasense/repository/AiChatRepository.kt`.
- Create `app/src/main/java/org/wit/vitasense/data/repository/AiChatRemoteDataSource.kt`.
- Create `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSource.kt`.
- Create `app/src/main/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilder.kt`.
- Create `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRepository.kt`.
- Modify `app/src/main/java/org/wit/vitasense/AppContainer.kt`: construct `aiChatRepository`.

Android UI:

- Create `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatUiModels.kt`.
- Create `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatViewModel.kt`.
- Create `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatMessageAdapter.kt`.
- Create `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatFragment.kt`.
- Create `app/src/main/res/layout/fragment_ai_chat.xml`.
- Create `app/src/main/res/layout/item_ai_chat_message.xml`.
- Modify `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`: add `AiChatViewModel`.
- Modify `app/src/main/res/navigation/main_nav_graph.xml`: add `aiChatFragment`.
- Modify `app/src/main/res/layout/view_floating_bottom_tabs.xml`: add center AI action.
- Modify `app/src/main/java/org/wit/vitasense/MainActivity.kt`: bind center AI action.
- Modify `app/src/main/res/values/strings.xml`: add AI chat strings.
- Modify `app/src/main/res/values/dimens.xml`: add AI action dimensions if needed.

Tests:

- Create `app/src/test/java/org/wit/vitasense/model/AiChatModelsTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSourceTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilderTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRepositoryTest.kt`.
- Create `app/src/test/java/org/wit/vitasense/ui/aichat/AiChatViewModelTest.kt`.
- Create or extend `app/src/androidTest/java/org/wit/vitasense/ui/AiChatNavigationSmokeTest.kt`.

---

### Task 1: Backend Streaming Chat Proxy

**Files:**
- Modify: `python_auth_api/main.py`
- Create: `python_auth_api/ai_chat_stream_test.py`

- [ ] **Step 1: Write the backend stream test**

Create `python_auth_api/ai_chat_stream_test.py`:

```python
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
```

- [ ] **Step 2: Run the backend test and verify it fails**

Run:

```powershell
cd python_auth_api
python ai_chat_stream_test.py
```

Expected: FAIL with `404 Not Found` for `/api/v1/ai/chat/stream` or `AttributeError` for `urlopen_for_ai`.

- [ ] **Step 3: Add backend models and stream endpoint**

In `python_auth_api/main.py`, add `StreamingResponse` and `Iterator` imports:

```python
from collections.abc import Iterator
from fastapi.responses import JSONResponse, StreamingResponse
from urllib.request import Request, urlopen
```

If `JSONResponse` is already imported separately, merge the import.

Add these Pydantic models near `AiAdviceRequest`:

```python
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
```

Add a module-level hook after `app = FastAPI(...)`:

```python
urlopen_for_ai = urlopen
```

Add helpers near the existing AI advice helpers:

```python
def validate_ai_chat_payload(payload: AiChatRequest) -> JSONResponse | None:
    if not payload.api_key.strip():
        return ai_error(400, "missing_api_key", "Add an API key in Settings first.")
    if not payload.base_url.strip():
        return ai_error(400, "missing_base_url", "Add an AI base URL in Settings first.")
    if not payload.model.strip():
        return ai_error(400, "missing_model", "Add a model name in Settings first.")
    if payload.provider.strip().lower() not in {"deepseek", "openai_compatible"}:
        return ai_error(400, "unsupported_provider", "The selected AI provider is not supported.")
    if not any(message.role == "user" and message.content.strip() for message in payload.messages):
        return ai_error(400, "empty_message", "Enter a message before sending.")
    return None


def build_chat_system_message(health_context: dict[str, Any]) -> str:
    return (
        "You are VitaSense AI Chat. Help the user reflect on their health trends, mood, "
        "stress, and recovery state using the provided VitaSense context. This is health "
        "support and state review, not a medical diagnosis. Encourage practical, low-risk "
        "next steps. For urgent, severe, or dangerous symptoms, recommend professional or "
        "emergency help.\n\n"
        f"Recent VitaSense context JSON: {json.dumps(health_context, ensure_ascii=False)}"
    )


def chat_completion_url(base_url: str) -> str:
    return base_url.strip().remove_suffix("/") + "/chat/completions"


def provider_chat_messages(payload: AiChatRequest) -> list[dict[str, str]]:
    messages = [{"role": "system", "content": build_chat_system_message(payload.health_context)}]
    for message in payload.messages[-20:]:
        role = message.role.strip().lower()
        if role in {"user", "assistant"} and message.content.strip():
            messages.append({"role": role, "content": message.content.strip()})
    return messages


def stream_chat_completion(payload: AiChatRequest) -> Iterator[str]:
    body = json.dumps(
        {
            "model": payload.model.strip(),
            "messages": provider_chat_messages(payload),
            "stream": True,
        },
    ).encode("utf-8")
    request = Request(
        chat_completion_url(payload.base_url),
        data=body,
        headers={
            "Authorization": f"Bearer {payload.api_key.strip()}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
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
                    yield 'data: {"done": true}\n\n'
                    return
                try:
                    obj = json.loads(data)
                    delta = obj.get("choices", [{}])[0].get("delta", {}).get("content", "")
                except (json.JSONDecodeError, KeyError, IndexError, TypeError):
                    delta = ""
                if delta:
                    yield "data: " + json.dumps({"delta": delta}) + "\n\n"
            yield 'data: {"done": true}\n\n'
    except urllib.error.HTTPError as exc:
        error = map_provider_error(exc)
        body = json.loads(error.body.decode("utf-8")) if isinstance(error.body, bytes) else {"code": "unexpected_ai_response", "message": "The AI service returned an unexpected response."}
        yield "data: " + json.dumps({"error": {"code": body.get("code", "unexpected_ai_response"), "message": body.get("message", "")}}) + "\n\n"
    except (urllib.error.URLError, TimeoutError):
        yield 'data: {"error": {"code": "ai_network_error", "message": "Unable to reach the AI service. Check your network or base URL."}}\n\n'
```

Add endpoint near `/api/v1/ai/advice`:

```python
@app.post("/api/v1/ai/chat/stream")
def ai_chat_stream(payload: AiChatRequest):
    validation_error = validate_ai_chat_payload(payload)
    if validation_error is not None:
        return validation_error
    return StreamingResponse(stream_chat_completion(payload), media_type="text/event-stream")
```

- [ ] **Step 4: Run backend test and fix import issues**

Run:

```powershell
cd python_auth_api
python ai_chat_stream_test.py
```

Expected: PASS with no output.

- [ ] **Step 5: Commit backend proxy**

```powershell
git add -- python_auth_api/main.py python_auth_api/ai_chat_stream_test.py
git commit -m "feat: add streaming AI chat proxy"
```

---

### Task 2: Android Chat Models and Room Storage

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/model/AiChatModels.kt`
- Create: `app/src/main/java/org/wit/vitasense/db/entity/AiChatSessionEntity.kt`
- Create: `app/src/main/java/org/wit/vitasense/db/entity/AiChatMessageEntity.kt`
- Create: `app/src/main/java/org/wit/vitasense/db/dao/AiChatSessionDao.kt`
- Create: `app/src/main/java/org/wit/vitasense/db/dao/AiChatMessageDao.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`
- Test: `app/src/test/java/org/wit/vitasense/model/AiChatModelsTest.kt`

- [ ] **Step 1: Write model parsing tests**

Create `app/src/test/java/org/wit/vitasense/model/AiChatModelsTest.kt`:

```kotlin
package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatModelsTest {
    @Test
    fun parsesDeltaDoneAndErrorStreamEvents() {
        assertEquals(AiChatStreamEvent.Delta("Hi"), parseAiChatStreamLine("""data: {"delta":"Hi"}"""))
        assertEquals(AiChatStreamEvent.Done, parseAiChatStreamLine("""data: {"done":true}"""))
        assertEquals(
            AiChatStreamEvent.Error("invalid_api_key", "Bad key"),
            parseAiChatStreamLine("""data: {"error":{"code":"invalid_api_key","message":"Bad key"}}"""),
        )
    }

    @Test
    fun ignoresBlankOrMalformedLines() {
        assertEquals(null, parseAiChatStreamLine(""))
        assertEquals(null, parseAiChatStreamLine("event: message"))
        assertTrue(aiChatErrorMessage("quota_or_rate_limit").contains("quota", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run model test and verify it fails**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.AiChatModelsTest"
```

Expected: FAIL because `AiChatModels.kt` does not exist.

- [ ] **Step 3: Add AI chat model file**

Create `app/src/main/java/org/wit/vitasense/model/AiChatModels.kt`:

```kotlin
package org.wit.vitasense.model

import org.json.JSONObject

enum class AiChatRole(val storageKey: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

enum class AiChatMessageStatus(val storageKey: String) {
    SENDING("sending"),
    STREAMING("streaming"),
    COMPLETE("complete"),
    FAILED("failed"),
}

data class AiChatMessage(
    val role: AiChatRole,
    val content: String,
)

data class AiChatHealthContext(
    val latestRisk: Map<String, Any?>,
    val recentSummaries: List<Map<String, Any?>>,
    val latestMood: Map<String, Any?>?,
)

sealed interface AiChatStreamEvent {
    data class Delta(val text: String) : AiChatStreamEvent
    data object Done : AiChatStreamEvent
    data class Error(val code: String, val message: String) : AiChatStreamEvent
}

fun parseAiChatStreamLine(raw: String): AiChatStreamEvent? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload.isBlank()) return null
    val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    if (obj.optBoolean("done", false)) return AiChatStreamEvent.Done
    obj.optString("delta").takeIf { it.isNotBlank() }?.let { return AiChatStreamEvent.Delta(it) }
    val error = obj.optJSONObject("error") ?: return null
    return AiChatStreamEvent.Error(
        code = error.optString("code", "unexpected_ai_response"),
        message = error.optString("message"),
    )
}

fun aiChatErrorMessage(code: String): String =
    when (code) {
        "missing_api_key" -> "Add an API key in Settings first."
        "missing_model" -> "Add a model name in Settings first."
        "missing_base_url" -> "Add an AI base URL in Settings first."
        "invalid_api_key" -> "The API key is invalid or expired."
        "model_unavailable" -> "The selected model is not available. Check the model name."
        "quota_or_rate_limit" -> "The AI service quota or rate limit was reached."
        "ai_network_error" -> "Unable to reach the AI service. Check your network or base URL."
        "proxy_unreachable" -> "Unable to reach the VitaSense AI proxy."
        "empty_message" -> "Enter a message before sending."
        else -> "Unable to continue the AI chat right now."
    }
```

- [ ] **Step 4: Add Room entities and DAOs**

Create `AiChatSessionEntity.kt`:

```kotlin
package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_sessions",
    indices = [Index(value = ["updatedAt"])],
)
data class AiChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isCurrent: Boolean = false,
)
```

Create `AiChatMessageEntity.kt`:

```kotlin
package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"]),
    ],
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val errorMessage: String? = null,
)
```

Create `AiChatSessionDao.kt`:

```kotlin
package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatSessionEntity

@Dao
interface AiChatSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AiChatSessionEntity): Long

    @Update
    suspend fun update(session: AiChatSessionEntity)

    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE isCurrent = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun observeCurrent(): Flow<AiChatSessionEntity?>

    @Query("SELECT * FROM ai_chat_sessions WHERE isCurrent = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getCurrent(): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiChatSessionEntity?

    @Query("UPDATE ai_chat_sessions SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query("UPDATE ai_chat_sessions SET isCurrent = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCurrent(id: Long, updatedAt: Long)

    @Query("UPDATE ai_chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM ai_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

Create `AiChatMessageDao.kt`:

```kotlin
package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatMessageEntity

@Dao
interface AiChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AiChatMessageEntity): Long

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeForSession(sessionId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getForSession(sessionId: Long): List<AiChatMessageEntity>

    @Query("UPDATE ai_chat_messages SET content = :content, status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateContentAndStatus(
        id: Long,
        content: String,
        status: String,
        errorMessage: String?,
    )

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
```

- [ ] **Step 5: Register Room entities and DAOs**

Modify `AppDatabase.kt`:

```kotlin
@Database(
    entities = [
        HeartRateRawSampleEntity::class,
        SleepRecordEntity::class,
        DailyPhysiologySummaryEntity::class,
        RiskAssessmentRecordEntity::class,
        MoodRecordEntity::class,
        AppSettingEntity::class,
        ImportLogEntity::class,
        LocalUserEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heartRateRawSampleDao(): HeartRateRawSampleDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun riskAssessmentDao(): RiskAssessmentDao
    abstract fun moodRecordDao(): MoodRecordDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun localUserDao(): LocalUserDao
    abstract fun aiChatSessionDao(): AiChatSessionDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
}
```

Add imports for the new DAOs/entities.

- [ ] **Step 6: Run model test and assemble to catch Room processor errors**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.model.AiChatModelsTest"
./gradlew.bat :app:assembleDebug
```

Expected: both PASS.

- [ ] **Step 7: Commit Android models and Room storage**

```powershell
git add -- app/src/main/java/org/wit/vitasense/model/AiChatModels.kt app/src/main/java/org/wit/vitasense/db/entity/AiChatSessionEntity.kt app/src/main/java/org/wit/vitasense/db/entity/AiChatMessageEntity.kt app/src/main/java/org/wit/vitasense/db/dao/AiChatSessionDao.kt app/src/main/java/org/wit/vitasense/db/dao/AiChatMessageDao.kt app/src/main/java/org/wit/vitasense/db/AppDatabase.kt app/src/test/java/org/wit/vitasense/model/AiChatModelsTest.kt
git commit -m "feat: add local AI chat storage"
```

---

### Task 3: Android Remote Streaming Client and Health Context

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/data/repository/AiChatRemoteDataSource.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSource.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilder.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSourceTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilderTest.kt`

- [ ] **Step 1: Write remote data source tests**

Create `DefaultAiChatRemoteDataSourceTest.kt` with the same fake `HttpURLConnection` style as `DefaultAiAdviceRepositoryTest`:

```kotlin
package org.wit.vitasense.data.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig

class DefaultAiChatRemoteDataSourceTest {
    @Test
    fun streamsDeltaAndDoneEvents() = runBlocking {
        val events = mutableListOf<AiChatStreamEvent>()
        val dataSource =
            DefaultAiChatRemoteDataSource(
                proxyBaseUrl = "https://server.example",
                connectionFactory =
                    FakeAiChatConnectionFactory(
                        code = 200,
                        body = """
                            data: {"delta":"Hello"}

                            data: {"delta":" there"}

                            data: {"done":true}
                        """.trimIndent(),
                    ),
            )

        dataSource.streamChat(config(), listOf(AiChatMessage(AiChatRole.USER, "Hi")), emptyMap()) {
            events += it
        }

        assertEquals(listOf(AiChatStreamEvent.Delta("Hello"), AiChatStreamEvent.Delta(" there"), AiChatStreamEvent.Done), events)
    }

    @Test
    fun mapsTransportErrorToProxyUnreachable() = runBlocking {
        val events = mutableListOf<AiChatStreamEvent>()
        val dataSource =
            DefaultAiChatRemoteDataSource(
                proxyBaseUrl = "https://server.example",
                connectionFactory = FakeAiChatConnectionFactory(code = 500, body = ""),
            )

        dataSource.streamChat(config(), listOf(AiChatMessage(AiChatRole.USER, "Hi")), emptyMap()) {
            events += it
        }

        assertEquals(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."), events.single())
    }

    private fun config() =
        AiProviderConfig(AiProvider.DEEPSEEK, "sk-test", "https://api.deepseek.com", "deepseek-chat")
}

private class FakeAiChatConnectionFactory(
    private val code: Int,
    private val body: String,
) : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = FakeAiChatConnection(url, code, body)
}

private class FakeAiChatConnection(
    url: URL,
    private val code: Int,
    private val body: String,
) : HttpURLConnection(url) {
    private val requestBuffer = java.io.ByteArrayOutputStream()

    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
    override fun getOutputStream() = requestBuffer
    override fun getResponseCode(): Int = code
    override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream(): InputStream = ByteArrayInputStream(body.toByteArray())
}
```

- [ ] **Step 2: Write health context builder test**

Create `AiChatHealthContextBuilderTest.kt`:

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository

class AiChatHealthContextBuilderTest {
    @Test
    fun buildsCompactHealthContext() = runBlocking {
        val context =
            AiChatHealthContextBuilder(
                healthRepository = FakeChatHealthRepository(),
                moodRepository = FakeChatMoodRepository(),
            ).build()

        assertEquals(82, context["latest_risk"]?.let { it as Map<*, *> }?.get("total_score"))
        assertEquals(1, (context["recent_summaries"] as List<*>).size)
        assertEquals("CALM", context["latest_mood"]?.let { it as Map<*, *> }?.get("mood_type"))
    }
}

private class FakeChatHealthRepository : HealthRepository {
    override fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?> = flowOf(null)
    override fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?> = flowOf(null)
    override fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?> =
        flowOf(RiskAssessmentRecordEntity("2026-06-09", 82, "low", 20, 20, 20, 22, "Stable", "Rest well."))
    override fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>> =
        flowOf(listOf(DailyPhysiologySummaryEntity("2026-06-09", 66.0, 61.0, 35.0, 50.0, 420, 62.0, 34.0, 67.0, "", "Stable.")))
    override fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>> = flowOf(emptyList())
    override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> = emptyList()
    override suspend fun importDemoBundle(bundleId: String): ImportOperationResult = ImportOperationResult(ImportStatus.SUCCESS, "", 0, 0, 0, 0)
    override suspend fun importRawJson(raw: String, sourceName: String): ImportOperationResult = ImportOperationResult(ImportStatus.SUCCESS, "", 0, 0, 0, 0)
    override suspend fun clearAllData() = Unit
}

private class FakeChatMoodRepository : MoodRepository {
    override fun observeMoodRecords(filter: MoodFilter): Flow<List<MoodRecordEntity>> = flowOf(emptyList())
    override suspend fun addMood(date: String, moodType: org.wit.vitasense.model.MoodType, note: String?) = Unit
    override suspend fun deleteMood(id: Long) = Unit
    override suspend fun getLatestMoodForDate(date: String): MoodRecordEntity? =
        MoodRecordEntity(date = date, moodType = "CALM", moodGroup = "positive", note = "steady", createdAt = 1L)
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiChatRemoteDataSourceTest" --tests "org.wit.vitasense.data.repository.AiChatHealthContextBuilderTest"
```

Expected: FAIL because remote data source and context builder do not exist.

- [ ] **Step 4: Add remote data source**

Create `AiChatRemoteDataSource.kt`:

```kotlin
package org.wit.vitasense.data.repository

import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProviderConfig

interface AiChatRemoteDataSource {
    suspend fun streamChat(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
        onEvent: suspend (AiChatStreamEvent) -> Unit,
    )
}
```

Create `DefaultAiChatRemoteDataSource.kt`:

```kotlin
package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.parseAiChatStreamLine

class DefaultAiChatRemoteDataSource(
    private val proxyBaseUrl: String,
    private val connectionFactory: AiConnectionFactory = DefaultAiConnectionFactory,
) : AiChatRemoteDataSource {
    override suspend fun streamChat(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
        onEvent: suspend (AiChatStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = connectionFactory.open(URL(proxyBaseUrl.trim().removeSuffix("/") + "/api/v1/ai/chat/stream"))
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(buildPayload(config, messages, healthContext).toString())
            }
            if (connection.responseCode !in 200..299) {
                onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
                return@withContext
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val event = parseAiChatStreamLine(line)
                    if (event != null) {
                        kotlinx.coroutines.runBlocking { onEvent(event) }
                    }
                }
            }
        } catch (_: IOException) {
            onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
        } catch (_: SecurityException) {
            onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
    ): JSONObject =
        JSONObject()
            .put("provider", config.provider.storageKey)
            .put("base_url", config.baseUrl)
            .put("model", config.model)
            .put("api_key", config.apiKey)
            .put("messages", JSONArray(messages.map { JSONObject().put("role", it.role.storageKey).put("content", it.content) }))
            .put("health_context", JSONObject(healthContext))
}
```

If the compiler flags `runBlocking` inside `useLines`, replace the `forEach` block with an explicit loop over `readLine()` inside the suspend context.

- [ ] **Step 5: Add health context builder**

Create `AiChatHealthContextBuilder.kt`:

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.first
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository
import org.wit.vitasense.util.DateUtils

class AiChatHealthContextBuilder(
    private val healthRepository: HealthRepository,
    private val moodRepository: MoodRepository,
) {
    suspend fun build(): Map<String, Any?> {
        val latestRisk = healthRepository.observeLatestRisk().first()
        val summaries = healthRepository.observeSummaries(7).first()
        val latestDate = summaries.maxByOrNull { it.date }?.date ?: DateUtils.todayString()
        val mood = moodRepository.getLatestMoodForDate(latestDate)
        return mapOf(
            "latest_risk" to latestRisk?.let {
                mapOf(
                    "date" to it.date,
                    "total_score" to it.totalScore,
                    "risk_level" to it.riskLevel,
                    "explanation" to it.explanation,
                    "suggestion" to it.suggestionText,
                )
            },
            "recent_summaries" to summaries.map {
                mapOf(
                    "date" to it.date,
                    "sleep_minutes" to it.sleepDurationMinutes,
                    "rmssd" to it.rmssd,
                    "resting_heart_rate" to it.restingHeartRate,
                    "avg_heart_rate" to it.avgHeartRate,
                    "anomaly_flags" to it.anomalyFlags,
                    "summary" to it.summaryText,
                )
            },
            "latest_mood" to mood?.let {
                mapOf(
                    "date" to it.date,
                    "mood_type" to it.moodType,
                    "mood_group" to it.moodGroup,
                    "note" to it.note,
                )
            },
        )
    }
}
```

- [ ] **Step 6: Run remote/context tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiChatRemoteDataSourceTest" --tests "org.wit.vitasense.data.repository.AiChatHealthContextBuilderTest"
```

Expected: PASS.

- [ ] **Step 7: Commit remote client and context builder**

```powershell
git add -- app/src/main/java/org/wit/vitasense/data/repository/AiChatRemoteDataSource.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSource.kt app/src/main/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilder.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRemoteDataSourceTest.kt app/src/test/java/org/wit/vitasense/data/repository/AiChatHealthContextBuilderTest.kt
git commit -m "feat: add AI chat streaming client"
```

---

### Task 4: Android Chat Repository

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/repository/AiChatRepository.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRepositoryTest.kt`

- [ ] **Step 1: Write repository behavior test**

Create `DefaultAiChatRepositoryTest.kt` with fake DAOs and remote source. The test should assert:

```kotlin
@Test
fun sendMessageCreatesSessionPersistsUserMessageAndStreamsAssistant() = runBlocking {
    val sessionDao = FakeAiChatSessionDao()
    val messageDao = FakeAiChatMessageDao()
    val repository =
        DefaultAiChatRepository(
            sessionDao = sessionDao,
            messageDao = messageDao,
            settingsRepository = FakeAiChatSettingsRepository(AiProviderConfig(AiProvider.DEEPSEEK, "sk", "https://api.deepseek.com", "deepseek-chat")),
            remoteDataSource = FakeStreamingRemoteDataSource(listOf(AiChatStreamEvent.Delta("Hello"), AiChatStreamEvent.Delta(" there"), AiChatStreamEvent.Done)),
            healthContextBuilder = FakeHealthContextBuilder(mapOf("latest_risk" to mapOf("total_score" to 82))),
            clock = { 10L },
        )

    repository.sendMessage("How am I doing?")

    val messages = messageDao.rows
    assertEquals("user", messages[0].role)
    assertEquals("How am I doing?", messages[0].content)
    assertEquals("assistant", messages[1].role)
    assertEquals("Hello there", messages[1].content)
    assertEquals("complete", messages[1].status)
}
```

Use focused fake classes in the test file. Implement only interface methods used by the repository; return `flowOf(...)` for observed state.

- [ ] **Step 2: Run repository test and verify it fails**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiChatRepositoryTest"
```

Expected: FAIL because repository does not exist.

- [ ] **Step 3: Add repository interface**

Create `AiChatRepository.kt`:

```kotlin
package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatMessageEntity
import org.wit.vitasense.db.entity.AiChatSessionEntity

interface AiChatRepository {
    fun observeCurrentSession(): Flow<AiChatSessionEntity?>
    fun observeMessages(sessionId: Long): Flow<List<AiChatMessageEntity>>
    suspend fun ensureCurrentSession(): Long
    suspend fun startNewChat(): Long
    suspend fun deleteCurrentChat()
    suspend fun sendMessage(text: String)
}
```

- [ ] **Step 4: Add repository implementation**

Create `DefaultAiChatRepository.kt`:

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.dao.AiChatMessageDao
import org.wit.vitasense.db.dao.AiChatSessionDao
import org.wit.vitasense.db.entity.AiChatMessageEntity
import org.wit.vitasense.db.entity.AiChatSessionEntity
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatMessageStatus
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.aiChatErrorMessage
import org.wit.vitasense.repository.AiChatRepository
import org.wit.vitasense.repository.SettingsRepository

class DefaultAiChatRepository(
    private val sessionDao: AiChatSessionDao,
    private val messageDao: AiChatMessageDao,
    private val settingsRepository: SettingsRepository,
    private val remoteDataSource: AiChatRemoteDataSource,
    private val healthContextBuilder: AiChatHealthContextBuilder,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AiChatRepository {
    override fun observeCurrentSession(): Flow<AiChatSessionEntity?> = sessionDao.observeCurrent()

    override fun observeMessages(sessionId: Long): Flow<List<AiChatMessageEntity>> = messageDao.observeForSession(sessionId)

    override suspend fun ensureCurrentSession(): Long {
        sessionDao.getCurrent()?.let { return it.id }
        return startNewChat()
    }

    override suspend fun startNewChat(): Long {
        val now = clock()
        sessionDao.clearCurrent()
        return sessionDao.insert(AiChatSessionEntity(title = "New chat", createdAt = now, updatedAt = now, isCurrent = true))
    }

    override suspend fun deleteCurrentChat() {
        val current = sessionDao.getCurrent() ?: return
        messageDao.deleteForSession(current.id)
        sessionDao.deleteById(current.id)
        startNewChat()
    }

    override suspend fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val config = settingsRepository.getAiProviderConfig()
        val sessionId = ensureCurrentSession()
        val now = clock()
        val userId =
            messageDao.insert(
                AiChatMessageEntity(
                    sessionId = sessionId,
                    role = AiChatRole.USER.storageKey,
                    content = trimmed,
                    createdAt = now,
                    status = AiChatMessageStatus.COMPLETE.storageKey,
                ),
            )
        if (sessionDao.getById(sessionId)?.title == "New chat") {
            sessionDao.updateTitle(sessionId, trimmed.take(36), now)
        } else {
            sessionDao.markCurrent(sessionId, now)
        }
        val assistantId =
            messageDao.insert(
                AiChatMessageEntity(
                    sessionId = sessionId,
                    role = AiChatRole.ASSISTANT.storageKey,
                    content = "",
                    createdAt = now + 1,
                    status = AiChatMessageStatus.STREAMING.storageKey,
                ),
            )
        val history =
            messageDao.getForSession(sessionId)
                .filter { it.id != assistantId }
                .mapNotNull { entity ->
                    when (entity.role) {
                        AiChatRole.USER.storageKey -> AiChatMessage(AiChatRole.USER, entity.content)
                        AiChatRole.ASSISTANT.storageKey -> AiChatMessage(AiChatRole.ASSISTANT, entity.content)
                        else -> null
                    }
                }
        var assistantText = ""
        remoteDataSource.streamChat(config, history, healthContextBuilder.build()) { event ->
            when (event) {
                is AiChatStreamEvent.Delta -> {
                    assistantText += event.text
                    messageDao.updateContentAndStatus(assistantId, assistantText, AiChatMessageStatus.STREAMING.storageKey, null)
                }
                AiChatStreamEvent.Done ->
                    messageDao.updateContentAndStatus(assistantId, assistantText, AiChatMessageStatus.COMPLETE.storageKey, null)
                is AiChatStreamEvent.Error ->
                    messageDao.updateContentAndStatus(
                        assistantId,
                        assistantText,
                        AiChatMessageStatus.FAILED.storageKey,
                        event.message.ifBlank { aiChatErrorMessage(event.code) },
                    )
            }
        }
    }
}
```

Remove `userId` if the compiler reports it as unused.

- [ ] **Step 5: Wire repository in AppContainer**

Modify `AppContainer.kt`:

```kotlin
val aiChatRepository: AiChatRepository by lazy {
    DefaultAiChatRepository(
        sessionDao = database.aiChatSessionDao(),
        messageDao = database.aiChatMessageDao(),
        settingsRepository = settingsRepository,
        remoteDataSource = DefaultAiChatRemoteDataSource(proxyBaseUrl = DEFAULT_AI_PROXY_BASE_URL),
        healthContextBuilder =
            AiChatHealthContextBuilder(
                healthRepository = healthRepository,
                moodRepository = moodRepository,
            ),
    )
}
```

Add imports for `AiChatRepository`, `DefaultAiChatRepository`, `DefaultAiChatRemoteDataSource`, and `AiChatHealthContextBuilder`.

- [ ] **Step 6: Run repository test**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiChatRepositoryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit repository**

```powershell
git add -- app/src/main/java/org/wit/vitasense/repository/AiChatRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultAiChatRepository.kt app/src/main/java/org/wit/vitasense/AppContainer.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultAiChatRepositoryTest.kt
git commit -m "feat: add AI chat repository"
```

---

### Task 5: Chat ViewModel, Fragment, and Layout

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatUiModels.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatViewModel.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatMessageAdapter.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/aichat/AiChatFragment.kt`
- Create: `app/src/main/res/layout/fragment_ai_chat.xml`
- Create: `app/src/main/res/layout/item_ai_chat_message.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/org/wit/vitasense/ui/aichat/AiChatViewModelTest.kt`

- [ ] **Step 1: Write ViewModel test**

Create `AiChatViewModelTest.kt` to cover:

```kotlin
@Test
fun exposesSetupRequiredWhenAiConfigIsIncomplete() = runBlocking {
    val viewModel =
        AiChatViewModel(
            aiChatRepository = FakeAiChatRepository(),
            settingsRepository = FakeSettingsRepository(AiProviderConfig()),
            scope = this,
        )

    assertEquals(true, viewModel.state.value.setupRequired)
}

@Test
fun sendMessageDelegatesToRepositoryWhenConfigIsComplete() = runBlocking {
    val repository = FakeAiChatRepository()
    val viewModel =
        AiChatViewModel(
            aiChatRepository = repository,
            settingsRepository = FakeSettingsRepository(AiProviderConfig(AiProvider.DEEPSEEK, "sk", "https://api.deepseek.com", "deepseek-chat")),
            scope = this,
        )

    viewModel.sendMessage("How am I doing?")

    assertEquals("How am I doing?", repository.sentMessages.single())
}
```

Use fake repository/settings classes in the test file.

- [ ] **Step 2: Run ViewModel test and verify it fails**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.aichat.AiChatViewModelTest"
```

Expected: FAIL because UI classes do not exist.

- [ ] **Step 3: Add UI models and ViewModel**

Create `AiChatUiModels.kt`:

```kotlin
package org.wit.vitasense.ui.aichat

data class AiChatMessageUiModel(
    val id: Long,
    val role: String,
    val content: String,
    val isAssistant: Boolean,
    val isStreaming: Boolean,
    val errorText: String?,
)

data class AiChatScreenState(
    val sessionId: Long? = null,
    val title: String = "AI Chat",
    val messages: List<AiChatMessageUiModel> = emptyList(),
    val setupRequired: Boolean = false,
    val isGenerating: Boolean = false,
    val errorText: String? = null,
)
```

Create `AiChatViewModel.kt`:

```kotlin
package org.wit.vitasense.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.AiChatMessageStatus
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.repository.AiChatRepository
import org.wit.vitasense.repository.SettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val localError = MutableStateFlow<String?>(null)

    val state: StateFlow<AiChatScreenState> =
        combine(
            aiChatRepository.observeCurrentSession(),
            settingsRepository.observeAiProviderConfig(),
            localError,
        ) { session, config, error ->
            Triple(session, config, error)
        }.flatMapLatest { (session, config, error) ->
            val messagesFlow = session?.let { aiChatRepository.observeMessages(it.id) } ?: flowOf(emptyList())
            messagesFlow.combine(flowOf(Triple(session, config, error))) { messages, values ->
                val currentSession = values.first
                val currentConfig = values.second
                val generating = messages.any { it.status == AiChatMessageStatus.STREAMING.storageKey || it.status == AiChatMessageStatus.SENDING.storageKey }
                AiChatScreenState(
                    sessionId = currentSession?.id,
                    title = currentSession?.title ?: "AI Chat",
                    messages =
                        messages.map {
                            AiChatMessageUiModel(
                                id = it.id,
                                role = it.role,
                                content = it.content,
                                isAssistant = it.role == AiChatRole.ASSISTANT.storageKey,
                                isStreaming = it.status == AiChatMessageStatus.STREAMING.storageKey,
                                errorText = it.errorMessage,
                            )
                        },
                    setupRequired = !currentConfig.isComplete,
                    isGenerating = generating,
                    errorText = values.third,
                )
            }
        }.stateIn(modelScope, SharingStarted.WhileSubscribed(5_000), AiChatScreenState())

    fun sendMessage(text: String) {
        if (state.value.setupRequired || state.value.isGenerating) return
        modelScope.launch {
            localError.value = null
            aiChatRepository.sendMessage(text)
        }
    }

    fun startNewChat() {
        modelScope.launch { aiChatRepository.startNewChat() }
    }

    fun deleteCurrentChat() {
        modelScope.launch { aiChatRepository.deleteCurrentChat() }
    }
}
```

- [ ] **Step 4: Add XML layouts**

Create `fragment_ai_chat.xml` with a top bar, RecyclerView, setup text, progress, input, and send button. Use stable ids:

- `aiChatBackButton`
- `aiChatTitleText`
- `aiChatNewButton`
- `aiChatDeleteButton`
- `aiChatSetupText`
- `aiChatRecyclerView`
- `aiChatProgress`
- `aiChatInput`
- `aiChatSendButton`

Create `item_ai_chat_message.xml` with:

- `messageContainer`
- `messageText`
- `messageErrorText`
- `messageProgress`

Use `MaterialCardView` or simple `TextView` bubbles consistent with existing XML view style.

- [ ] **Step 5: Add adapter and fragment**

Create `AiChatMessageAdapter.kt` as a `RecyclerView.Adapter` rendering user messages aligned end and assistant messages aligned start. Show `messageProgress` when `isStreaming` is true and `messageErrorText` when `errorText` is non-null.

Create `AiChatFragment.kt`:

```kotlin
package org.wit.vitasense.ui.aichat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentAiChatBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class AiChatFragment : Fragment() {
    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val adapter = AiChatMessageAdapter()
    private val viewModel: AiChatViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.aiChatRecyclerView.adapter = adapter
        binding.aiChatBackButton.setOnClickListener { findNavController().navigateUp() }
        binding.aiChatNewButton.setOnClickListener { viewModel.startNewChat() }
        binding.aiChatDeleteButton.setOnClickListener { viewModel.deleteCurrentChat() }
        binding.aiChatSendButton.setOnClickListener {
            val text = binding.aiChatInput.text?.toString().orEmpty()
            viewModel.sendMessage(text)
            binding.aiChatInput.setText("")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AiChatScreenState) {
        binding.aiChatTitleText.text = state.title
        adapter.submitList(state.messages)
        binding.aiChatSetupText.visibility = if (state.setupRequired) View.VISIBLE else View.GONE
        binding.aiChatProgress.visibility = if (state.isGenerating) View.VISIBLE else View.GONE
        binding.aiChatSendButton.isEnabled = !state.setupRequired && !state.isGenerating
        binding.aiChatInput.isEnabled = !state.setupRequired && !state.isGenerating
        if (state.messages.isNotEmpty()) {
            binding.aiChatRecyclerView.scrollToPosition(state.messages.lastIndex)
        }
    }

    override fun onDestroyView() {
        binding.aiChatRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
```

- [ ] **Step 6: Wire ViewModel factory and strings**

Add `AiChatViewModel` case to `VitaSenseViewModelFactory`:

```kotlin
modelClass.isAssignableFrom(AiChatViewModel::class.java) ->
    AiChatViewModel(
        aiChatRepository = appContainer.aiChatRepository,
        settingsRepository = appContainer.settingsRepository,
    ) as T
```

Add strings:

```xml
<string name="nav_ai_chat">AI</string>
<string name="ai_chat_title">AI Chat</string>
<string name="ai_chat_new">New</string>
<string name="ai_chat_delete">Delete</string>
<string name="ai_chat_input_hint">Ask about your current state</string>
<string name="ai_chat_send">Send</string>
<string name="ai_chat_setup_required">Set up AI provider settings before starting a chat.</string>
<string name="ai_chat_generating">Generating...</string>
```

- [ ] **Step 7: Run ViewModel test and assemble**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.aichat.AiChatViewModelTest"
./gradlew.bat :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 8: Commit chat UI**

```powershell
git add -- app/src/main/java/org/wit/vitasense/ui/aichat app/src/main/res/layout/fragment_ai_chat.xml app/src/main/res/layout/item_ai_chat_message.xml app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt app/src/main/res/values/strings.xml app/src/test/java/org/wit/vitasense/ui/aichat/AiChatViewModelTest.kt
git commit -m "feat: add AI chat screen"
```

---

### Task 6: Center AI Button, Navigation, and Verification

**Files:**
- Modify: `app/src/main/res/navigation/main_nav_graph.xml`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/navigation/FloatingTabShellDestinationPolicy.kt` only if a regression test requires explicit exclusion.
- Test: `app/src/androidTest/java/org/wit/vitasense/ui/AiChatNavigationSmokeTest.kt`

- [ ] **Step 1: Add navigation destination**

Modify `main_nav_graph.xml`:

```xml
<fragment
    android:id="@+id/aiChatFragment"
    android:name="org.wit.vitasense.ui.aichat.AiChatFragment"
    android:label="@string/ai_chat_title" />
```

Do not add this destination to `FloatingTabShellDestinationPolicy.topLevelDestinationIds`.

- [ ] **Step 2: Add center AI action to bottom tabs layout**

In `view_floating_bottom_tabs.xml`, insert a center `FrameLayout` between `tabTrends` and `tabMood`:

```xml
<FrameLayout
    android:id="@+id/tabAiChat"
    android:layout_width="0dp"
    android:layout_height="@dimen/vs_bottom_tab_item_height"
    android:layout_weight="1"
    android:contentDescription="@string/nav_ai_chat"
    android:foreground="?attr/selectableItemBackgroundBorderless">

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/tabAiChatButton"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="center"
        app:cardBackgroundColor="?attr/vsColorPrimaryStrong"
        app:cardCornerRadius="28dp"
        app:cardElevation="8dp"
        app:strokeWidth="0dp">

        <TextView
            android:id="@+id/tabAiChatLabel"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="@string/nav_ai_chat"
            android:textColor="?attr/colorOnPrimary"
            android:textSize="14sp"
            android:textStyle="bold" />
    </com.google.android.material.card.MaterialCardView>
</FrameLayout>
```

This creates a five-slot foreground layout but only four selectable destinations. The liquid indicator remains bound to the four destination tabs and ignores the AI action.

- [ ] **Step 3: Bind AI button in MainActivity**

In `bindFloatingTabs()` add:

```kotlin
findViewById<View>(R.id.tabAiChat).setOnClickListener {
    navController.navigate(R.id.aiChatFragment)
}
```

In `renderBottomTabs`, guard destination lookup so `BottomTabDestination.entries` remains the only tint loop. Do not add AI to `BottomTabDestination`.

- [ ] **Step 4: Write navigation smoke test**

Create `AiChatNavigationSmokeTest.kt`:

```kotlin
package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Test
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

class AiChatNavigationSmokeTest {
    @Test
    fun centerAiButtonOpensChatPage() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tabAiChat)).perform(click())
            onView(withId(R.id.aiChatTitleText)).check(matches(isDisplayed()))
        }
    }
}
```

- [ ] **Step 5: Run final verification**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleDebugAndroidTest
cd python_auth_api
python ai_chat_stream_test.py
```

Expected: all commands pass.

- [ ] **Step 6: Commit navigation and final integration**

```powershell
git add -- app/src/main/res/navigation/main_nav_graph.xml app/src/main/res/layout/view_floating_bottom_tabs.xml app/src/main/java/org/wit/vitasense/MainActivity.kt app/src/androidTest/java/org/wit/vitasense/ui/AiChatNavigationSmokeTest.kt
git commit -m "feat: add center AI chat entry"
```

---

## Self-Review Notes

Spec coverage:

- Center AI entry: Task 6.
- Chat page: Task 5.
- Local Room history: Task 2 and Task 4.
- Existing AI settings reuse: Task 4 and Task 5.
- Backend proxy with streaming: Task 1 and Task 3.
- Health context: Task 3 and Task 4.
- New chat/delete/history/input/send/loading/error states: Task 4, Task 5, and Task 6.
- No backend chat persistence: Task 1 keeps backend streaming-only.

Type consistency:

- Stream protocol uses `AiChatStreamEvent` across model, remote source, and repository.
- Message roles/statuses use `AiChatRole.storageKey` and `AiChatMessageStatus.storageKey`.
- `AppContainer.aiChatRepository` is consumed by `VitaSenseViewModelFactory`.

Verification:

- Android unit tests exercise model parsing, remote stream parsing, context building, repository streaming persistence, and ViewModel setup/send states.
- Android build verifies Room/KSP and ViewBinding.
- Backend script verifies request validation and simulated provider streaming.
