package org.wit.vitasense.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity

class DerivedContentSyncTest {
    @Test
    fun refreshes_derived_content_when_version_is_missing() = runBlocking {
        val appSettingDao = FakeAppSettingDao()
        var recomputeCount = 0
        val sync =
            DerivedContentSync(
                appSettingDao = appSettingDao,
                recompute = { recomputeCount++ },
            )

        sync.refreshIfNeeded()

        assertEquals(1, recomputeCount)
        assertEquals(
            DerivedContentSync.CURRENT_VERSION,
            appSettingDao.get(DerivedContentSync.KEY)?.value,
        )
    }

    @Test
    fun skips_refresh_when_version_is_current() = runBlocking {
        val appSettingDao =
            FakeAppSettingDao(
                mutableMapOf(
                    DerivedContentSync.KEY to
                        AppSettingEntity(
                            key = DerivedContentSync.KEY,
                            value = DerivedContentSync.CURRENT_VERSION,
                        ),
                ),
            )
        var recomputeCount = 0
        val sync =
            DerivedContentSync(
                appSettingDao = appSettingDao,
                recompute = { recomputeCount++ },
            )

        sync.refreshIfNeeded()

        assertEquals(0, recomputeCount)
        assertEquals(
            DerivedContentSync.CURRENT_VERSION,
            appSettingDao.get(DerivedContentSync.KEY)?.value,
        )
    }

    @Test
    fun does_not_write_version_when_recompute_fails() = runBlocking {
        val appSettingDao = FakeAppSettingDao()
        val sync =
            DerivedContentSync(
                appSettingDao = appSettingDao,
                recompute = { error("boom") },
            )

        runCatching { sync.refreshIfNeeded() }

        assertNull(appSettingDao.get(DerivedContentSync.KEY))
        assertTrue(appSettingDao.upsertedKeys.isEmpty())
    }

    private class FakeAppSettingDao(
        private val storage: MutableMap<String, AppSettingEntity> = mutableMapOf(),
    ) : AppSettingDao {
        val upsertedKeys = mutableListOf<String>()

        override suspend fun upsert(setting: AppSettingEntity) {
            storage[setting.key] = setting
            upsertedKeys += setting.key
        }

        override fun observe(key: String): Flow<AppSettingEntity?> = emptyFlow()

        override suspend fun get(key: String): AppSettingEntity? = storage[key]
    }
}
