package org.wit.vitasense.repository

import org.wit.vitasense.model.AiAdviceResult
import org.wit.vitasense.model.AiHealthSummary
import org.wit.vitasense.model.AiProviderConfig

interface AiAdviceRepository {
    suspend fun generateAdvice(
        config: AiProviderConfig,
        summary: AiHealthSummary,
    ): AiAdviceResult
}
