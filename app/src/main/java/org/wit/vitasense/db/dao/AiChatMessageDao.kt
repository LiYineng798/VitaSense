package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatMessageEntity

@Dao
interface AiChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AiChatMessageEntity): Long

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeForSession(sessionId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getForSession(sessionId: Long): List<AiChatMessageEntity>

    @Query("UPDATE ai_chat_messages SET content = :content, status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateContentAndStatus(
        id: Long,
        content: String,
        status: String,
        errorMessage: String?,
    )

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
