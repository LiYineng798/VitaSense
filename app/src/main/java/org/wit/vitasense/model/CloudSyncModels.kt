package org.wit.vitasense.model

import java.security.MessageDigest

data class CloudSyncSettings(
    val themeMode: String,
    val themeFamily: String,
    val updatedAt: Long,
)

data class CloudSyncMoodRecord(
    val id: String,
    val date: String,
    val moodType: String,
    val moodGroup: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class CloudSyncHeartRateSample(
    val id: String,
    val sampleTimestamp: Long,
    val date: String,
    val heartRate: Int,
    val sourceBatchId: String,
    val updatedAt: Long,
)

data class CloudSyncSleepRecord(
    val id: String,
    val date: String,
    val startAt: Long,
    val endAt: Long,
    val durationMinutes: Int,
    val avgHeartRate: Double?,
    val heartRateVariabilityHint: Double?,
    val sourceBatchId: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class CloudSyncSnapshot(
    val settings: CloudSyncSettings?,
    val moodRecords: List<CloudSyncMoodRecord>,
    val heartRateSamples: List<CloudSyncHeartRateSample>,
    val sleepRecords: List<CloudSyncSleepRecord>,
    val serverTime: Long? = null,
)

data class CloudSyncResult(
    val success: Boolean,
    val message: String,
    val serverTime: Long? = null,
)

enum class SyncReason {
    LOGIN,
    SESSION_RESTORE,
    THEME_CHANGED,
    MOOD_CHANGED,
    DEMO_IMPORT,
    MANUAL,
}

fun deterministicHeartRateCloudId(
    sampleTimestamp: Long,
    heartRate: Int,
    sourceBatchId: String,
): String {
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest("$sampleTimestamp:$heartRate:$sourceBatchId".toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    return "hr_$digest"
}

fun cloudSyncErrorMessage(code: String): String =
    when (code) {
        "missing_token" -> "Sign in before syncing data."
        "unauthorized" -> "Session expired. Please sign in again."
        "network" -> "Unable to reach the cloud sync service."
        "server" -> "Cloud sync is temporarily unavailable."
        "malformed" -> "Cloud sync returned an unexpected response."
        "merge" -> "Some cloud records could not be merged."
        else -> "Cloud sync failed. Try again later."
    }
