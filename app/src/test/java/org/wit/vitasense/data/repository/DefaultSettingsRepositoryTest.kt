package org.wit.vitasense.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
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
