package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.db.dao.HeartRateRawSampleDao
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.dao.SleepRecordDao
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.SleepRecordEntity
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.CloudSyncSnapshot
import org.wit.vitasense.model.SyncReason
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.SettingsRepository

class DefaultCloudSyncRepositoryTest {
    @Test
    fun missingTokenReturnsErrorWithoutNetworkCall() = runBlocking {
        var networkCalls = 0
        val settings = FakeCloudSettingsRepository(authToken = "")
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                request = { _, _, _, _ ->
                    networkCalls++
                    NetworkResponse(200, "{}")
                },
            )

        val result = repository.bootstrapAfterLogin()

        assertFalse(result.success)
        assertEquals("Sign in before syncing data.", result.message)
        assertEquals(0, networkCalls)
        assertEquals("error", settings.syncStatus.value)
    }

    @Test
    fun bootstrapAppliesNewerCloudThemeAndReportsServerTime() = runBlocking {
        var mergedSnapshot: CloudSyncSnapshot? = null
        val settings = FakeCloudSettingsRepository(authToken = "token")
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                request = { method, path, token, body ->
                    assertEquals("GET", method)
                    assertEquals("/api/v1/sync/bootstrap", path)
                    assertEquals("token", token)
                    assertEquals(null, body)
                    NetworkResponse(
                        200,
                        """
                        {
                          "success": true,
                          "server_time": 1770000000123,
                          "settings": {"theme_mode": "dark", "theme_family": "rose_indigo", "updated_at": 1770000000000},
                          "mood_records": [],
                          "heart_rate_samples": [],
                          "sleep_records": []
                        }
                        """.trimIndent(),
                    )
                },
                snapshotMerger = { snapshot ->
                    mergedSnapshot = snapshot
                    snapshot.settings?.let {
                        settings.setThemeMode(ThemeMode.valueOf(it.themeMode.uppercase()))
                        settings.setThemeFamily(ThemeFamily.valueOf(it.themeFamily.uppercase()))
                    }
                },
                clock = { 1770000000999 },
            )

        val result = repository.bootstrapAfterLogin()

        assertTrue(result.success)
        assertEquals(1770000000123L, result.serverTime)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        assertEquals(ThemeFamily.ROSE_INDIGO, settings.themeFamily.value)
        assertEquals(1770000000999L, settings.lastSyncAt.value)
        assertEquals("synced", settings.syncStatus.value)
        assertEquals("rose_indigo", mergedSnapshot?.settings?.themeFamily)
    }

    @Test
    fun pushSendsLocalSnapshotWithNoAiKey() = runBlocking {
        var body: JSONObject? = null
        val settings = FakeCloudSettingsRepository(authToken = "token")
        settings.aiConfig.value = AiProviderConfig(apiKey = "sk-secret")
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                request = { method, path, _, rawBody ->
                    assertEquals("POST", method)
                    assertEquals("/api/v1/sync/push", path)
                    body = JSONObject(rawBody!!)
                    NetworkResponse(200, """{"success":true,"server_time":1770000000000}""")
                },
                localSnapshotProvider = {
                    JSONObject()
                        .put("settings", JSONObject().put("theme_mode", "light").put("theme_family", "default").put("updated_at", 1L))
                        .put("mood_records", emptyList<Any>())
                        .put("heart_rate_samples", emptyList<Any>())
                        .put("sleep_records", emptyList<Any>())
                },
            )

        val result = repository.pushLocalSnapshot(SyncReason.MANUAL)

        assertTrue(result.success)
        assertFalse(body.toString().contains("sk-secret"))
        assertFalse(body.toString().contains("api_key"))
    }

    @Test
    fun failedBootstrapStoresSyncErrorButDoesNotThrow() = runBlocking {
        val settings = FakeCloudSettingsRepository(authToken = "token")
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                request = { _, _, _, _ -> NetworkResponse(500, """{"success":false}""") },
            )

        val result = repository.bootstrapAfterLogin()

        assertFalse(result.success)
        assertEquals("Cloud sync is temporarily unavailable.", result.message)
        assertEquals("error", settings.syncStatus.value)
        assertEquals("Cloud sync is temporarily unavailable.", settings.syncError.value)
    }

    @Test
    fun accountSwitchBootstrapClearsLocalSyncDataBeforeMergingCloudSnapshot() = runBlocking {
        val settings = FakeCloudSettingsRepository(authToken = "token")
        settings.lastSyncAt.value = 1_880_000_000_000L
        val moodDao = FakeCloudMoodRecordDao()
        val heartRateDao = FakeCloudHeartRateRawSampleDao()
        val sleepDao = FakeCloudSleepRecordDao()
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                moodRecordDao = moodDao,
                heartRateDao = heartRateDao,
                sleepRecordDao = sleepDao,
                request = { _, _, _, _ ->
                    NetworkResponse(
                        200,
                        """
                        {
                          "success": true,
                          "server_time": 1770000000123,
                          "settings": {"theme_mode": "dark", "theme_family": "rose_indigo", "updated_at": 1770000000000},
                          "mood_records": [
                            {"id":"cloud-mood-b","date":"2026-06-03","mood_type":"HAPPY","mood_group":"POSITIVE","note":"new account","created_at":1770000000000,"updated_at":1770000000000,"deleted_at":null}
                          ],
                          "heart_rate_samples": [
                            {"id":"cloud-hr-b","sample_timestamp":1770000000000,"date":"2026-06-03","heart_rate":76,"source_batch_id":"cloud","updated_at":1770000000000}
                          ],
                          "sleep_records": [
                            {"id":"cloud-sleep-b","date":"2026-06-03","start_at":1769971200000,"end_at":1769996400000,"duration_minutes":420,"avg_heart_rate":61.0,"heart_rate_variability_hint":38.0,"source_batch_id":"cloud","updated_at":1770000000000,"deleted_at":null}
                          ]
                        }
                        """.trimIndent(),
                    )
                },
            )

        val result = repository.bootstrapForAccountSwitch()

        assertTrue(result.success)
        assertTrue(moodDao.cleared)
        assertTrue(heartRateDao.cleared)
        assertTrue(sleepDao.cleared)
        assertEquals(listOf("cloud-mood-b"), moodDao.rows.map { it.cloudId })
        assertEquals(listOf("cloud-hr-b"), heartRateDao.rows.map { it.cloudId })
        assertEquals(listOf("cloud-sleep-b"), sleepDao.rows.map { it.cloudId })
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        assertEquals(ThemeFamily.ROSE_INDIGO, settings.themeFamily.value)
    }

    @Test
    fun failedAccountSwitchBootstrapDoesNotClearLocalSyncData() = runBlocking {
        val settings = FakeCloudSettingsRepository(authToken = "token")
        val moodDao = FakeCloudMoodRecordDao()
        val heartRateDao = FakeCloudHeartRateRawSampleDao()
        val sleepDao = FakeCloudSleepRecordDao()
        moodDao.rows +=
            MoodRecordEntity(
                id = 1L,
                date = "2026-06-02",
                moodType = "CALM",
                moodGroup = "POSITIVE",
                note = "keep",
                cloudId = "local-mood",
            )
        heartRateDao.rows +=
            HeartRateRawSampleEntity(
                id = 1L,
                sampleTimestamp = 1770000000000,
                date = "2026-06-02",
                heartRate = 72,
                sourceBatchId = "local",
                cloudId = "local-hr",
            )
        sleepDao.rows +=
            SleepRecordEntity(
                id = 1L,
                date = "2026-06-02",
                startAt = 1769971200000,
                endAt = 1769996400000,
                durationMinutes = 420,
                sourceBatchId = "local",
                cloudId = "local-sleep",
            )
        val repository =
            DefaultCloudSyncRepository(
                baseUrl = "https://server.np5.top",
                settingsRepository = settings,
                moodRecordDao = moodDao,
                heartRateDao = heartRateDao,
                sleepRecordDao = sleepDao,
                request = { _, _, _, _ -> NetworkResponse(500, """{"success":false}""") },
            )

        val result = repository.bootstrapForAccountSwitch()

        assertFalse(result.success)
        assertFalse(moodDao.cleared)
        assertFalse(heartRateDao.cleared)
        assertFalse(sleepDao.cleared)
        assertEquals(listOf("local-mood"), moodDao.rows.map { it.cloudId })
        assertEquals(listOf("local-hr"), heartRateDao.rows.map { it.cloudId })
        assertEquals(listOf("local-sleep"), sleepDao.rows.map { it.cloudId })
    }
}

