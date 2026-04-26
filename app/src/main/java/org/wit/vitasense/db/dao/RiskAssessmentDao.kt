package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity

@Dao
interface RiskAssessmentDao {
    @Upsert
    suspend fun upsert(record: RiskAssessmentRecordEntity)

    @Query("SELECT * FROM risk_assessment_records ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<RiskAssessmentRecordEntity?>

    @Query("SELECT * FROM risk_assessment_records ORDER BY date DESC LIMIT 1")
    suspend fun getLatestOnce(): RiskAssessmentRecordEntity?

    @Query("SELECT * FROM risk_assessment_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun observeRange(startDate: String, endDate: String): Flow<List<RiskAssessmentRecordEntity>>

    @Query("SELECT * FROM risk_assessment_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRange(startDate: String, endDate: String): List<RiskAssessmentRecordEntity>

    @Query("DELETE FROM risk_assessment_records")
    suspend fun clear()
}
