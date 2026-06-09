package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.AiChatMessageEntity
import org.wit.vitasense.db.entity.AiChatSessionEntity

interface AiChatRepository {
    fun observeCurrentSession(): Flow<AiChatSessionEntity?>

    fun observeMessages(sessionId: Long): Flow<List<AiChatMessageEntity>>

    suspend fun ensureCurrentSession(): Long

    suspend fun startNewChat(): Long

    suspend fun deleteCurrentChat()

    suspend fun sendMessage(text: String)
}
