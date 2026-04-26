package org.wit.vitasense.data.importer

import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.model.ImportBundle
import org.wit.vitasense.model.ImportHeartRateSample
import org.wit.vitasense.model.ImportSleepRecord

object ImportBundleParser {
    fun parse(raw: String): ImportBundle {
        val root = JSONObject(raw)
        return ImportBundle(
            batchId = root.getString("batchId"),
            sourceType = root.getString("sourceType"),
            generatedAt = root.getString("generatedAt"),
            heartRateSamples = root.getJSONArray("heartRateSamples").toHeartRateSamples(),
            sleepRecords = root.getJSONArray("sleepRecords").toSleepRecords(),
        )
    }

    private fun JSONArray.toHeartRateSamples(): List<ImportHeartRateSample> =
        buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    ImportHeartRateSample(
                        timestamp = item.getString("timestamp"),
                        heartRate = item.getInt("heartRate"),
                    ),
                )
            }
        }

    private fun JSONArray.toSleepRecords(): List<ImportSleepRecord> =
        buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    ImportSleepRecord(
                        date = item.getString("date"),
                        startAt = item.getString("startAt"),
                        endAt = item.getString("endAt"),
                    ),
                )
            }
        }
}
