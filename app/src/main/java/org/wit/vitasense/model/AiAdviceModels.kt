package org.wit.vitasense.model

import org.json.JSONArray
import org.json.JSONObject

enum class AiProvider(
    val storageKey: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    DEEPSEEK("deepseek", "https://api.deepseek.com", "deepseek-chat"),
    OPENAI_COMPATIBLE("openai_compatible", "", ""),
    ;

    companion object {
        fun fromStorageKey(raw: String): AiProvider =
            entries.firstOrNull { it.storageKey == raw.lowercase() } ?: DEEPSEEK
    }
}

data class AiProviderConfig(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = AiProvider.DEEPSEEK.defaultBaseUrl,
    val model: String = AiProvider.DEEPSEEK.defaultModel,
) {
    val isComplete: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

data class AiAdvice(
    val summary: String,
    val recommendations: List<String>,
    val riskNote: String,
    val disclaimer: String,
)

data class AiHealthSummary(
    val date: String,
    val totalScore: Int?,
    val riskLevel: String?,
    val sleepMinutes: Int?,
    val rmssd: Double?,
    val restingHeartRate: Double?,
    val avgHeartRate: Double?,
    val anomalyFlags: List<String>,
    val ruleSuggestion: String?,
)

sealed interface AiAdviceResult {
    data class Success(
        val advice: AiAdvice,
    ) : AiAdviceResult

    data class Error(
        val code: String,
        val message: String,
    ) : AiAdviceResult
}

fun AiAdvice.toStorageJson(): String =
    JSONObject()
        .put("summary", summary)
        .put("recommendations", JSONArray(recommendations))
        .put("risk_note", riskNote)
        .put("disclaimer", disclaimer)
        .toString()

fun parseStoredAiAdvice(raw: String): AiAdvice? =
    runCatching {
        val obj = JSONObject(raw)
        val recs = obj.optJSONArray("recommendations") ?: JSONArray()
        AiAdvice(
            summary = obj.optString("summary"),
            recommendations =
                (0 until recs.length()).mapNotNull { index ->
                    recs.optString(index).takeIf { it.isNotBlank() }
                },
            riskNote = obj.optString("risk_note"),
            disclaimer = obj.optString("disclaimer"),
        )
    }.getOrNull()

fun aiErrorMessage(code: String): String =
    when (code) {
        "missing_api_key" -> "Add an API key in Settings first."
        "missing_model" -> "Add a model name in Settings first."
        "missing_base_url" -> "Add an AI base URL in Settings first."
        "proxy_unreachable" -> "Unable to reach the VitaSense AI proxy."
        "ai_network_error" -> "Unable to reach the AI service. Check your network or base URL."
        "invalid_api_key" -> "The API key is invalid or expired."
        "model_unavailable" -> "The selected model is not available. Check the model name."
        "quota_or_rate_limit" -> "The AI service quota or rate limit was reached."
        "unexpected_ai_response" -> "The AI service returned an unexpected response."
        else -> "Unable to generate AI advice right now."
    }
