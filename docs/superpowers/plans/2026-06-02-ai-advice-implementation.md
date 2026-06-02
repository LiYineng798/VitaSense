# AI Advice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a manually-triggered, network-backed AI advice feature that uses user-provided AI API credentials and the latest VitaSense health data.

**Architecture:** Android stores AI configuration and the latest generated advice in Room-backed settings, exposes an AI Advice card on Home, and calls the existing Python service as a proxy. The Python service validates request data, calls DeepSeek or any OpenAI-compatible chat-completions provider, normalizes responses, and never persists user API keys.

**Tech Stack:** Kotlin, XML Views, ViewBinding, Room settings table, Coroutines/Flow, `HttpURLConnection`, FastAPI, SQLite for existing auth only, Python stdlib `urllib.request` for outbound provider calls.

---

## File Map

- Create `app/src/main/java/org/wit/vitasense/model/AiAdviceModels.kt`: Android model types, JSON serialization, provider defaults, and error-message mapping.
- Modify `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`: expose AI config and saved-advice settings.
- Modify `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`: persist AI config and saved advice through `AppSettingDao`.
- Create `app/src/main/java/org/wit/vitasense/repository/AiAdviceRepository.kt`: interface for generating advice.
- Create `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiAdviceRepository.kt`: backend proxy client.
- Modify `app/src/main/java/org/wit/vitasense/AppContainer.kt`: construct `AiAdviceRepository` and seed default AI proxy URL.
- Modify `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`: inject AI repository and settings repository into Dashboard.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeModels.kt`: add AI card state.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeUiMapper.kt`: map saved config/advice to Home state.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt`: add `generateAiAdvice()` with duplicate-click protection.
- Modify `app/src/main/res/layout/fragment_dashboard.xml`: add the AI Advice card.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`: render card states and handle button clicks.
- Modify `app/src/main/res/layout/fragment_settings.xml`: add AI Provider settings controls.
- Modify `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`: expose and save AI settings.
- Modify `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`: bind AI settings inputs.
- Modify `app/src/main/res/values/strings.xml`: add user-facing AI strings.
- Modify `python_auth_api/main.py`: add AI advice request/response models, provider call helper, endpoint, and error mapping.
- Modify `python_auth_api/smoke_test.py`: add local validation checks for AI endpoint.
- Create or modify Android unit tests under `app/src/test/java/org/wit/vitasense/...`.

---

### Task 1: Android AI Models And Settings Persistence

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/model/AiAdviceModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Modify: `app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing settings tests**

Add these tests to `DefaultSettingsRepositoryTest`:

```kotlin
@Test
fun persists_and_restores_ai_configuration() = runBlocking {
    val dao = FakeAppSettingDao()
    val repository = DefaultSettingsRepository(dao)
    val config = AiProviderConfig(
        provider = AiProvider.DEEPSEEK,
        apiKey = "sk-user",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-chat",
    )

    repository.setAiProviderConfig(config)

    assertEquals(config, repository.getAiProviderConfig())
    assertEquals(config, repository.observeAiProviderConfig().first())
    assertEquals("deepseek", dao.snapshot()["ai_provider"])
    assertEquals("sk-user", dao.snapshot()["ai_api_key"])
}

@Test
fun persists_and_restores_latest_ai_advice() = runBlocking {
    val dao = FakeAppSettingDao()
    val repository = DefaultSettingsRepository(dao)
    val advice = AiAdvice(
        summary = "Recovery looks stable.",
        recommendations = listOf("Keep training light.", "Prioritize sleep."),
        riskNote = "Sleep was slightly short.",
        disclaimer = "This is wellness support, not medical diagnosis.",
    )

    repository.setLatestAiAdvice(
        advice = advice,
        generatedAt = 1_779_999_000_000L,
    )

    assertEquals(advice, repository.getLatestAiAdvice())
    assertEquals(advice, repository.observeLatestAiAdvice().first())
    assertEquals(1_779_999_000_000L, repository.observeLatestAiAdviceGeneratedAt().first())
}
```

Add imports:

```kotlin
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest"
```

Expected: compile failure because `AiAdvice`, `AiProviderConfig`, and settings methods do not exist.

- [ ] **Step 3: Add AI model file**

Create `AiAdviceModels.kt`:

```kotlin
package org.wit.vitasense.model

import org.json.JSONArray
import org.json.JSONObject

enum class AiProvider(
    val storageKey: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    DEEPSEEK("deepseek", "https://api.deepseek.com", "deepseek-chat"),
    OPENAI_COMPATIBLE("openai_compatible", "", "");

    companion object {
        fun fromStorageKey(raw: String): AiProvider =
            entries.firstOrNull { it.storageKey == raw.lowercase() } ?: DEEPSEEK
    }
}

