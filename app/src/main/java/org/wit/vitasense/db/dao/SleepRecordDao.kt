package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.SleepRecordEntity

@Dao
interface SleepRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SleepRecordEntity): Long

    @Query("SELECT * FROM sleep_records WHERE date = :date AND deletedAt IS NULL LIMIT 1")
    suspend fun getByDate(date: String): SleepRecordEntity?

    @Query("SELECT * FROM sleep_records WHERE date = :date LIMIT 1")
    suspend fun getAnyByDate(date: String): SleepRecordEntity?

    @Query("SELECT * FROM sleep_records WHERE deletedAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeLatest(): Flow<SleepRecordEntity?>

    @Query("SELECT * FROM sleep_records WHERE deletedAt IS NULL ORDER BY date ASC")
    suspend fun getAllActiveForSync(): List<SleepRecordEntity>

    @Upsert
    suspend fun upsertForSync(entity: SleepRecordEntity)

    @Query("SELECT DISTINCT date FROM sleep_records WHERE deletedAt IS NULL ORDER BY date ASC")
    suspend fun getDistinctDates(): List<String>

    @Query("DELETE FROM sleep_records")
    suspend fun clear()
}
