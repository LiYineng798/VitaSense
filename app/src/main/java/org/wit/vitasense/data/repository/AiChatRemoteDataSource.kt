package org.wit.vitasense.data.repository

import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProviderConfig

interface AiChatRemoteDataSource {
    suspend fun streamChat(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
        onEvent: suspend (AiChatStreamEvent) -> Unit,
    )
}
