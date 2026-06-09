package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.parseAiChatStreamLine

class DefaultAiChatRemoteDataSource(
    private val proxyBaseUrl: String,
    private val connectionFactory: AiConnectionFactory = DefaultAiConnectionFactory,
) : AiChatRemoteDataSource {
    override suspend fun streamChat(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
        onEvent: suspend (AiChatStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = connectionFactory.open(URL(proxyBaseUrl.trim().removeSuffix("/") + "/api/v1/ai/chat/stream"))
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(buildPayload(config, messages, healthContext).toString())
            }
            if (connection.responseCode !in 200..299) {
                onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
                return@withContext
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    parseAiChatStreamLine(line)?.let { event ->
                        onEvent(event)
                    }
                }
            }
        } catch (_: IOException) {
            onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
        } catch (_: SecurityException) {
            onEvent(AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(
        config: AiProviderConfig,
        messages: List<AiChatMessage>,
        healthContext: Map<String, Any?>,
    ): JSONObject =
        JSONObject()
            .put("provider", config.provider.storageKey)
            .put("base_url", config.baseUrl)
            .put("model", config.model)
            .put("api_key", config.apiKey)
            .put(
                "messages",
                JSONArray(
                    messages.map {
                        JSONObject()
                            .put("role", it.role.storageKey)
                            .put("content", it.content)
                    },
                ),
            )
            .put("health_context", JSONObject(healthContext))
}