private class FakeCloudMoodRecordDao : MoodRecordDao {
    val rows = mutableListOf<MoodRecordEntity>()
    var cleared = false

    override suspend fun insert(record: MoodRecordEntity): Long {
        rows += record.copy(id = (rows.size + 1).toLong())
        return rows.last().id
    }

    override fun observeFiltered(group: String?, startDate: String?, endDate: String?): Flow<List<MoodRecordEntity>> = flowOf(rows)

    override fun observeActiveMoodRecords(): Flow<List<MoodRecordEntity>> = flowOf(rows.filter { it.deletedAt == null })

    override suspend fun getAllForSync(): List<MoodRecordEntity> = rows

    override suspend fun getByCloudId(cloudId: String): MoodRecordEntity? = rows.firstOrNull { it.cloudId == cloudId }

    override suspend fun upsertForSync(entity: MoodRecordEntity) {
        rows.removeAll { it.id == entity.id }
        rows += entity.copy(id = if (entity.id == 0L) (rows.size + 1).toLong() else entity.id)
    }

    override suspend fun markDeleted(id: Long, deletedAt: Long) = Unit

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun clear() {
        cleared = true
        rows.clear()
    }
}

private class FakeCloudHeartRateRawSampleDao : HeartRateRawSampleDao {
    val rows = mutableListOf<HeartRateRawSampleEntity>()
    var cleared = false

