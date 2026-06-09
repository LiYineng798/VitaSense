package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatModelsTest {
    @Test
    fun parsesDeltaDoneAndErrorStreamEvents() {
        assertEquals(AiChatStreamEvent.Delta("Hi"), parseAiChatStreamLine("""data: {"delta":"Hi"}"""))
        assertEquals(AiChatStreamEvent.Done, parseAiChatStreamLine("""data: {"done":true}"""))
        assertEquals(
            AiChatStreamEvent.Error("invalid_api_key", "Bad key"),
            parseAiChatStreamLine("""data: {"error":{"code":"invalid_api_key","message":"Bad key"}}"""),
        )
    }

    @Test
    fun ignoresBlankOrMalformedLines() {
        assertEquals(null, parseAiChatStreamLine(""))
        assertEquals(null, parseAiChatStreamLine("event: message"))
        assertTrue(aiChatErrorMessage("quota_or_rate_limit").contains("quota", ignoreCase = true))
    }
}
