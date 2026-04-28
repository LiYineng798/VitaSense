package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
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

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val THEME_FAMILY_KEY = "theme_family"
        const val AUTH_BASE_URL_KEY = "auth_base_url"
        const val AUTH_TOKEN_KEY = "auth_token"
        const val CURRENT_USER_JSON_KEY = "current_user_json"
        const val CURRENT_USER_ID_KEY = "current_user_id"
    }
}