    override suspend fun insertAll(samples: List<HeartRateRawSampleEntity>): List<Long> {
        rows += samples
        return samples.indices.map { it.toLong() + 1L }
    }

    override suspend fun insertIgnore(entity: HeartRateRawSampleEntity): Long {
        if (rows.none { it.sampleTimestamp == entity.sampleTimestamp && it.heartRate == entity.heartRate && it.sourceBatchId == entity.sourceBatchId }) {
            rows += entity.copy(id = (rows.size + 1).toLong())
            return rows.last().id
        }
        return -1L
    }

    override fun observeLatest(): Flow<HeartRateRawSampleEntity?> = flowOf(rows.maxByOrNull { it.sampleTimestamp })

    override suspend fun getLatestOnce(): HeartRateRawSampleEntity? = rows.maxByOrNull { it.sampleTimestamp }

    override suspend fun getByDate(date: String): List<HeartRateRawSampleEntity> = rows.filter { it.date == date }

    override suspend fun getBetween(startAt: Long, endAt: Long): List<HeartRateRawSampleEntity> =
        rows.filter { it.sampleTimestamp in startAt..endAt }

    override suspend fun getAllForSync(): List<HeartRateRawSampleEntity> = rows

    override suspend fun findDuplicate(sampleTimestamp: Long, heartRate: Int, sourceBatchId: String): HeartRateRawSampleEntity? =
        rows.firstOrNull { it.sampleTimestamp == sampleTimestamp && it.heartRate == heartRate && it.sourceBatchId == sourceBatchId }

    override suspend fun getDistinctDates(): List<String> = rows.map { it.date }.distinct()

    override suspend fun clear() {
        cleared = true
        rows.clear()
    }
}

private class FakeCloudSleepRecordDao : SleepRecordDao {
    val rows = mutableListOf<SleepRecordEntity>()
    var cleared = false

    override suspend fun insert(record: SleepRecordEntity): Long {
        rows += record.copy(id = (rows.size + 1).toLong())
        return rows.last().id
    }

    override suspend fun getByDate(date: String): SleepRecordEntity? = rows.firstOrNull { it.date == date && it.deletedAt == null }

    override suspend fun getAnyByDate(date: String): SleepRecordEntity? = rows.firstOrNull { it.date == date }

    override fun observeLatest(): Flow<SleepRecordEntity?> = flowOf(rows.maxByOrNull { it.startAt })

    override suspend fun getAllActiveForSync(): List<SleepRecordEntity> = rows.filter { it.deletedAt == null }

