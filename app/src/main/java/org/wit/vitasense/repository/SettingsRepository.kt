package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.ThemeMode

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun getThemeMode(): ThemeMode

    suspend fun setThemeMode(mode: ThemeMode)
}
