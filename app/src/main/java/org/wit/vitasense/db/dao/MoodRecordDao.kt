package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.MoodRecordEntity

@Dao
interface MoodRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MoodRecordEntity): Long

    @Query(
        """
        SELECT * FROM mood_records
        WHERE (:group IS NULL OR moodGroup = :group)
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

    @Query("DELETE FROM mood_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_records")
    suspend fun clear()
}
