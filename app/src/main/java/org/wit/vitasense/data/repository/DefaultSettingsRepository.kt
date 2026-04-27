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

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val THEME_FAMILY_KEY = "theme_family"
    }
}