data class AiProviderConfig(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = AiProvider.DEEPSEEK.defaultBaseUrl,
    val model: String = AiProvider.DEEPSEEK.defaultModel,
) {
    val isComplete: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

data class AiAdvice(
    val summary: String,
    val recommendations: List<String>,
    val riskNote: String,
    val disclaimer: String,
)

sealed interface AiAdviceResult {
    data class Success(val advice: AiAdvice) : AiAdviceResult
    data class Error(val code: String, val message: String) : AiAdviceResult
}

fun AiAdvice.toStorageJson(): String =
    JSONObject()
        .put("summary", summary)
        .put("recommendations", JSONArray(recommendations))
        .put("risk_note", riskNote)
        .put("disclaimer", disclaimer)
        .toString()

fun parseStoredAiAdvice(raw: String): AiAdvice? =
    runCatching {
        val obj = JSONObject(raw)
        val recs = obj.optJSONArray("recommendations") ?: JSONArray()
        AiAdvice(
            summary = obj.optString("summary"),
            recommendations = (0 until recs.length()).mapNotNull { recs.optString(it).takeIf(String::isNotBlank) },
            riskNote = obj.optString("risk_note"),
            disclaimer = obj.optString("disclaimer"),
        )
    }.getOrNull()

fun aiErrorMessage(code: String): String =
    when (code) {
        "missing_api_key" -> "Add an API key in Settings first."
        "missing_model" -> "Add a model name in Settings first."
        "missing_base_url" -> "Add an AI base URL in Settings first."
        "proxy_unreachable" -> "Unable to reach the VitaSense AI proxy."
        "ai_network_error" -> "Unable to reach the AI service. Check your network or base URL."
        "invalid_api_key" -> "The API key is invalid or expired."
        "model_unavailable" -> "The selected model is not available. Check the model name."
        "quota_or_rate_limit" -> "The AI service quota or rate limit was reached."
        "unexpected_ai_response" -> "The AI service returned an unexpected response."
        else -> "Unable to generate AI advice right now."
    }
```

- [ ] **Step 4: Extend settings interfaces**

Add to `SettingsRepository`:

```kotlin
fun observeAiProviderConfig(): Flow<AiProviderConfig>
fun observeLatestAiAdvice(): Flow<AiAdvice?>
fun observeLatestAiAdviceGeneratedAt(): Flow<Long?>

suspend fun getAiProviderConfig(): AiProviderConfig
suspend fun getLatestAiAdvice(): AiAdvice?
suspend fun getLatestAiAdviceGeneratedAt(): Long?

suspend fun setAiProviderConfig(config: AiProviderConfig)
suspend fun setLatestAiAdvice(advice: AiAdvice, generatedAt: Long)
```

Add imports:

```kotlin
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
```

- [ ] **Step 5: Implement settings persistence**

Add imports to `DefaultSettingsRepository`:

```kotlin
import kotlinx.coroutines.flow.combine
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.parseStoredAiAdvice
import org.wit.vitasense.model.toStorageJson
```

Add methods:

```kotlin
override fun observeAiProviderConfig(): Flow<AiProviderConfig> =
    combine(
        appSettingDao.observe(AI_PROVIDER_KEY),
        appSettingDao.observe(AI_API_KEY),
        appSettingDao.observe(AI_BASE_URL_KEY),
        appSettingDao.observe(AI_MODEL_KEY),
    ) { providerEntity, apiKeyEntity, baseUrlEntity, modelEntity ->
        val provider = AiProvider.fromStorageKey(providerEntity?.value.orEmpty())
        AiProviderConfig(
            provider = provider,
            apiKey = apiKeyEntity?.value.orEmpty(),
            baseUrl = baseUrlEntity?.value?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            model = modelEntity?.value?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
        )
    }

override fun observeLatestAiAdvice(): Flow<AiAdvice?> =
    appSettingDao.observe(AI_LATEST_ADVICE_JSON_KEY).map { parseStoredAiAdvice(it?.value.orEmpty()) }

override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> =
    appSettingDao.observe(AI_LATEST_ADVICE_GENERATED_AT_KEY).map { it?.value?.toLongOrNull() }

override suspend fun getAiProviderConfig(): AiProviderConfig {
    val provider = AiProvider.fromStorageKey(appSettingDao.get(AI_PROVIDER_KEY)?.value.orEmpty())
    return AiProviderConfig(
        provider = provider,
        apiKey = appSettingDao.get(AI_API_KEY)?.value.orEmpty(),
        baseUrl = appSettingDao.get(AI_BASE_URL_KEY)?.value?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
        model = appSettingDao.get(AI_MODEL_KEY)?.value?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
    )
}

override suspend fun getLatestAiAdvice(): AiAdvice? =
    parseStoredAiAdvice(appSettingDao.get(AI_LATEST_ADVICE_JSON_KEY)?.value.orEmpty())

override suspend fun getLatestAiAdviceGeneratedAt(): Long? =
    appSettingDao.get(AI_LATEST_ADVICE_GENERATED_AT_KEY)?.value?.toLongOrNull()

override suspend fun setAiProviderConfig(config: AiProviderConfig) {
    appSettingDao.upsert(AppSettingEntity(AI_PROVIDER_KEY, config.provider.storageKey))
    appSettingDao.upsert(AppSettingEntity(AI_API_KEY, config.apiKey.trim()))
    appSettingDao.upsert(AppSettingEntity(AI_BASE_URL_KEY, config.baseUrl.trim().removeSuffix("/")))
    appSettingDao.upsert(AppSettingEntity(AI_MODEL_KEY, config.model.trim()))
}

override suspend fun setLatestAiAdvice(advice: AiAdvice, generatedAt: Long) {
    appSettingDao.upsert(AppSettingEntity(AI_LATEST_ADVICE_JSON_KEY, advice.toStorageJson()))
    appSettingDao.upsert(AppSettingEntity(AI_LATEST_ADVICE_GENERATED_AT_KEY, generatedAt.toString()))
}
```

Add constants:

```kotlin
const val AI_PROVIDER_KEY = "ai_provider"
const val AI_API_KEY = "ai_api_key"
const val AI_BASE_URL_KEY = "ai_base_url"
const val AI_MODEL_KEY = "ai_model"
const val AI_LATEST_ADVICE_JSON_KEY = "ai_latest_advice_json"
const val AI_LATEST_ADVICE_GENERATED_AT_KEY = "ai_latest_advice_generated_at"
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest"
```

Expected: PASS.

Commit:

```powershell
git add -- app/src/main/java/org/wit/vitasense/model/AiAdviceModels.kt app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt
git commit -m "Add AI advice settings persistence"
```

---

### Task 2: Python AI Proxy Endpoint

**Files:**
- Modify: `python_auth_api/main.py`
- Modify: `python_auth_api/smoke_test.py`

- [ ] **Step 1: Add validation and mapping checks to smoke test**

Append to `smoke_test.py`:

```python
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
```

Call `run_ai_validation_checks()` at the end of `main()`.

- [ ] **Step 2: Run smoke test against current server and confirm failure**

Start the server in one terminal:

```powershell
cd python_auth_api
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Run in another terminal:

```powershell
cd python_auth_api
python smoke_test.py
```

Expected: FAIL with 404 for `/api/v1/ai/advice`.

- [ ] **Step 3: Add endpoint models and helpers to `main.py`**

Add imports:

```python
import json
import urllib.error
import urllib.request
from typing import Any
```

Add models:

```python
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
```

Add helpers:

```python
def ai_error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"success": False, "code": code, "message": message},
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
```

- [ ] **Step 4: Add endpoint**

Add to `main.py`:

```python
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

    return {"success": True, "advice": advice}
```

- [ ] **Step 5: Run smoke test and commit**

Run:

```powershell
cd python_auth_api
python smoke_test.py
```

Expected: PASS for auth checks and AI validation checks.

Commit:

```powershell
git add -- python_auth_api/main.py python_auth_api/smoke_test.py
git commit -m "Add AI advice proxy endpoint"
```

---

### Task 3: Android AI Advice Proxy Client

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/repository/AiAdviceRepository.kt`
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAiAdviceRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Create: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAiAdviceRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

Create `DefaultAiAdviceRepositoryTest.kt`:

```kotlin
package org.wit.vitasense.data.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.AiHealthSummary

class DefaultAiAdviceRepositoryTest {
    @Test
    fun maps_successful_proxy_response_to_advice() = runBlocking {
        val repository = DefaultAiAdviceRepository(
            proxyBaseUrl = "https://server.example",
            connectionFactory = FakeAiConnectionFactory(
                code = 200,
                body = """{"success":true,"advice":{"summary":"Stable","recommendations":["Rest"],"risk_note":"Low risk","disclaimer":"Not diagnosis"}}""",
            ),
        )

        val result = repository.generateAdvice(config(), summary())

        val success = result as AiAdviceResult.Success
        assertEquals("Stable", success.advice.summary)
        assertEquals(listOf("Rest"), success.advice.recommendations)
    }

    @Test
    fun maps_proxy_error_code_to_error_result() = runBlocking {
        val repository = DefaultAiAdviceRepository(
            proxyBaseUrl = "https://server.example",
            connectionFactory = FakeAiConnectionFactory(
                code = 401,
                body = """{"success":false,"code":"invalid_api_key","message":"bad key"}""",
            ),
        )

        val result = repository.generateAdvice(config(), summary())

        val error = result as AiAdviceResult.Error
        assertEquals("invalid_api_key", error.code)
    }

    private fun config() = AiProviderConfig(
        provider = AiProvider.DEEPSEEK,
        apiKey = "sk-test",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-chat",
    )

    private fun summary() = AiHealthSummary(
        date = "2026-06-02",
        totalScore = 82,
        riskLevel = "low",
        sleepMinutes = 430,
        rmssd = 35.0,
        restingHeartRate = 61.0,
        avgHeartRate = 65.0,
        anomalyFlags = emptyList(),
        ruleSuggestion = "Keep the current pace.",
    )
}
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiAdviceRepositoryTest"
```

Expected: compile failure because `DefaultAiAdviceRepository`, `AiHealthSummary`, and fake connection types do not exist.

- [ ] **Step 3: Add repository interface and model**

Add to `AiAdviceModels.kt`:

```kotlin
data class AiHealthSummary(
    val date: String,
    val totalScore: Int?,
    val riskLevel: String?,
    val sleepMinutes: Int?,
    val rmssd: Double?,
    val restingHeartRate: Double?,
    val avgHeartRate: Double?,
    val anomalyFlags: List<String>,
    val ruleSuggestion: String?,
)
```

Create `AiAdviceRepository.kt`:

```kotlin
package org.wit.vitasense.repository

import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProviderConfig

interface AiAdviceRepository {
    suspend fun generateAdvice(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): AiAdviceResult
}
```

- [ ] **Step 4: Implement proxy client**

Create `DefaultAiAdviceRepository.kt`:

```kotlin
package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.repository.AiAdviceRepository

interface AiConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

object DefaultAiConnectionFactory : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = url.openConnection() as HttpURLConnection
}

