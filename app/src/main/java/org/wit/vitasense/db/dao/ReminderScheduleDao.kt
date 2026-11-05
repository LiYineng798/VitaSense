package org.wit.vitasense.db.dao
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.ReminderScheduleEntity
@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules ORDER BY kind") fun observeAll(): Flow<List<ReminderScheduleEntity>>
    @Query("SELECT * FROM reminder_schedules") suspend fun all(): List<ReminderScheduleEntity>
    @Query("SELECT * FROM reminder_schedules WHERE kind = :kind LIMIT 1") suspend fun get(kind: String): ReminderScheduleEntity?
    @Upsert suspend fun upsert(schedule: ReminderScheduleEntity)
}
