package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.ImportLogEntity

@Dao
interface ImportLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: ImportLogEntity): Long

    @Query("SELECT * FROM import_logs WHERE batchId = :batchId LIMIT 1")
    suspend fun getByBatchId(batchId: String): ImportLogEntity?

    @Query("SELECT * FROM import_logs ORDER BY importedAt DESC LIMIT 1")
    fun observeLatest(): Flow<ImportLogEntity?>

    @Query("DELETE FROM import_logs")
    suspend fun clear()
}
