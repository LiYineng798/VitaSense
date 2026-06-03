package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.parseStoredAiAdvice
import org.wit.vitasense.model.toStorageJson
import org.wit.vitasense.repository.SettingsRepository

class DefaultSettingsRepository(
    private val appSettingDao: AppSettingDao,
) : SettingsRepository {
    override fun observeThemeMode(): Flow<ThemeMode> =
        appSettingDao.observe(THEME_KEY)
            .map { entity ->
                when (entity?.value?.lowercase()) {
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.LIGHT
                }
            }

    override fun observeThemeFamily(): Flow<ThemeFamily> =
        appSettingDao.observe(THEME_FAMILY_KEY)
            .map { entity ->
                when (entity?.value?.lowercase()) {
                    "olive_ember" -> ThemeFamily.OLIVE_EMBER
                    "sunlit_meadow" -> ThemeFamily.SUNLIT_MEADOW
                    "rose_indigo" -> ThemeFamily.ROSE_INDIGO
                    else -> ThemeFamily.DEFAULT
                }
            }

    override fun observeAuthBaseUrl(): Flow<String> =
        appSettingDao.observe(AUTH_BASE_URL_KEY)
            .map { entity -> entity?.value.orEmpty() }

    override fun observeAuthToken(): Flow<String> =
        appSettingDao.observe(AUTH_TOKEN_KEY)
            .map { entity -> entity?.value.orEmpty() }

    override fun observeCurrentUserJson(): Flow<String> =
        appSettingDao.observe(CURRENT_USER_JSON_KEY)
            .map { entity -> entity?.value.orEmpty() }

    override fun observeCurrentUserId(): Flow<Long?> =
        appSettingDao.observe(CURRENT_USER_ID_KEY)
            .map { entity -> entity?.value?.toLongOrNull() }

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
        appSettingDao.observe(AI_LATEST_ADVICE_JSON_KEY)
            .map { entity -> parseStoredAiAdvice(entity?.value.orEmpty()) }

    override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> =
        appSettingDao.observe(AI_LATEST_ADVICE_GENERATED_AT_KEY)
            .map { entity -> entity?.value?.toLongOrNull() }

    override fun observeLastSyncAt(): Flow<Long?> =
        appSettingDao.observe(KEY_LAST_SYNC_AT)
            .map { entity -> entity?.value?.toLongOrNull() }

    override fun observeSyncStatus(): Flow<String> =
        appSettingDao.observe(KEY_SYNC_STATUS)
            .map { entity -> entity?.value?.takeIf { it.isNotBlank() } ?: "idle" }

    override fun observeSyncError(): Flow<String> =
        appSettingDao.observe(KEY_SYNC_ERROR)
            .map { entity -> entity?.value.orEmpty() }

    override suspend fun getThemeMode(): ThemeMode =
        when (appSettingDao.get(THEME_KEY)?.value?.lowercase()) {
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }

    override suspend fun getThemeFamily(): ThemeFamily =
        when (appSettingDao.get(THEME_FAMILY_KEY)?.value?.lowercase()) {
            "olive_ember" -> ThemeFamily.OLIVE_EMBER
            "sunlit_meadow" -> ThemeFamily.SUNLIT_MEADOW
            "rose_indigo" -> ThemeFamily.ROSE_INDIGO
            else -> ThemeFamily.DEFAULT
        }

    override suspend fun getAuthBaseUrl(): String =
        appSettingDao.get(AUTH_BASE_URL_KEY)?.value.orEmpty()

    override suspend fun getAuthToken(): String =
        appSettingDao.get(AUTH_TOKEN_KEY)?.value.orEmpty()

    override suspend fun getCurrentUserJson(): String =
        appSettingDao.get(CURRENT_USER_JSON_KEY)?.value.orEmpty()

    override suspend fun getCurrentUserId(): Long? =
        appSettingDao.get(CURRENT_USER_ID_KEY)?.value?.toLongOrNull()

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

    override suspend fun getLastSyncAt(): Long? =
        appSettingDao.get(KEY_LAST_SYNC_AT)?.value?.toLongOrNull()

    override suspend fun getSyncStatus(): String =
        appSettingDao.get(KEY_SYNC_STATUS)?.value?.takeIf { it.isNotBlank() } ?: "idle"

    override suspend fun getSyncError(): String =
        appSettingDao.get(KEY_SYNC_ERROR)?.value.orEmpty()

    override suspend fun setThemeMode(mode: ThemeMode) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = THEME_KEY,
                value = mode.name.lowercase(),
            ),
        )
    }

    override suspend fun setThemeFamily(family: ThemeFamily) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = THEME_FAMILY_KEY,
                value = family.name.lowercase(),
            ),
        )
    }

    override suspend fun setAuthBaseUrl(baseUrl: String) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = AUTH_BASE_URL_KEY,
                value = baseUrl.trim(),
            ),
        )
    }

    override suspend fun setAuthToken(token: String?) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = AUTH_TOKEN_KEY,
                value = token.orEmpty(),
            ),
        )
    }

    override suspend fun setCurrentUserJson(userJson: String?) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = CURRENT_USER_JSON_KEY,
                value = userJson.orEmpty(),
            ),
        )
    }

    override suspend fun setCurrentUserId(userId: Long?) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = CURRENT_USER_ID_KEY,
                value = userId?.toString().orEmpty(),
            ),
        )
    }

    override suspend fun setAiProviderConfig(config: AiProviderConfig) {
        appSettingDao.upsert(AppSettingEntity(AI_PROVIDER_KEY, config.provider.storageKey))
        appSettingDao.upsert(AppSettingEntity(AI_API_KEY, config.apiKey.trim()))
        appSettingDao.upsert(AppSettingEntity(AI_BASE_URL_KEY, config.baseUrl.trim().removeSuffix("/")))
        appSettingDao.upsert(AppSettingEntity(AI_MODEL_KEY, config.model.trim()))
    }

    override suspend fun setLatestAiAdvice(
        advice: AiAdvice,
        generatedAt: Long,
    ) {
        appSettingDao.upsert(AppSettingEntity(AI_LATEST_ADVICE_JSON_KEY, advice.toStorageJson()))
        appSettingDao.upsert(AppSettingEntity(AI_LATEST_ADVICE_GENERATED_AT_KEY, generatedAt.toString()))
    }

    override suspend fun setSyncStatus(
        status: String,
        error: String?,
        syncedAt: Long?,
    ) {
        appSettingDao.upsert(AppSettingEntity(KEY_SYNC_STATUS, status))
        appSettingDao.upsert(AppSettingEntity(KEY_SYNC_ERROR, error.orEmpty()))
        if (syncedAt != null) {
            appSettingDao.upsert(AppSettingEntity(KEY_LAST_SYNC_AT, syncedAt.toString()))
        }
    }

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val THEME_FAMILY_KEY = "theme_family"
        const val AUTH_BASE_URL_KEY = "auth_base_url"
        const val AUTH_TOKEN_KEY = "auth_token"
        const val CURRENT_USER_JSON_KEY = "current_user_json"
        const val CURRENT_USER_ID_KEY = "current_user_id"
        const val AI_PROVIDER_KEY = "ai_provider"
        const val AI_API_KEY = "ai_api_key"
        const val AI_BASE_URL_KEY = "ai_base_url"
        const val AI_MODEL_KEY = "ai_model"
        const val AI_LATEST_ADVICE_JSON_KEY = "ai_latest_advice_json"
        const val AI_LATEST_ADVICE_GENERATED_AT_KEY = "ai_latest_advice_generated_at"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_SYNC_STATUS = "sync_status"
        const val KEY_SYNC_ERROR = "sync_error"
    }
}
