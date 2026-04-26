package org.wit.vitasense.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.wit.vitasense.data.importer.DemoImportProvider
import org.wit.vitasense.data.importer.ImportBundleParser
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.dao.DailySummaryDao
import org.wit.vitasense.db.dao.HeartRateRawSampleDao
import org.wit.vitasense.db.dao.ImportLogDao
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.dao.RiskAssessmentDao
import org.wit.vitasense.db.dao.SleepRecordDao
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.ImportLogEntity
import org.wit.vitasense.db.entity.SleepRecordEntity
import org.wit.vitasense.domain.HealthRecomputeEngine
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult
import org.wit.vitasense.model.ImportStatus
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.util.DateUtils

class DefaultHealthRepository(
    private val database: AppDatabase,
    private val heartRateDao: HeartRateRawSampleDao,
    private val sleepRecordDao: SleepRecordDao,
    private val dailySummaryDao: DailySummaryDao,
    private val riskAssessmentDao: RiskAssessmentDao,
    private val moodRecordDao: MoodRecordDao,
    private val importLogDao: ImportLogDao,
    private val demoImportProvider: DemoImportProvider,
    private val recomputeEngine: HealthRecomputeEngine,
) : HealthRepository {
    override fun observeLatestHeartRate() = heartRateDao.observeLatest()

    override fun observeLatestSummary() = dailySummaryDao.observeLatest()

    override fun observeLatestRisk() = riskAssessmentDao.observeLatest()

    override fun observeSummaries(days: Int): Flow<List<org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity>> {
        return dailySummaryDao.observeLatest().map { latest ->
            val end = latest?.date ?: return@map emptyList()
            val start = DateUtils.parseDate(end).minusDays((days - 1).toLong()).toString()
            dailySummaryDao.getRange(start, end)
        }
    }

    override fun observeRisks(days: Int): Flow<List<org.wit.vitasense.db.entity.RiskAssessmentRecordEntity>> {
        return riskAssessmentDao.observeLatest().map { latest ->
            val end = latest?.date ?: return@map emptyList()
            val start = DateUtils.parseDate(end).minusDays((days - 1).toLong()).toString()
            riskAssessmentDao.getRange(start, end)
        }
    }

    override suspend fun getAvailableDemoBundles(): List<DemoBundleInfo> = demoImportProvider.availableBundles()

    override suspend fun importDemoBundle(bundleId: String): ImportOperationResult =
        importRawJson(
            raw = demoImportProvider.rawBundle(bundleId),
            sourceName = bundleId,
        )

    override suspend fun importRawJson(
        raw: String,
        sourceName: String,
    ): ImportOperationResult =
        withContext(Dispatchers.IO) {
            val bundle = ImportBundleParser.parse(raw)
            val existingLog = importLogDao.getByBatchId(bundle.batchId)
            if (existingLog != null) {
                return@withContext ImportOperationResult(
                    status = ImportStatus.FAILED,
                    message = "This batch has already been imported. Duplicate data was skipped.",
                    rawCount = bundle.heartRateSamples.size + bundle.sleepRecords.size,
                    insertedCount = 0,
                    duplicateCount = bundle.heartRateSamples.size + bundle.sleepRecords.size,
                    invalidCount = 0,
                )
            }

            val normalizedByTimestamp = linkedMapOf<Long, Int>()
            var duplicateCount = 0
            var invalidCount = 0

            bundle.heartRateSamples.forEach { sample ->
                try {
                    val timestamp = DateUtils.parseOffsetDateTime(sample.timestamp)
                    if (sample.heartRate !in 35..220) {
                        invalidCount++
                        return@forEach
                    }
                    if (normalizedByTimestamp.containsKey(timestamp)) {
                        duplicateCount++
                    }
                    normalizedByTimestamp[timestamp] = sample.heartRate
                } catch (_: Exception) {
                    invalidCount++
                }
            }

            val heartRateEntities =
                normalizedByTimestamp.entries.sortedBy { it.key }
                    .map { (timestamp, heartRate) ->
                        HeartRateRawSampleEntity(
                            sampleTimestamp = timestamp,
                            date = DateUtils.formatDate(timestamp),
                            heartRate = heartRate,
                            sourceBatchId = bundle.batchId,
                        )
                    }
            val insertResults = heartRateDao.insertAll(heartRateEntities)
            val dbDuplicates = insertResults.count { it == -1L }

            val sleepRecordsByDate = linkedMapOf<String, SleepRecordEntity>()
            bundle.sleepRecords.forEach { record ->
                try {
                    val startAt = DateUtils.parseOffsetDateTime(record.startAt)
                    val endAt = DateUtils.parseOffsetDateTime(record.endAt)
                    val duration = ((endAt - startAt) / 60_000L).toInt()
                    if (duration !in 60..900 || endAt <= startAt) {
                        invalidCount++
                        return@forEach
                    }
                    val entity =
                        SleepRecordEntity(
                            date = record.date,
                            startAt = startAt,
                            endAt = endAt,
                            durationMinutes = duration,
                            sourceBatchId = bundle.batchId,
                        )
                    val existing = sleepRecordsByDate[record.date]
                    if (existing == null || entity.durationMinutes > existing.durationMinutes) {
                        sleepRecordsByDate[record.date] = entity
                    } else {
                        duplicateCount++
                    }
                } catch (_: Exception) {
                    invalidCount++
                }
            }

            sleepRecordsByDate.values.forEach { sleepRecordDao.insert(it) }
            recomputeEngine.recomputeAllDates()

            val insertedCount = insertResults.count { it != -1L } + sleepRecordsByDate.size
            val result =
                ImportOperationResult(
                    status = if (invalidCount > 0 || duplicateCount > 0 || dbDuplicates > 0) ImportStatus.PARTIAL_FAILED else ImportStatus.SUCCESS,
                    message =
                        if (invalidCount > 0 || duplicateCount > 0 || dbDuplicates > 0) {
                            "Import finished. Some invalid or duplicate data was filtered out."
                        } else {
                            "Import succeeded and recomputation is complete."
                        },
                    rawCount = bundle.heartRateSamples.size + bundle.sleepRecords.size,
                    insertedCount = insertedCount,
                    duplicateCount = duplicateCount + dbDuplicates,
                    invalidCount = invalidCount,
                )

            importLogDao.insert(
                ImportLogEntity(
                    batchId = bundle.batchId,
                    sourceType = bundle.sourceType,
                    sourceName = sourceName,
                    status = result.status.name.lowercase(),
                    message = result.message,
                    rawCount = result.rawCount,
                    insertedCount = result.insertedCount,
                    duplicateCount = result.duplicateCount,
                    invalidCount = result.invalidCount,
                    checksum = DateUtils.checksum(raw),
                ),
            )

            result
        }

    override suspend fun clearAllData() =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                moodRecordDao.clear()
                riskAssessmentDao.clear()
                dailySummaryDao.clear()
                sleepRecordDao.clear()
                heartRateDao.clear()
                importLogDao.clear()
            }
        }
}
