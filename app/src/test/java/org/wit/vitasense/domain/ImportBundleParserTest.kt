package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.data.importer.ImportBundleParser

class ImportBundleParserTest {
    @Test
    fun parses_mock_import_bundle() {
        val json =
            """
            {
              "batchId": "demo-1",
              "sourceType": "mock_json",
              "generatedAt": "2026-04-23T08:00:00+08:00",
              "heartRateSamples": [
                {
                  "timestamp": "2026-04-22T23:10:00+08:00",
                  "heartRate": 63
                }
              ],
              "sleepRecords": [
                {
                  "date": "2026-04-22",
                  "startAt": "2026-04-22T23:15:00+08:00",
                  "endAt": "2026-04-23T07:00:00+08:00"
                }
              ]
            }
            """.trimIndent()

        val bundle = ImportBundleParser.parse(json)

        assertEquals("demo-1", bundle.batchId)
        assertEquals(1, bundle.heartRateSamples.size)
        assertEquals(1, bundle.sleepRecords.size)
    }
}
