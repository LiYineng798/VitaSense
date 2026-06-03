package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.model.SyncReason
import org.wit.vitasense.repository.CloudSyncRepository
import org.wit.vitasense.repository.MoodRepository

class DefaultMoodRepository(
    private val moodRecordDao: MoodRecordDao,
    private val cloudSyncRepositoryProvider: (() -> CloudSyncRepository?)? = null,
) : MoodRepository {
    override fun observeMoodRecords(filter: MoodFilter): Flow<List<MoodRecordEntity>> =
        moodRecordDao.observeFiltered(
            group = filter.group?.name?.lowercase(),
            startDate = filter.startDate,
            endDate = filter.endDate,
        )

    override suspend fun addMood(
        date: String,
        moodType: MoodType,
        note: String?,
    ) {
        moodRecordDao.insert(
            MoodRecordEntity(
                date = date,
                moodType = moodType.name,
                moodGroup = moodType.group.name.lowercase(),
                note = note?.takeIf { it.isNotBlank() },
            ),
        )
        pushMoodChanged()
    }

    override suspend fun deleteMood(id: Long) {
        moodRecordDao.markDeleted(id, System.currentTimeMillis())
        pushMoodChanged()
    }

    private suspend fun pushMoodChanged() {
        runCatching {
            cloudSyncRepositoryProvider?.invoke()?.pushLocalSnapshot(SyncReason.MOOD_CHANGED)
        }
    }
}
