package org.wit.vitasense.data.repository

import org.wit.vitasense.domain.WeeklyReportGenerator
import java.time.LocalDate

class WeeklyReportsRepository(private val insights: HealthInsightsRepository) {
    suspend fun generate(today: LocalDate = LocalDate.now()) = WeeklyReportGenerator.generate(
        insights.snapshot(today.minusDays(6),today), insights.snapshot(today.minusDays(13),today.minusDays(7)))
    suspend fun explain(aggregates: String) = insights.explainAggregates(aggregates)
}
