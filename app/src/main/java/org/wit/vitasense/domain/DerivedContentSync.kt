package org.wit.vitasense.domain

import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity

class DerivedContentSync(
    private val appSettingDao: AppSettingDao,
    private val recompute: suspend () -> Unit,
) {
    suspend fun refreshIfNeeded() {
        val currentVersion = appSettingDao.get(KEY)?.value
        if (currentVersion == CURRENT_VERSION) {
            return
        }

        recompute()
        appSettingDao.upsert(
            AppSettingEntity(
                key = KEY,
                value = CURRENT_VERSION,
            ),
        )
    }

    companion object {
        const val KEY = "derived_content_version"
        const val CURRENT_VERSION = "en_v1"
    }
}
