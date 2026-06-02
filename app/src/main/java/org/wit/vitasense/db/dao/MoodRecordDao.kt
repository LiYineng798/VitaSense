package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.MoodRecordEntity

@Dao
interface MoodRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MoodRecordEntity): Long

    @Query(
        """
        SELECT * FROM mood_records
        WHERE deletedAt IS NULL
          AND (:group IS NULL OR moodGroup = :group)
          AND (:startDate IS NULL OR date >= :startDate)
          AND (:endDate IS NULL OR date <= :endDate)
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        group: String?,
        startDate: String?,
        endDate: String?,
    ): Flow<List<MoodRecordEntity>>

    @Query("SELECT * FROM mood_records WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActiveMoodRecords(): Flow<List<MoodRecordEntity>>

    @Query("SELECT * FROM mood_records ORDER BY createdAt ASC")
    suspend fun getAllForSync(): List<MoodRecordEntity>

    @Query("SELECT * FROM mood_records WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(cloudId: String): MoodRecordEntity?

    @Upsert
    suspend fun upsertForSync(entity: MoodRecordEntity)

    @Query("UPDATE mood_records SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, deletedAt: Long)

    @Query("DELETE FROM mood_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_records")
    suspend fun clear()
}