class DefaultAiAdviceRepository(
    private val proxyBaseUrl: String,
    private val connectionFactory: AiConnectionFactory = DefaultAiConnectionFactory,
) : AiAdviceRepository {
    override suspend fun generateAdvice(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): AiAdviceResult = withContext(Dispatchers.IO) {
        try {
            val response = execute(config, summary)
            parseResponse(response.body)
        } catch (_: IOException) {
            AiAdviceResult.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy.")
        } catch (_: SecurityException) {
            AiAdviceResult.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy.")
        } catch (_: Exception) {
            AiAdviceResult.Error("unexpected_ai_response", "The AI service returned an unexpected response.")
        }
    }

    private fun execute(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): HttpResponse {
        val connection = connectionFactory.open(URL(proxyBaseUrl.trim().removeSuffix("/") + "/api/v1/ai/advice"))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(buildPayload(config, summary).toString()) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(config: AiProviderConfig, summary: AiHealthSummary): JSONObject =
        JSONObject()
            .put("provider", config.provider.storageKey)
            .put("base_url", config.baseUrl)
            .put("model", config.model)
            .put("api_key", config.apiKey)
            .put(
                "health_summary",
                JSONObject()
                    .put("date", summary.date)
                    .put("total_score", summary.totalScore)
                    .put("risk_level", summary.riskLevel)
                    .put("sleep_minutes", summary.sleepMinutes)
                    .put("rmssd", summary.rmssd)
                    .put("resting_heart_rate", summary.restingHeartRate)
                    .put("avg_heart_rate", summary.avgHeartRate)
                    .put("anomaly_flags", JSONArray(summary.anomalyFlags))
                    .put("rule_suggestion", summary.ruleSuggestion),
            )

    private fun parseResponse(raw: String): AiAdviceResult {
        val obj = JSONObject(raw.ifBlank { "{}" })
        if (!obj.optBoolean("success", false)) {
            val code = obj.optString("code", "unexpected_ai_response")
            return AiAdviceResult.Error(code, obj.optString("message"))
        }
        val advice = obj.getJSONObject("advice")
        val recs = advice.optJSONArray("recommendations") ?: JSONArray()
        return AiAdviceResult.Success(
            AiAdvice(
                summary = advice.optString("summary"),
                recommendations = (0 until recs.length()).mapNotNull { recs.optString(it).takeIf(String::isNotBlank) },
                riskNote = advice.optString("risk_note"),
                disclaimer = advice.optString("disclaimer"),
            ),
        )
    }

    private data class HttpResponse(val code: Int, val body: String)
}
```

Add `FakeAiConnectionFactory` and fake connection implementation to the test file:

```kotlin
private class FakeAiConnectionFactory(
    private val code: Int,
    private val body: String,
) : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = FakeConnection(url, code, body)
}

