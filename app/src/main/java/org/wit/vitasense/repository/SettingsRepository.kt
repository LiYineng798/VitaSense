package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    fun observeThemeFamily(): Flow<ThemeFamily>

    fun observeAuthBaseUrl(): Flow<String>

    fun observeAuthToken(): Flow<String>

    fun observeCurrentUserJson(): Flow<String>

    fun observeCurrentUserId(): Flow<Long?>

    fun observeAiProviderConfig(): Flow<AiProviderConfig>

    fun observeLatestAiAdvice(): Flow<AiAdvice?>

    fun observeLatestAiAdviceGeneratedAt(): Flow<Long?>

    suspend fun getThemeMode(): ThemeMode

    suspend fun getThemeFamily(): ThemeFamily

    suspend fun getAuthBaseUrl(): String

    suspend fun getAuthToken(): String

    suspend fun getCurrentUserJson(): String

    suspend fun getCurrentUserId(): Long?

    suspend fun getAiProviderConfig(): AiProviderConfig

    suspend fun getLatestAiAdvice(): AiAdvice?

    suspend fun getLatestAiAdviceGeneratedAt(): Long?

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setThemeFamily(family: ThemeFamily)

    suspend fun setAuthBaseUrl(baseUrl: String)

    suspend fun setAuthToken(token: String?)

    suspend fun setCurrentUserJson(userJson: String?)

    suspend fun setCurrentUserId(userId: Long?)

    suspend fun setAiProviderConfig(config: AiProviderConfig)

    suspend fun setLatestAiAdvice(
        advice: AiAdvice,
        generatedAt: Long,
    )
}
