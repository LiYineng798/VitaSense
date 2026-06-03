package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.model.CloudSyncResult
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.model.SyncReason
import org.wit.vitasense.repository.CloudSyncRepository

class DefaultMoodRepositoryTest {
    @Test
    fun moodDeleteCreatesTombstoneAndPushes() = runBlocking {
        val dao = FakeMoodRecordDao()
        val cloudSyncRepository = FakeMoodCloudSyncRepository()
        val repository = DefaultMoodRepository(dao) { cloudSyncRepository }

        repository.addMood("2026-06-03", MoodType.CALM, "steady")
        repository.deleteMood(1L)

        assertNotNull(dao.deletedAt)
        assertEquals(1L, dao.markDeletedId)
        assertEquals(listOf(SyncReason.MOOD_CHANGED, SyncReason.MOOD_CHANGED), cloudSyncRepository.pushReasons)
    }
}

private class FakeMoodRecordDao : MoodRecordDao {
    private val rows = mutableListOf<MoodRecordEntity>()
    var deletedAt: Long? = null
    var markDeletedId: Long? = null

    override suspend fun insert(record: MoodRecordEntity): Long {
        rows += record.copy(id = 1L)
        return 1L
    }

    override fun observeFiltered(
        group: String?,
        startDate: String?,
        endDate: String?,
    ): Flow<List<MoodRecordEntity>> = flowOf(rows.filter { it.deletedAt == null })

    override fun observeActiveMoodRecords(): Flow<List<MoodRecordEntity>> = flowOf(rows.filter { it.deletedAt == null })

    override suspend fun getAllForSync(): List<MoodRecordEntity> = rows

    override suspend fun getByCloudId(cloudId: String): MoodRecordEntity? = rows.firstOrNull { it.cloudId == cloudId }

    override suspend fun upsertForSync(entity: MoodRecordEntity) {
        rows.removeAll { it.id == entity.id }
        rows += entity
    }

    override suspend fun markDeleted(id: Long, deletedAt: Long) {
        markDeletedId = id
        this.deletedAt = deletedAt
        rows.replaceAll { if (it.id == id) it.copy(updatedAt = deletedAt, deletedAt = deletedAt) else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun clear() {
        rows.clear()
    }
}

private class FakeMoodCloudSyncRepository : CloudSyncRepository {
    val pushReasons = mutableListOf<SyncReason>()

    override suspend fun bootstrapAfterLogin(): CloudSyncResult = CloudSyncResult(true, "ok")

    override suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult {
        pushReasons += reason
        return CloudSyncResult(true, "ok")
    }

    override suspend fun syncNow(): CloudSyncResult = CloudSyncResult(true, "ok")
}
