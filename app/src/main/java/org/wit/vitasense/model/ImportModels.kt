package org.wit.vitasense.model

data class ImportHeartRateSample(
    val timestamp: String,
    val heartRate: Int,
)

data class ImportSleepRecord(
    val date: String,
    val startAt: String,
    val endAt: String,
)

data class ImportBundle(
    val batchId: String,
    val sourceType: String,
    val generatedAt: String,
    val heartRateSamples: List<ImportHeartRateSample>,
    val sleepRecords: List<ImportSleepRecord>,
)
