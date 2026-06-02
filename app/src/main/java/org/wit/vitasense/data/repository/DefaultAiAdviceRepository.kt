package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.repository.AiAdviceRepository

interface AiConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

object DefaultAiConnectionFactory : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = url.openConnection() as HttpURLConnection
}

class DefaultAiAdviceRepository(
    private val proxyBaseUrl: String,
    private val connectionFactory: AiConnectionFactory = DefaultAiConnectionFactory,
) : AiAdviceRepository {
    override suspend fun generateAdvice(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): AiAdviceResult =
        withContext(Dispatchers.IO) {
            try {
                parseResponse(execute(config, summary).body)
            } catch (_: IOException) {
                AiAdviceResult.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy.")
            } catch (_: SecurityException) {
                AiAdviceResult.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy.")
            } catch (_: Exception) {
                AiAdviceResult.Error("unexpected_ai_response", "The AI service returned an unexpected response.")
            }
        }

    private fun execute(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): HttpResponse {
        val connection = connectionFactory.open(URL(proxyBaseUrl.trim().removeSuffix("/") + "/api/v1/ai/advice"))
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(buildPayload(config, summary).toString())
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            HttpResponse(
                code = code,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): JSONObject =
        JSONObject()
            .put("provider", config.provider.storageKey)
            .put("base_url", config.baseUrl)
            .put("model", config.model)
            .put("api_key", config.apiKey)
            .put(
                "health_summary",
                JSONObject()
                    .put("date", summary.date)
                    .put("total_score", summary.totalScore)
                    .put("risk_level", summary.riskLevel)
                    .put("sleep_minutes", summary.sleepMinutes)
                    .put("rmssd", summary.rmssd)
                    .put("resting_heart_rate", summary.restingHeartRate)
                    .put("avg_heart_rate", summary.avgHeartRate)
                    .put("anomaly_flags", JSONArray(summary.anomalyFlags))
                    .put("rule_suggestion", summary.ruleSuggestion),
            )

    private fun parseResponse(raw: String): AiAdviceResult {
        val obj = JSONObject(raw.ifBlank { "{}" })
        if (!obj.optBoolean("success", false)) {
            val code = obj.optString("code", "unexpected_ai_response")
            return AiAdviceResult.Error(code, obj.optString("message"))
        }
        val advice = obj.getJSONObject("advice")
        val recs = advice.optJSONArray("recommendations") ?: JSONArray()
        return AiAdviceResult.Success(
            AiAdvice(
                summary = advice.optString("summary"),
                recommendations =
                    (0 until recs.length()).mapNotNull { index ->
                        recs.optString(index).takeIf { it.isNotBlank() }
                    },
                riskNote = advice.optString("risk_note"),
                disclaimer = advice.optString("disclaimer"),
            ),
        )
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}
