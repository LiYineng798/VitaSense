package org.wit.vitasense.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepositoryTest {
    @Test
    fun defaults_to_default_family_when_key_is_missing() = runBlocking {
        val dao = FakeAppSettingDao(mapOf("theme_mode" to "dark"))
        val repository = DefaultSettingsRepository(dao)

        assertEquals(ThemeFamily.DEFAULT, repository.getThemeFamily())
        assertEquals(ThemeMode.DARK, repository.getThemeMode())
        assertEquals(ThemeFamily.DEFAULT, repository.observeThemeFamily().first())
    }

    @Test
    fun persists_family_value_as_lowercase_keyed_setting() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setThemeFamily(ThemeFamily.ROSE_INDIGO)

        assertEquals("rose_indigo", dao.snapshot()["theme_family"])
    }

    @Test
    fun restores_rose_indigo_family_from_saved_setting() = runBlocking {
        val dao = FakeAppSettingDao(mapOf("theme_family" to "rose_indigo"))
        val repository = DefaultSettingsRepository(dao)

        assertEquals(ThemeFamily.ROSE_INDIGO, repository.getThemeFamily())
        assertEquals(ThemeFamily.ROSE_INDIGO, repository.observeThemeFamily().first())
    }

    @Test
    fun persists_and_restores_auth_base_url() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setAuthBaseUrl("https://example.com/api")

        assertEquals("https://example.com/api", repository.getAuthBaseUrl())
        assertEquals("https://example.com/api", repository.observeAuthBaseUrl().first())
        assertEquals("https://example.com/api", dao.snapshot()["auth_base_url"])
    }

    @Test
    fun persists_and_clears_current_user_id() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setCurrentUserId(42L)
        assertEquals(42L, repository.getCurrentUserId())
        assertEquals(42L, repository.observeCurrentUserId().first())
        assertEquals("42", dao.snapshot()["current_user_id"])

        repository.setCurrentUserId(null)
        assertEquals(null, repository.getCurrentUserId())
        assertEquals(null, repository.observeCurrentUserId().first())
        assertEquals("", dao.snapshot()["current_user_id"])
    }

    @Test
    fun persists_and_clears_auth_token_and_current_user_json() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setAuthToken("token-123")
        repository.setCurrentUserJson("""{"id":1}""")

        assertEquals("token-123", repository.getAuthToken())
        assertEquals("token-123", repository.observeAuthToken().first())
        assertEquals("""{"id":1}""", repository.getCurrentUserJson())
        assertEquals("""{"id":1}""", repository.observeCurrentUserJson().first())

        repository.setAuthToken(null)
        repository.setCurrentUserJson(null)

        assertEquals("", repository.getAuthToken())
        assertEquals("", repository.observeAuthToken().first())
        assertEquals("", repository.getCurrentUserJson())
        assertEquals("", repository.observeCurrentUserJson().first())
    }

    @Test
    fun persists_and_restores_ai_configuration() = runBlocking {
        val dao = FakeAppSettingDao()
        val repository = DefaultSettingsRepository(dao)
        val config =
            AiProviderConfig(
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
        val advice =
            AiAdvice(
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
}

private class FakeAppSettingDao(
    seed: Map<String, String> = emptyMap(),
) : AppSettingDao {
    private val values = seed.toMutableMap()
    private val flows =
        values
            .mapValues<String, String, MutableStateFlow<AppSettingEntity?>> {
                MutableStateFlow(AppSettingEntity(it.key, it.value))
            }.toMutableMap()

    override suspend fun upsert(setting: AppSettingEntity) {
        values[setting.key] = setting.value
        flows.getOrPut(setting.key) { MutableStateFlow(setting) }.value = setting
    }

    override fun observe(key: String) =
        flows.getOrPut(key) { MutableStateFlow<AppSettingEntity?>(null) }

    override suspend fun get(key: String): AppSettingEntity? =
        values[key]?.let { AppSettingEntity(key, it) }

    fun snapshot(): Map<String, String> = values.toMap()
}
