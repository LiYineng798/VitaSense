package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.dao.AiChatMessageDao
import org.wit.vitasense.db.dao.AiChatSessionDao
import org.wit.vitasense.db.entity.AiChatMessageEntity
import org.wit.vitasense.db.entity.AiChatSessionEntity
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatMessageStatus
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.aiChatErrorMessage
import org.wit.vitasense.repository.AiChatRepository
import org.wit.vitasense.repository.SettingsRepository

class DefaultAiChatRepository(
    private val sessionDao: AiChatSessionDao,
    private val messageDao: AiChatMessageDao,
    private val settingsRepository: SettingsRepository,
    private val remoteDataSource: AiChatRemoteDataSource,
    private val healthContextBuilder: AiChatHealthContextBuilder,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AiChatRepository {
    override fun observeCurrentSession(): Flow<AiChatSessionEntity?> = sessionDao.observeCurrent()

    override fun observeMessages(sessionId: Long): Flow<List<AiChatMessageEntity>> =
        messageDao.observeForSession(sessionId)

    override suspend fun ensureCurrentSession(): Long {
        sessionDao.getCurrent()?.let { return it.id }
        return startNewChat()
    }

    override suspend fun startNewChat(): Long {
        val now = clock()
        sessionDao.clearCurrent()
        return sessionDao.insert(
            AiChatSessionEntity(
                title = "New chat",
                createdAt = now,
                updatedAt = now,
                isCurrent = true,
            ),
        )
    }

    override suspend fun deleteCurrentChat() {
        val current = sessionDao.getCurrent() ?: return
        messageDao.deleteForSession(current.id)
        sessionDao.deleteById(current.id)
        startNewChat()
    }

    override suspend fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val config = settingsRepository.getAiProviderConfig()
        val sessionId = ensureCurrentSession()
        val now = clock()
        messageDao.insert(
            AiChatMessageEntity(
                sessionId = sessionId,
                role = AiChatRole.USER.storageKey,
                content = trimmed,
                createdAt = now,
                status = AiChatMessageStatus.COMPLETE.storageKey,
            ),
        )
        updateSessionTitleAndTimestamp(sessionId, trimmed, now)

        val assistantId =
            messageDao.insert(
                AiChatMessageEntity(
                    sessionId = sessionId,
                    role = AiChatRole.ASSISTANT.storageKey,
                    content = "",
                    createdAt = now + 1,
                    status = AiChatMessageStatus.STREAMING.storageKey,
                ),
            )

        val history =
            messageDao.getForSession(sessionId)
                .filter { it.id != assistantId }
                .mapNotNull { entity ->
                    when (entity.role) {
                        AiChatRole.USER.storageKey -> AiChatMessage(AiChatRole.USER, entity.content)
                        AiChatRole.ASSISTANT.storageKey -> AiChatMessage(AiChatRole.ASSISTANT, entity.content)
                        else -> null
                    }
                }
        var assistantText = ""
        remoteDataSource.streamChat(config, history, healthContextBuilder.build()) { event ->
            when (event) {
                is AiChatStreamEvent.Delta -> {
                    assistantText += event.text
                    messageDao.updateContentAndStatus(
                        id = assistantId,
                        content = assistantText,
                        status = AiChatMessageStatus.STREAMING.storageKey,
                        errorMessage = null,
                    )
                }

                AiChatStreamEvent.Done ->
                    messageDao.updateContentAndStatus(
                        id = assistantId,
                        content = assistantText,
                        status = AiChatMessageStatus.COMPLETE.storageKey,
                        errorMessage = null,
                    )

                is AiChatStreamEvent.Error ->
                    messageDao.updateContentAndStatus(
                        id = assistantId,
                        content = assistantText,
                        status = AiChatMessageStatus.FAILED.storageKey,
                        errorMessage = event.message.ifBlank { aiChatErrorMessage(event.code) },
                    )
            }
        }
    }

    private suspend fun updateSessionTitleAndTimestamp(
        sessionId: Long,
        firstMessage: String,
        updatedAt: Long,
    ) {
        val session = sessionDao.getById(sessionId)
        if (session?.title == "New chat") {
            sessionDao.updateTitle(sessionId, firstMessage.take(36), updatedAt)
        } else {
            sessionDao.markCurrent(sessionId, updatedAt)
        }
    }
}
