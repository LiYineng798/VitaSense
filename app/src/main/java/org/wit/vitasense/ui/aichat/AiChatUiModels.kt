package org.wit.vitasense.ui.aichat

data class AiChatMessageUiModel(
    val id: Long,
    val role: String,
    val content: String,
    val isAssistant: Boolean,
    val isStreaming: Boolean,
    val errorText: String?,
)

data class AiChatScreenState(
    val sessionId: Long? = null,
    val title: String = "AI Chat",
    val messages: List<AiChatMessageUiModel> = emptyList(),
    val setupRequired: Boolean = false,
    val isGenerating: Boolean = false,
    val errorText: String? = null,
)
