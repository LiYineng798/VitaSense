package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatSessionEntity

@Dao
interface AiChatSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AiChatSessionEntity): Long

    @Update
    suspend fun update(session: AiChatSessionEntity)

    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<AiChatSessionEntity>>

    @Query("SELECT * FROM ai_chat_sessions WHERE isCurrent = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun observeCurrent(): Flow<AiChatSessionEntity?>

    @Query("SELECT * FROM ai_chat_sessions WHERE isCurrent = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getCurrent(): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiChatSessionEntity?

    @Query("UPDATE ai_chat_sessions SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query("UPDATE ai_chat_sessions SET isCurrent = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCurrent(
        id: Long,
        updatedAt: Long,
    )

    @Query("UPDATE ai_chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(
        id: Long,
        title: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM ai_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