private class FakeConnection(
    url: URL,
    private val code: Int,
    private val body: String,
) : HttpURLConnection(url) {
    val requestBuffer = java.io.ByteArrayOutputStream()
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
    override fun getOutputStream() = requestBuffer
    override fun getResponseCode(): Int = code
    override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream(): InputStream = ByteArrayInputStream(body.toByteArray())
}
```

- [ ] **Step 5: Wire repository into container**

Add to `AppContainer`:

```kotlin
const val DEFAULT_AI_PROXY_BASE_URL = "https://server.np5.top"
```

Add property:

```kotlin
val aiAdviceRepository: AiAdviceRepository by lazy {
    DefaultAiAdviceRepository(proxyBaseUrl = DEFAULT_AI_PROXY_BASE_URL)
}
```

Add imports:

```kotlin
import org.wit.vitasense.repository.AiAdviceRepository
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.data.repository.DefaultAiAdviceRepositoryTest"
```

Expected: PASS.

Commit:

```powershell
git add -- app/src/main/java/org/wit/vitasense/model/AiAdviceModels.kt app/src/main/java/org/wit/vitasense/repository/AiAdviceRepository.kt app/src/main/java/org/wit/vitasense/data/repository/DefaultAiAdviceRepository.kt app/src/main/java/org/wit/vitasense/AppContainer.kt app/src/test/java/org/wit/vitasense/data/repository/DefaultAiAdviceRepositoryTest.kt
git commit -m "Add Android AI advice proxy client"
```

---

### Task 4: Dashboard AI Advice State And Manual Generation

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeUiMapper.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/dashboard/DashboardViewModelTest.kt`

