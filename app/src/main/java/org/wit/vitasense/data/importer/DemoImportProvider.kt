package org.wit.vitasense.data.importer

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.wit.vitasense.model.DemoBundleInfo

class DemoImportProvider {
    private val baseDate: LocalDate = LocalDate.of(2026, 4, 23)

    fun availableBundles(): List<DemoBundleInfo> =
        listOf(
            DemoBundleInfo("normal", "Stable Sample", "7 days of steady sleep and heart-rate change"),
            DemoBundleInfo("low_sleep", "Low Sleep Sample", "Sleep duration stays short across recent days"),
            DemoBundleInfo("hrv_drop", "HRV Drop Sample", "HRV falls noticeably over the last two days"),
            DemoBundleInfo("continuous_anomaly", "Continuous Anomaly Sample", "Anomalies persist over the last three days"),
            DemoBundleInfo("high_risk", "High Risk Sample", "Low sleep + high heart rate + low HRV"),
        )

    fun rawBundle(bundleId: String): String =
        when (bundleId) {
            "normal" -> scenarioJson(bundleId, listOf(450, 440, 460, 430, 450, 455, 465), listOf(60, 62, 59, 63, 58, 61))
            "low_sleep" -> scenarioJson(bundleId, listOf(410, 360, 340, 320, 300, 310, 330), listOf(64, 65, 63, 66, 64, 65))
            "hrv_drop" -> scenarioJson(bundleId, listOf(450, 445, 440, 430, 425, 420, 420), listOf(60, 61, 59, 60, 60, 60, 60), lowVarianceFrom = 5)
            "continuous_anomaly" -> scenarioJson(bundleId, listOf(450, 440, 430, 360, 320, 310, 300), listOf(60, 61, 60, 66, 70, 72, 73), lowVarianceFrom = 4)
            "high_risk" -> scenarioJson(bundleId, listOf(360, 340, 320, 300, 280, 260, 255), listOf(68, 70, 72, 74, 75, 76, 77), lowVarianceFrom = 0)
            else -> scenarioJson("normal", listOf(450, 440, 460, 430, 450, 455, 465), listOf(60, 62, 59, 63, 58, 61))
        }

    private fun scenarioJson(
        bundleId: String,
        sleepDurations: List<Int>,
        baseHeartRates: List<Int>,
        lowVarianceFrom: Int = Int.MAX_VALUE,
    ): String {
        val heartRateItems = mutableListOf<String>()
        val sleepItems = mutableListOf<String>()

        sleepDurations.forEachIndexed { index, duration ->
            val day = baseDate.minusDays((sleepDurations.size - 1L) - index)
            val endAt = day.atTime(7, 0).atOffset(ZoneOffset.ofHours(8))
            val startAt = endAt.minusMinutes(duration.toLong())
            val pattern =
                if (index >= lowVarianceFrom) {
                    listOf(0, 0, 1, 0, 0, 1, 0, 0)
                } else {
                    listOf(-2, 1, -1, 2, -3, 2, -1, 1)
                }
            repeat(40) { pointIndex ->
                val timestamp = startAt.plusMinutes((pointIndex * 10L))
                val bpm = baseHeartRates[minOf(index, baseHeartRates.lastIndex)] + pattern[pointIndex % pattern.size]
                heartRateItems += """{"timestamp":"$timestamp","heartRate":$bpm}"""
            }
            sleepItems += """{"date":"$day","startAt":"$startAt","endAt":"$endAt"}"""
        }

        val generatedAt = OffsetDateTime.of(baseDate.atTime(8, 0), ZoneOffset.ofHours(8))
        return buildString {
            append("{")
            append(""""batchId":"demo-$bundleId",""")
            append(""""sourceType":"mock_json",""")
            append(""""generatedAt":"$generatedAt",""")
            append(""""heartRateSamples":[${heartRateItems.joinToString(",")}],""")
            append(""""sleepRecords":[${sleepItems.joinToString(",")}]""")
            append("}")
        }
    }
}