    override suspend fun upsertForSync(entity: SleepRecordEntity) {
        rows.removeAll { it.id == entity.id || it.date == entity.date }
        rows += entity.copy(id = if (entity.id == 0L) (rows.size + 1).toLong() else entity.id)
    }

    override suspend fun getDistinctDates(): List<String> = rows.map { it.date }.distinct()

    override suspend fun clear() {
        cleared = true
        rows.clear()
    }
}

private class FakeCloudSettingsRepository(
    authToken: String,
) : SettingsRepository {
    val themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeFamily = MutableStateFlow(ThemeFamily.DEFAULT)
    val authBaseUrl = MutableStateFlow("")
    val authToken = MutableStateFlow(authToken)
    val currentUserJson = MutableStateFlow("")
    val currentUserId = MutableStateFlow<Long?>(null)
    val aiConfig = MutableStateFlow(AiProviderConfig())
    val latestAiAdvice = MutableStateFlow<AiAdvice?>(null)
    val latestAiAdviceGeneratedAt = MutableStateFlow<Long?>(null)
    val lastSyncAt = MutableStateFlow<Long?>(null)
    val syncStatus = MutableStateFlow("idle")
    val syncError = MutableStateFlow("")

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode
    override fun observeThemeFamily(): Flow<ThemeFamily> = themeFamily
    override fun observeAuthBaseUrl(): Flow<String> = authBaseUrl
    override fun observeAuthToken(): Flow<String> = this.authToken
    override fun observeCurrentUserJson(): Flow<String> = currentUserJson
    override fun observeCurrentUserId(): Flow<Long?> = currentUserId
    override fun observeAiProviderConfig(): Flow<AiProviderConfig> = aiConfig
    override fun observeLatestAiAdvice(): Flow<AiAdvice?> = latestAiAdvice
    override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> = latestAiAdviceGeneratedAt
    override fun observeLastSyncAt(): Flow<Long?> = lastSyncAt
    override fun observeSyncStatus(): Flow<String> = syncStatus
    override fun observeSyncError(): Flow<String> = syncError
    override suspend fun getThemeMode(): ThemeMode = themeMode.value
    override suspend fun getThemeFamily(): ThemeFamily = themeFamily.value
    override suspend fun getAuthBaseUrl(): String = authBaseUrl.value
    override suspend fun getAuthToken(): String = authToken.value
    override suspend fun getCurrentUserJson(): String = currentUserJson.value
    override suspend fun getCurrentUserId(): Long? = currentUserId.value
    override suspend fun getAiProviderConfig(): AiProviderConfig = aiConfig.value
    override suspend fun getLatestAiAdvice(): AiAdvice? = latestAiAdvice.value
    override suspend fun getLatestAiAdviceGeneratedAt(): Long? = latestAiAdviceGeneratedAt.value
    override suspend fun getLastSyncAt(): Long? = lastSyncAt.value
    override suspend fun getSyncStatus(): String = syncStatus.value
    override suspend fun getSyncError(): String = syncError.value
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
    override suspend fun setThemeFamily(family: ThemeFamily) {
        themeFamily.value = family
    }

    override suspend fun applySyncedTheme(
        mode: ThemeMode,
        family: ThemeFamily,
    ) {
        themeMode.value = mode
        themeFamily.value = family
    }
    override suspend fun setAuthBaseUrl(baseUrl: String) {
        authBaseUrl.value = baseUrl
    }
    override suspend fun setAuthToken(token: String?) {
        authToken.value = token.orEmpty()
    }
    override suspend fun setCurrentUserJson(userJson: String?) {
        currentUserJson.value = userJson.orEmpty()
    }
    override suspend fun setCurrentUserId(userId: Long?) {
        currentUserId.value = userId
    }
    override suspend fun setAiProviderConfig(config: AiProviderConfig) {
        aiConfig.value = config
    }
    override suspend fun setLatestAiAdvice(advice: AiAdvice, generatedAt: Long) {
        latestAiAdvice.value = advice
        latestAiAdviceGeneratedAt.value = generatedAt
    }
    override suspend fun setSyncStatus(status: String, error: String?, syncedAt: Long?) {
        syncStatus.value = status
        syncError.value = error.orEmpty()
        if (syncedAt != null) lastSyncAt.value = syncedAt
    }
}
