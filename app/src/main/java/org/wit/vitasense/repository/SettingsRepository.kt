package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode

interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    fun observeThemeFamily(): Flow<ThemeFamily>

    suspend fun getThemeMode(): ThemeMode

    suspend fun getThemeFamily(): ThemeFamily

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setThemeFamily(family: ThemeFamily)
}
