package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity

@Dao
interface DailySummaryDao {
    @Upsert
    suspend fun upsert(summary: DailyPhysiologySummaryEntity)

    @Query("SELECT * FROM daily_physiology_summary ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<DailyPhysiologySummaryEntity?>

    @Query("SELECT * FROM daily_physiology_summary ORDER BY date DESC LIMIT 1")
    suspend fun getLatestOnce(): DailyPhysiologySummaryEntity?

    @Query("SELECT * FROM daily_physiology_summary WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyPhysiologySummaryEntity?

    @Query("SELECT * FROM daily_physiology_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun observeRange(startDate: String, endDate: String): Flow<List<DailyPhysiologySummaryEntity>>

    @Query("SELECT * FROM daily_physiology_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRange(startDate: String, endDate: String): List<DailyPhysiologySummaryEntity>

    @Query("SELECT * FROM daily_physiology_summary ORDER BY date ASC")
    suspend fun getAllSorted(): List<DailyPhysiologySummaryEntity>

    @Query("DELETE FROM daily_physiology_summary")
    suspend fun clear()
}