- [ ] **Step 1: Add failing Dashboard tests**

Add tests that verify:

```kotlin
@Test
fun ai_card_requires_settings_when_api_key_is_missing() = runBlocking {
    val settings = FakeSettingsRepository()
    val viewModel = DashboardViewModel(
        healthRepository = FakeHealthRepository(),
        authRepository = FakeAuthRepository(null),
        settingsRepository = settings,
        aiAdviceRepository = FakeAiAdviceRepository(),
        scope = CoroutineScope(Job() + Dispatchers.Unconfined),
    )

    assertEquals("Set up AI advice in Settings.", viewModel.state.value.aiAdvice.statusText)
    assertEquals("Set up", viewModel.state.value.aiAdvice.actionText)
}

@Test
fun generate_ai_advice_ignores_duplicate_click_while_loading() = runBlocking {
    val health = FakeHealthRepository()
    health.summaries.value = listOf(summary("2026-06-02", 430, 35.0, 65.0))
    health.latestRisk.value = risk(82)
    val settings = FakeSettingsRepository()
    settings.aiConfig.value = AiProviderConfig(apiKey = "sk-test")
    val adviceRepository = FakeAiAdviceRepository(delayUntilReleased = true)
    val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    val viewModel = DashboardViewModel(health, FakeAuthRepository(null), settings, adviceRepository, scope)

    val first = scope.launch { viewModel.generateAiAdvice() }
    viewModel.generateAiAdvice()

    assertEquals(1, adviceRepository.calls)
    adviceRepository.release()
    first.join()
    scope.coroutineContext[Job]?.cancel()
}
```

Extend fake repositories with AI settings and fake AI calls as needed.

