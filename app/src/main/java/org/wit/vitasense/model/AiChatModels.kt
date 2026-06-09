package org.wit.vitasense.model

import org.json.JSONObject

enum class AiChatRole(
    val storageKey: String,
) {
    USER("user"),
    ASSISTANT("assistant"),
}

enum class AiChatMessageStatus(
    val storageKey: String,
) {
    SENDING("sending"),
    STREAMING("streaming"),
    COMPLETE("complete"),
    FAILED("failed"),
}

data class AiChatMessage(
    val role: AiChatRole,
    val content: String,
)

sealed interface AiChatStreamEvent {
    data class Delta(
        val text: String,
    ) : AiChatStreamEvent

    data object Done : AiChatStreamEvent

    data class Error(
        val code: String,
        val message: String,
    ) : AiChatStreamEvent
}

fun parseAiChatStreamLine(raw: String): AiChatStreamEvent? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload.isBlank()) return null
    val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    if (obj.optBoolean("done", false)) return AiChatStreamEvent.Done
    obj.optString("delta").takeIf { it.isNotBlank() }?.let { return AiChatStreamEvent.Delta(it) }
    val error = obj.optJSONObject("error") ?: return null
    return AiChatStreamEvent.Error(
        code = error.optString("code", "unexpected_ai_response"),
        message = error.optString("message"),
    )
}

fun aiChatErrorMessage(code: String): String =
    when (code) {
        "missing_api_key" -> "Add an API key in Settings first."
        "missing_model" -> "Add a model name in Settings first."
        "missing_base_url" -> "Add an AI base URL in Settings first."
        "invalid_api_key" -> "The API key is invalid or expired."
        "model_unavailable" -> "The selected model is not available. Check the model name."
        "quota_or_rate_limit" -> "The AI service quota or rate limit was reached."
        "ai_network_error" -> "Unable to reach the AI service. Check your network or base URL."
        "proxy_unreachable" -> "Unable to reach the VitaSense AI proxy."
        "empty_message" -> "Enter a message before sending."
        else -> "Unable to continue the AI chat right now."
    }
