package org.wit.vitasense.data.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.AiProviderConfig

class DefaultAiAdviceRepositoryTest {
    @Test
    fun maps_successful_proxy_response_to_advice() =
        runBlocking {
            val repository =
                DefaultAiAdviceRepository(
                    proxyBaseUrl = "https://server.example",
                    connectionFactory =
                        FakeAiConnectionFactory(
                            code = 200,
                            body = """{"success":true,"advice":{"summary":"Stable","recommendations":["Rest"],"risk_note":"Low risk","disclaimer":"Not diagnosis"}}""",
                        ),
                )

            val result = repository.generateAdvice(config(), summary())

            val success = result as AiAdviceResult.Success
            assertEquals("Stable", success.advice.summary)
            assertEquals(listOf("Rest"), success.advice.recommendations)
        }

    @Test
    fun maps_proxy_error_code_to_error_result() =
        runBlocking {
            val repository =
                DefaultAiAdviceRepository(
                    proxyBaseUrl = "https://server.example",
                    connectionFactory =
                        FakeAiConnectionFactory(
                            code = 401,
                            body = """{"success":false,"code":"invalid_api_key","message":"bad key"}""",
                        ),
                )

            val result = repository.generateAdvice(config(), summary())

            val error = result as AiAdviceResult.Error
            assertEquals("invalid_api_key", error.code)
        }

    private fun config() =
        AiProviderConfig(
            provider = AiProvider.DEEPSEEK,
            apiKey = "sk-test",
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-chat",
        )

    private fun summary() =
        AiHealthSummary(
            date = "2026-06-02",
            totalScore = 82,
            riskLevel = "low",
            sleepMinutes = 430,
            rmssd = 35.0,
            restingHeartRate = 61.0,
            avgHeartRate = 65.0,
            anomalyFlags = emptyList(),
            ruleSuggestion = "Keep the current pace.",
        )
}

private class FakeAiConnectionFactory(
    private val code: Int,
    private val body: String,
) : AiConnectionFactory {
    override fun open(url: URL): HttpURLConnection = FakeConnection(url, code, body)
}

private class FakeConnection(
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
