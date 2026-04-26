package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.DemoBundleInfo
import org.wit.vitasense.model.ImportOperationResult

interface HealthRepository {
    fun observeLatestHeartRate(): Flow<HeartRateRawSampleEntity?>

    fun observeLatestSummary(): Flow<DailyPhysiologySummaryEntity?>

    fun observeLatestRisk(): Flow<RiskAssessmentRecordEntity?>

    fun observeSummaries(days: Int): Flow<List<DailyPhysiologySummaryEntity>>

    fun observeRisks(days: Int): Flow<List<RiskAssessmentRecordEntity>>

    suspend fun getAvailableDemoBundles(): List<DemoBundleInfo>

    suspend fun importDemoBundle(bundleId: String): ImportOperationResult

    suspend fun importRawJson(
        raw: String,
        sourceName: String = "manual",
    ): ImportOperationResult

    suspend fun clearAllData()
}