- [ ] **Step 2: Run Dashboard test and confirm failure**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.dashboard.DashboardViewModelTest"
```

Expected: compile failure because Dashboard constructor and AI state do not exist.

- [ ] **Step 3: Add Dashboard AI state models**

Add to `DashboardHomeModels.kt`:

```kotlin
data class DashboardAiAdviceState(
    val title: String = "AI Advice",
    val statusText: String = "Set up AI advice in Settings.",
    val summary: String = "",
    val recommendations: List<String> = emptyList(),
    val riskNote: String = "",
    val disclaimer: String = "",
    val actionText: String = "Set up",
    val showProgress: Boolean = false,
    val canGenerate: Boolean = false,
    val shouldOpenSettings: Boolean = true,
    val errorText: String? = null,
)
```

Add `val aiAdvice: DashboardAiAdviceState = DashboardAiAdviceState()` to `DashboardScreenState`.

- [ ] **Step 4: Extend mapper**

Change `DashboardHomeUiMapper.build` signature to accept:

```kotlin
aiConfig: AiProviderConfig,
latestAiAdvice: AiAdvice?,
latestAiAdviceGeneratedAt: Long?,
isAiLoading: Boolean,
aiErrorText: String?,
```

Add helper:

```kotlin
private fun buildAiState(
    aiConfig: AiProviderConfig,
    latestAiAdvice: AiAdvice?,
    isAiLoading: Boolean,
    aiErrorText: String?,
): DashboardAiAdviceState =
    when {
        aiConfig.apiKey.isBlank() -> DashboardAiAdviceState()
        isAiLoading -> DashboardAiAdviceState(
            statusText = "Generating personalized advice...",
            actionText = "Generating...",
            showProgress = true,
            canGenerate = false,
            shouldOpenSettings = false,
        )
        latestAiAdvice != null -> DashboardAiAdviceState(
            statusText = "Latest generated advice",
            summary = latestAiAdvice.summary,
            recommendations = latestAiAdvice.recommendations,
            riskNote = latestAiAdvice.riskNote,
            disclaimer = latestAiAdvice.disclaimer,
            actionText = "Refresh advice",
            canGenerate = true,
            shouldOpenSettings = false,
            errorText = aiErrorText,
        )
        else -> DashboardAiAdviceState(
            statusText = "Generate advice from today's health data.",
            actionText = "Generate advice",
            canGenerate = true,
            shouldOpenSettings = false,
            errorText = aiErrorText,
        )
    }
```

Pass the resulting AI state into every `DashboardScreenState` construction.

- [ ] **Step 5: Extend DashboardViewModel**

Change constructor:

```kotlin
class DashboardViewModel(
    private val healthRepository: HealthRepository,
    authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val aiAdviceRepository: AiAdviceRepository,
    scope: CoroutineScope? = null,
) : ViewModel()
```

Add private state:

```kotlin
private val aiLoading = MutableStateFlow(false)
private val aiErrorText = MutableStateFlow<String?>(null)
```

Combine:

```kotlin
combine(
    healthRepository.observeSummaries(7),
    healthRepository.observeLatestRisk(),
    authRepository.observeCurrentUser(),
    settingsRepository.observeAiProviderConfig(),
    settingsRepository.observeLatestAiAdvice(),
    settingsRepository.observeLatestAiAdviceGeneratedAt(),
    aiLoading,
    aiErrorText,
) { values -> ... }
```

In the lambda, cast values in order and call `DashboardHomeUiMapper.build`.

Add action:

```kotlin
fun generateAiAdvice() {
    if (aiLoading.value) return
    modelScope.launch {
        val config = settingsRepository.getAiProviderConfig()
        if (!config.isComplete) {
            aiErrorText.value = aiErrorMessage(
                when {
                    config.apiKey.isBlank() -> "missing_api_key"
                    config.baseUrl.isBlank() -> "missing_base_url"
                    else -> "missing_model"
                },
            )
            return@launch
        }
        val summaries = healthRepository.observeSummaries(7).first()
        val latestSummary = summaries.maxByOrNull { it.date }
        val latestRisk = healthRepository.observeLatestRisk().first()
        if (latestSummary == null) {
            aiErrorText.value = "Import health data before generating AI advice."
            return@launch
        }
        aiLoading.value = true
        aiErrorText.value = null
        val result = aiAdviceRepository.generateAdvice(
            config = config,
            summary = AiHealthSummary(
                date = latestSummary.date,
                totalScore = latestRisk?.totalScore,
                riskLevel = latestRisk?.riskLevel,
                sleepMinutes = latestSummary.sleepDurationMinutes,
                rmssd = latestSummary.rmssd,
                restingHeartRate = latestSummary.restingHeartRate,
                avgHeartRate = latestSummary.avgHeartRate,
                anomalyFlags = latestSummary.anomalyFlags.split("|").filter { it.isNotBlank() },
                ruleSuggestion = latestRisk?.suggestionText,
            ),
        )
        when (result) {
            is AiAdviceResult.Success ->
                settingsRepository.setLatestAiAdvice(result.advice, System.currentTimeMillis())
            is AiAdviceResult.Error ->
                aiErrorText.value = aiErrorMessage(result.code)
        }
        aiLoading.value = false
    }
}
```

Add imports for `first`, `MutableStateFlow`, AI models, repositories, and `aiErrorMessage`.

- [ ] **Step 6: Inject dependencies and run tests**

Modify factory Dashboard creation:

```kotlin
DashboardViewModel(
    healthRepository = appContainer.healthRepository,
    authRepository = appContainer.authRepository,
    settingsRepository = appContainer.settingsRepository,
    aiAdviceRepository = appContainer.aiAdviceRepository,
) as T
```

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.dashboard.DashboardViewModelTest"
```

