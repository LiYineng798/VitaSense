package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_physiology_summary",
    indices = [Index(value = ["date"], unique = true)],
)
data class DailyPhysiologySummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val avgHeartRate: Double?,
    val restingHeartRate: Double?,
    val rmssd: Double?,
    val sdnn: Double?,
    val sleepDurationMinutes: Int?,
    val baselineRestingHeartRate: Double?,
    val baselineRmssd: Double?,
    val baselineAvgHeartRate: Double?,
    val anomalyFlags: String,
    val summaryText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
