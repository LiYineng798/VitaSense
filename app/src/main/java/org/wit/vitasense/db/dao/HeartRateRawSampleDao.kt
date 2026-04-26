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

    @Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp DESC LIMIT 1")
    fun observeLatest(): Flow<HeartRateRawSampleEntity?>

    @Query("SELECT * FROM heart_rate_raw_samples ORDER BY sampleTimestamp DESC LIMIT 1")
    suspend fun getLatestOnce(): HeartRateRawSampleEntity?

    @Query("SELECT * FROM heart_rate_raw_samples WHERE date = :date ORDER BY sampleTimestamp ASC")
    suspend fun getByDate(date: String): List<HeartRateRawSampleEntity>

    @Query("SELECT * FROM heart_rate_raw_samples WHERE sampleTimestamp BETWEEN :startAt AND :endAt ORDER BY sampleTimestamp ASC")
    suspend fun getBetween(startAt: Long, endAt: Long): List<HeartRateRawSampleEntity>

    @Query("SELECT DISTINCT date FROM heart_rate_raw_samples ORDER BY date ASC")
    suspend fun getDistinctDates(): List<String>

    @Query("DELETE FROM heart_rate_raw_samples")
    suspend fun clear()
}