Expected: PASS.

Commit:

```powershell
git add -- app/src/main/java/org/wit/vitasense/ui/dashboard app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt app/src/test/java/org/wit/vitasense/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Add dashboard AI advice state"
```

---

### Task 5: Android Home And Settings UI

**Files:**
- Modify: `app/src/main/res/layout/fragment_dashboard.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add failing Settings ViewModel test**

Add:

```kotlin
@Test
fun persists_ai_provider_settings() = runBlocking {
    val repository = FakeSettingsRepository()
    val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    val viewModel = SettingsViewModel(FakeHealthRepository(), repository, scope)

    viewModel.saveAiSettings(
        provider = AiProvider.OPENAI_COMPATIBLE,
        apiKey = "sk-custom",
        baseUrl = "https://api.example.com/v1",
        model = "custom-model",
    )
    yield()

    assertEquals(AiProvider.OPENAI_COMPATIBLE, repository.aiConfig.value.provider)
    assertEquals("sk-custom", repository.aiConfig.value.apiKey)
}
```

- [ ] **Step 2: Run Settings test and confirm failure**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.settings.SettingsViewModelTest"
```

Expected: compile failure because `saveAiSettings` and fake AI settings methods do not exist.

- [ ] **Step 3: Add strings**

Add to `strings.xml`:

```xml
<string name="dashboard_ai_advice_title">AI Advice</string>
<string name="dashboard_ai_advice_setup">Set up AI advice in Settings.</string>
<string name="dashboard_ai_advice_generate">Generate advice</string>
<string name="dashboard_ai_advice_refresh">Refresh advice</string>
<string name="dashboard_ai_advice_generating">Generating...</string>
<string name="settings_ai_section">AI Provider</string>
<string name="settings_ai_provider_deepseek">DeepSeek</string>
<string name="settings_ai_provider_custom">Custom OpenAI-compatible</string>
<string name="settings_ai_api_key">API Key</string>
<string name="settings_ai_base_url">Base URL</string>
<string name="settings_ai_model">Model</string>
<string name="settings_ai_save">Save AI Settings</string>
<string name="settings_ai_note">AI usage may consume your provider quota. Your API key is sent to the VitaSense backend only for the current request.</string>
```

- [ ] **Step 4: Add Dashboard AI card layout**

Insert a `MaterialCardView` in `fragment_dashboard.xml` before `quickMoodButton` with IDs:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/aiAdviceCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="24dp"
    app:strokeColor="?attr/colorOutline"
    app:strokeWidth="1dp">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">
        <TextView
            android:id="@+id/aiAdviceTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/dashboard_ai_advice_title"
            android:textColor="?android:attr/textColorPrimary"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/aiAdviceStatusText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="?android:attr/textColorSecondary" />
        <TextView
            android:id="@+id/aiAdviceSummaryText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="?android:attr/textColorPrimary" />
        <ProgressBar
            android:id="@+id/aiAdviceProgress"
            style="?android:attr/progressBarStyleSmall"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:visibility="gone" />
        <com.google.android.material.button.MaterialButton
            android:id="@+id/aiAdviceActionButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 5: Render Dashboard AI card**

In `DashboardFragment`, inside state collection:

```kotlin
val ai = state.aiAdvice
binding.aiAdviceStatusText.text = ai.errorText ?: ai.statusText
binding.aiAdviceSummaryText.text =
    buildString {
        if (ai.summary.isNotBlank()) append(ai.summary)
        if (ai.recommendations.isNotEmpty()) {
            if (isNotBlank()) append("\n\n")
            ai.recommendations.forEach { append("- ").append(it).append("\n") }
        }
        if (ai.disclaimer.isNotBlank()) append("\n").append(ai.disclaimer)
    }.trim()
binding.aiAdviceSummaryText.visibility =
    if (binding.aiAdviceSummaryText.text.isBlank()) View.GONE else View.VISIBLE
binding.aiAdviceProgress.visibility = if (ai.showProgress) View.VISIBLE else View.GONE
binding.aiAdviceActionButton.text = ai.actionText
binding.aiAdviceActionButton.isEnabled = ai.canGenerate || ai.shouldOpenSettings
binding.aiAdviceActionButton.setOnClickListener {
    if (ai.shouldOpenSettings) {
        findNavController().navigate(R.id.settingsFragment)
    } else {
        viewModel.generateAiAdvice()
    }
}
```

- [ ] **Step 6: Add Settings AI controls and binding**

