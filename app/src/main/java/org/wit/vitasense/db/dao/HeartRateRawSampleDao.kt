package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity

@Dao
interface HeartRateRawSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<HeartRateRawSampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: HeartRateRawSampleEntity): Long

    @Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp DESC LIMIT 1")
    fun observeLatest(): Flow<HeartRateRawSampleEntity?>

    @Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp DESC LIMIT 1")
    suspend fun getLatestOnce(): HeartRateRawSampleEntity?

    @Query("SELECT * FROM heart_rate_raw_samples WHERE date = :date ORDER BY sampleTimestamp ASC")
    suspend fun getByDate(date: String): List<HeartRateRawSampleEntity>

    @Query("SELECT * FROM heart_rate_raw_samples WHERE sampleTimestamp BETWEEN :startAt AND :endAt ORDER BY sampleTimestamp ASC")
    suspend fun getBetween(startAt: Long, endAt: Long): List<HeartRateRawSampleEntity>

    @Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp ASC")
    suspend fun getAllForSync(): List<HeartRateRawSampleEntity>

    @Query(
        """
        SELECT * FROM heart_rate_raw_samples
        WHERE sampleTimestamp = :sampleTimestamp
          AND heartRate = :heartRate
          AND sourceBatchId = :sourceBatchId
        LIMIT 1
        """,
    )
    suspend fun findDuplicate(
        sampleTimestamp: Long,
        heartRate: Int,
        sourceBatchId: String,
    ): HeartRateRawSampleEntity?

    @Query("SELECT DISTINCT date FROM heart_rate_raw_samples ORDER BY date ASC")
    suspend fun getDistinctDates(): List<String>

    @Query("DELETE FROM heart_rate_raw_samples")
    suspend fun clear()
}
