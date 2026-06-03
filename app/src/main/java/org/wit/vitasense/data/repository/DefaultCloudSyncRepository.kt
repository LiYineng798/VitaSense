package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.dao.DailySummaryDao
import org.wit.vitasense.db.dao.HeartRateRawSampleDao
import org.wit.vitasense.db.dao.ImportLogDao
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.dao.RiskAssessmentDao
import org.wit.vitasense.db.dao.SleepRecordDao
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.SleepRecordEntity
import org.wit.vitasense.domain.HealthRecomputeEngine
import org.wit.vitasense.model.CloudSyncHeartRateSample
import org.wit.vitasense.model.CloudSyncMoodRecord
import org.wit.vitasense.model.CloudSyncResult
import org.wit.vitasense.model.CloudSyncSettings
import org.wit.vitasense.model.CloudSyncSleepRecord
import org.wit.vitasense.model.CloudSyncSnapshot
import org.wit.vitasense.model.SyncReason
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.cloudSyncErrorMessage
import org.wit.vitasense.repository.CloudSyncRepository
import org.wit.vitasense.repository.SettingsRepository

data class NetworkResponse(
    val statusCode: Int,
    val body: String,
)

class DefaultCloudSyncRepository(
    private val baseUrl: String,
    private val settingsRepository: SettingsRepository,
    @Suppress("unused") private val database: AppDatabase? = null,
    private val moodRecordDao: MoodRecordDao? = null,
    private val heartRateDao: HeartRateRawSampleDao? = null,
    private val sleepRecordDao: SleepRecordDao? = null,
    private val dailySummaryDao: DailySummaryDao? = null,
    private val riskAssessmentDao: RiskAssessmentDao? = null,
    private val importLogDao: ImportLogDao? = null,
    private val recomputeEngine: HealthRecomputeEngine? = null,
    private val request: (suspend (method: String, path: String, token: String, body: String?) -> NetworkResponse)? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val localSnapshotProvider: (suspend (SyncReason) -> JSONObject)? = null,
    private val snapshotMerger: (suspend (CloudSyncSnapshot) -> Unit)? = null,
) : CloudSyncRepository {
    override suspend fun bootstrapAfterLogin(): CloudSyncResult {
        return bootstrap(resetLocalDataFirst = false)
    }

    override suspend fun bootstrapForAccountSwitch(): CloudSyncResult {
        return bootstrap(resetLocalDataFirst = true)
    }

    private suspend fun bootstrap(resetLocalDataFirst: Boolean): CloudSyncResult {
        val token = settingsRepository.getAuthToken()
        if (token.isBlank()) return fail("missing_token")
        return runCatching {
            settingsRepository.setSyncStatus("syncing")
            val response = executeRequest("GET", "/api/v1/sync/bootstrap", token, null)
            if (response.statusCode == 401) return fail("unauthorized")
            if (response.statusCode !in 200..299) return fail("server")
            val snapshot = parseBootstrapResponse(response.body)
            if (resetLocalDataFirst) {
                clearSyncedLocalData()
            }
            mergeSnapshot(snapshot, forceSettings = resetLocalDataFirst)
            recomputeEngine?.recomputeAllDates()
            settingsRepository.setSyncStatus("synced", syncedAt = clock())
            CloudSyncResult(true, "Cloud sync complete.", snapshot.serverTime)
        }.getOrElse {
            fail("network")
        }
    }

    override suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult {
        val token = settingsRepository.getAuthToken()
        if (token.isBlank()) return fail("missing_token")
        return runCatching {
            settingsRepository.setSyncStatus("syncing")
            val response = executeRequest("POST", "/api/v1/sync/push", token, buildPushPayload(reason).toString())
            if (response.statusCode == 401) return fail("unauthorized")
            if (response.statusCode !in 200..299) return fail("server")
            settingsRepository.setSyncStatus("synced", syncedAt = clock())
            CloudSyncResult(true, "Cloud sync complete.")
        }.getOrElse {
            fail("network")
        }
    }

    override suspend fun syncNow(): CloudSyncResult {
        val bootstrap = bootstrapAfterLogin()
        if (!bootstrap.success) return bootstrap
        return pushLocalSnapshot(SyncReason.MANUAL)
    }

    private suspend fun fail(code: String): CloudSyncResult {
        val message = cloudSyncErrorMessage(code)
        settingsRepository.setSyncStatus("error", error = message)
        return CloudSyncResult(false, message)
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        token: String,
        body: String?,
    ): NetworkResponse =
        request?.invoke(method, path, token, body)
            ?: defaultRequest(method, baseUrl.trim().removeSuffix("/") + path, token, body)

    private suspend fun buildPushPayload(reason: SyncReason): JSONObject {
        localSnapshotProvider?.let { return it(reason) }
        val payload = JSONObject()
        payload.put(
            "settings",
            JSONObject()
                .put("theme_mode", settingsRepository.getThemeMode().name.lowercase())
                .put("theme_family", settingsRepository.getThemeFamily().name.lowercase())
                .put("updated_at", clock()),
        )
        payload.put(
            "mood_records",
            JSONArray(
                requireNotNull(moodRecordDao) { "Mood DAO is required for snapshot sync." }
                    .getAllForSync()
                    .map { it.toSyncJson() },
            ),
        )
        payload.put(
            "heart_rate_samples",
            JSONArray(
                requireNotNull(heartRateDao) { "Heart-rate DAO is required for snapshot sync." }
                    .getAllForSync()
                    .map { it.toSyncJson() },
            ),
        )
        payload.put(
            "sleep_records",
            JSONArray(
                requireNotNull(sleepRecordDao) { "Sleep DAO is required for snapshot sync." }
                    .getAllActiveForSync()
                    .map { it.toSyncJson() },
            ),
        )
        return payload
    }

    private suspend fun mergeSnapshot(
        snapshot: CloudSyncSnapshot,
        forceSettings: Boolean = false,
    ) {
        snapshotMerger?.let {
            it(snapshot)
            return
        }
        snapshot.settings?.let { settings ->
            if (forceSettings || settings.updatedAt >= (settingsRepository.getLastSyncAt() ?: 0L)) {
                settingsRepository.applySyncedTheme(
                    mode = ThemeMode.valueOf(settings.themeMode.uppercase()),
                    family = ThemeFamily.valueOf(settings.themeFamily.uppercase()),
                )
            }
        }
        snapshot.moodRecords.forEach { mergeMood(it) }
        snapshot.heartRateSamples.forEach { mergeHeartRate(it) }
        snapshot.sleepRecords.forEach { mergeSleep(it) }
    }

    private suspend fun clearSyncedLocalData() {
        requireNotNull(moodRecordDao) { "Mood DAO is required for account-switch sync." }.clear()
        requireNotNull(heartRateDao) { "Heart-rate DAO is required for account-switch sync." }.clear()
        requireNotNull(sleepRecordDao) { "Sleep DAO is required for account-switch sync." }.clear()
        dailySummaryDao?.clear()
        riskAssessmentDao?.clear()
        importLogDao?.clear()
    }

    private suspend fun mergeMood(record: CloudSyncMoodRecord) {
        val dao = requireNotNull(moodRecordDao) { "Mood DAO is required for bootstrap sync." }
        val existing = dao.getByCloudId(record.id)
        if (existing == null || record.updatedAt > existing.updatedAt || (record.deletedAt ?: 0L) > (existing.deletedAt ?: 0L)) {
            dao.upsertForSync(record.toEntity(existing?.id ?: 0L))
        }
    }

    private suspend fun mergeHeartRate(sample: CloudSyncHeartRateSample) {
        requireNotNull(heartRateDao) { "Heart-rate DAO is required for bootstrap sync." }
            .insertIgnore(sample.toEntity())
    }

    private suspend fun mergeSleep(record: CloudSyncSleepRecord) {
        val dao = requireNotNull(sleepRecordDao) { "Sleep DAO is required for bootstrap sync." }
        val existing = dao.getAnyByDate(record.date)
        val shouldKeepCloud =
            existing == null ||
                record.updatedAt > existing.updatedAt ||
                (record.updatedAt == existing.updatedAt && record.durationMinutes > existing.durationMinutes)
        if (shouldKeepCloud) {
            dao.upsertForSync(record.toEntity(existing?.id ?: 0L))
        }
    }

    private fun parseBootstrapResponse(raw: String): CloudSyncSnapshot {
        val obj = JSONObject(raw.ifBlank { "{}" })
        val settingsObj = obj.optJSONObject("settings")
        return CloudSyncSnapshot(
            serverTime = obj.optLong("server_time").takeIf { it > 0L },
            settings =
                settingsObj?.let {
                    CloudSyncSettings(
                        themeMode = it.getString("theme_mode"),
                        themeFamily = it.getString("theme_family"),
                        updatedAt = it.getLong("updated_at"),
                    )
                },
            moodRecords = obj.optJSONArray("mood_records").toMoodRecords(),
            heartRateSamples = obj.optJSONArray("heart_rate_samples").toHeartRateSamples(),
            sleepRecords = obj.optJSONArray("sleep_records").toSleepRecords(),
        )
    }

    private fun JSONArray?.toMoodRecords(): List<CloudSyncMoodRecord> =
        mapObjects { item ->
            CloudSyncMoodRecord(
                id = item.getString("id"),
                date = item.getString("date"),
                moodType = item.getString("mood_type"),
                moodGroup = item.getString("mood_group"),
                note = item.optString("note").takeIf { it.isNotBlank() },
                createdAt = item.getLong("created_at"),
                updatedAt = item.getLong("updated_at"),
                deletedAt = item.optNullableLong("deleted_at"),
            )
        }

    private fun JSONArray?.toHeartRateSamples(): List<CloudSyncHeartRateSample> =
        mapObjects { item ->
            CloudSyncHeartRateSample(
                id = item.getString("id"),
                sampleTimestamp = item.getLong("sample_timestamp"),
                date = item.getString("date"),
                heartRate = item.getInt("heart_rate"),
                sourceBatchId = item.getString("source_batch_id"),
                updatedAt = item.getLong("updated_at"),
            )
        }

    private fun JSONArray?.toSleepRecords(): List<CloudSyncSleepRecord> =
        mapObjects { item ->
            CloudSyncSleepRecord(
                id = item.getString("id"),
                date = item.getString("date"),
                startAt = item.getLong("start_at"),
                endAt = item.getLong("end_at"),
                durationMinutes = item.getInt("duration_minutes"),
                avgHeartRate = item.optNullableDouble("avg_heart_rate"),
                heartRateVariabilityHint = item.optNullableDouble("heart_rate_variability_hint"),
                sourceBatchId = item.getString("source_batch_id"),
                updatedAt = item.getLong("updated_at"),
                deletedAt = item.optNullableLong("deleted_at"),
            )
        }

    private fun MoodRecordEntity.toSyncJson(): JSONObject =
        JSONObject()
            .put("id", cloudId)
            .put("date", date)
            .put("mood_type", moodType)
            .put("mood_group", moodGroup)
            .put("note", note)
            .put("created_at", createdAt)
            .put("updated_at", updatedAt)
            .put("deleted_at", deletedAt)

    private fun HeartRateRawSampleEntity.toSyncJson(): JSONObject =
        JSONObject()
            .put("id", cloudId)
            .put("sample_timestamp", sampleTimestamp)
            .put("date", date)
            .put("heart_rate", heartRate)
            .put("source_batch_id", sourceBatchId)
            .put("updated_at", updatedAt)

    private fun SleepRecordEntity.toSyncJson(): JSONObject =
        JSONObject()
            .put("id", cloudId)
            .put("date", date)
            .put("start_at", startAt)
            .put("end_at", endAt)
            .put("duration_minutes", durationMinutes)
            .put("avg_heart_rate", avgHeartRate)
            .put("heart_rate_variability_hint", heartRateVariabilityHint)
            .put("source_batch_id", sourceBatchId)
            .put("updated_at", updatedAt)
            .put("deleted_at", deletedAt)

    private fun CloudSyncMoodRecord.toEntity(localId: Long): MoodRecordEntity =
        MoodRecordEntity(
            id = localId,
            date = date,
            moodType = moodType,
            moodGroup = moodGroup,
            note = note,
            createdAt = createdAt,
            cloudId = id,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

    private fun CloudSyncHeartRateSample.toEntity(): HeartRateRawSampleEntity =
        HeartRateRawSampleEntity(
            sampleTimestamp = sampleTimestamp,
            date = date,
            heartRate = heartRate,
            sourceBatchId = sourceBatchId,
            cloudId = id,
            updatedAt = updatedAt,
        )

    private fun CloudSyncSleepRecord.toEntity(localId: Long): SleepRecordEntity =
        SleepRecordEntity(
            id = localId,
            date = date,
            startAt = startAt,
            endAt = endAt,
            durationMinutes = durationMinutes,
            avgHeartRate = avgHeartRate,
            heartRateVariabilityHint = heartRateVariabilityHint,
            sourceBatchId = sourceBatchId,
            cloudId = id,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { block(getJSONObject(it)) }
    }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name)

}

private suspend fun defaultRequest(
    method: String,
    urlString: String,
    token: String,
    body: String?,
): NetworkResponse =
    withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            NetworkResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } catch (error: IOException) {
            throw error
        } finally {
            connection.disconnect()
        }
    }