Add controls to `fragment_settings.xml` after the Import card:

```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="20dp">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="18dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/settings_ai_section"
            android:textColor="?android:attr/textColorPrimary"
            android:textStyle="bold" />
        <RadioGroup
            android:id="@+id/aiProviderGroup"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">
            <RadioButton
                android:id="@+id/aiProviderDeepSeek"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_ai_provider_deepseek" />
            <RadioButton
                android:id="@+id/aiProviderCustom"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_ai_provider_custom" />
        </RadioGroup>
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/settings_ai_api_key">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/aiApiKeyInput"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="@string/settings_ai_base_url">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/aiBaseUrlInput"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="@string/settings_ai_model">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/aiModelInput"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </com.google.android.material.textfield.TextInputLayout>
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/settings_ai_note"
            android:textColor="?android:attr/textColorSecondary" />
        <com.google.android.material.button.MaterialButton
            android:id="@+id/saveAiSettingsButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/settings_ai_save" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

Add to `SettingsViewModel`:

```kotlin
val aiConfig: StateFlow<AiProviderConfig> =
    settingsRepository.observeAiProviderConfig()
        .stateIn(modelScope, SharingStarted.WhileSubscribed(5_000), AiProviderConfig())

fun saveAiSettings(provider: AiProvider, apiKey: String, baseUrl: String, model: String) {
    modelScope.launch {
        val normalized = AiProviderConfig(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl },
            model = model.ifBlank { provider.defaultModel },
        )
        settingsRepository.setAiProviderConfig(normalized)
        _events.emit(UiEvent.Message("AI settings saved."))
    }
}
```

Bind in `SettingsFragment`:

```kotlin
binding.saveAiSettingsButton.setOnClickListener {
    val provider =
        if (binding.aiProviderCustom.isChecked) AiProvider.OPENAI_COMPATIBLE else AiProvider.DEEPSEEK
    viewModel.saveAiSettings(
        provider = provider,
        apiKey = binding.aiApiKeyInput.text?.toString().orEmpty(),
        baseUrl = binding.aiBaseUrlInput.text?.toString().orEmpty(),
        model = binding.aiModelInput.text?.toString().orEmpty(),
    )
}
```

Also collect `viewModel.aiConfig` and populate the fields once per state update.

- [ ] **Step 7: Run UI-adjacent tests and commit**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "org.wit.vitasense.ui.settings.SettingsViewModelTest" --tests "org.wit.vitasense.ui.dashboard.DashboardViewModelTest"
```

Expected: PASS.

Commit:

```powershell
git add -- app/src/main/res/layout/fragment_dashboard.xml app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt app/src/main/res/layout/fragment_settings.xml app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt app/src/main/java/org/wit/vitasense/ui/settings/SettingsFragment.kt app/src/main/res/values/strings.xml app/src/test/java/org/wit/vitasense/ui/settings/SettingsViewModelTest.kt
git commit -m "Add AI advice Home and Settings UI"
```

---

### Task 6: Full Verification And Polish

**Files:**
- Modify only files already touched if verification exposes issues.

- [ ] **Step 1: Run full Android unit tests**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build Android debug APK**

Run:

```powershell
./gradlew.bat :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 3: Run Python smoke test**

Start server:

```powershell
cd python_auth_api
python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Run smoke test:

```powershell
cd python_auth_api
python smoke_test.py
```

Expected: PASS.

- [ ] **Step 4: Manual behavior checks**

Check these flows on emulator or device:

- Home with no API key shows setup state and navigates to Settings.
- Save DeepSeek config in Settings.
- Home shows `Generate advice`.
- Tap `Generate advice`; button disables and progress appears.
- Invalid API key returns key-specific error.
- Invalid model returns model-specific error.
- Returning to Home after successful generation shows saved advice without calling AI again.

- [ ] **Step 5: Commit verification fixes if any**

If any changes were required:

```powershell
git add -- <changed-files>
git commit -m "Polish AI advice verification issues"
```

If no changes were required, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Home AI Advice card: Task 5.
- Settings provider config: Tasks 1 and 5.
- Python backend endpoint: Task 2.
- DeepSeek and custom OpenAI-compatible support: Tasks 1, 2, and 5.
- Save latest successful advice locally: Tasks 1 and 4.
- Manual-only generation and duplicate-click protection: Task 4.
- Loading state and specific error feedback: Tasks 2, 3, 4, and 5.
- Tests and manual verification: Tasks 1 through 6.

No hard daily quota is implemented, matching the first-version scope. The plan keeps MiniMax-specific support out of the first implementation, matching the approved design.
