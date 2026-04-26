package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity
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

    override suspend fun getThemeMode(): ThemeMode =
        when (appSettingDao.get(THEME_KEY)?.value?.lowercase()) {
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        appSettingDao.upsert(
            AppSettingEntity(
                key = THEME_KEY,
                value = mode.name.lowercase(),
            ),
        )
    }

    private companion object {
        const val THEME_KEY = "theme_mode"
    }
}
