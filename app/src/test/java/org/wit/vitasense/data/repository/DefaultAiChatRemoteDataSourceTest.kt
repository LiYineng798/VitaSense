package org.wit.vitasense.data.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AiChatMessage
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.model.AiChatStreamEvent
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig

class DefaultAiChatRemoteDataSourceTest {
    @Test
    fun streamsDeltaAndDoneEvents() =
        runBlocking {
            val events = mutableListOf<AiChatStreamEvent>()
            val dataSource =
                DefaultAiChatRemoteDataSource(
                    proxyBaseUrl = "https://server.example",
                    connectionFactory =
                        FakeAiChatConnectionFactory(
                            code = 200,
                            body =
                                """
                                data: {"delta":"Hello"}

                                data: {"delta":" there"}

                                data: {"done":true}
                                """.trimIndent(),
                        ),
                )

            dataSource.streamChat(config(), listOf(AiChatMessage(AiChatRole.USER, "Hi")), emptyMap()) {
                events += it
            }

            assertEquals(
                listOf(
                    AiChatStreamEvent.Delta("Hello"),
                    AiChatStreamEvent.Delta(" there"),
                    AiChatStreamEvent.Done,
                ),
                events,
            )
        }

    @Test
    fun mapsTransportErrorToProxyUnreachable() =
        runBlocking {
            val events = mutableListOf<AiChatStreamEvent>()
            val dataSource =
                DefaultAiChatRemoteDataSource(
                    proxyBaseUrl = "https://server.example",
                    connectionFactory = FakeAiChatConnectionFactory(code = 500, body = ""),
                )

            dataSource.streamChat(config(), listOf(AiChatMessage(AiChatRole.USER, "Hi")), emptyMap()) {
                events += it
            }

            assertEquals(
                AiChatStreamEvent.Error("proxy_unreachable", "Unable to reach the VitaSense AI proxy."),
                events.single(),
            )
        }

    private fun config() =
        AiProviderConfig(
            provider = AiProvider.DEEPSEEK,
            apiKey = "sk-test",
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat",
        )
}

private class FakeAiChatConnectionFactory(
    private val code: Int,
    private val body: String,
) : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = FakeAiChatConnection(url, code, body)
}

private class FakeAiChatConnection(
    url: URL,
    private val code: Int,
    private val body: String,
) : HttpURLConnection(url) {
    private val requestBuffer = java.io.ByteArrayOutputStream()

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit

    override fun getOutputStream() = requestBuffer

    override fun getResponseCode(): Int = code

    override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())

    override fun getErrorStream(): InputStream = ByteArrayInputStream(body.toByteArray())
}
