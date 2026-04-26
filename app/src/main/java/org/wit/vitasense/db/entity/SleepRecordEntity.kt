package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_records",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["startAt"]),
        Index(value = ["endAt"]),
    ],
)
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startAt: Long,
    val endAt: Long,
    val durationMinutes: Int,
    val avgHeartRate: Double? = null,
    val heartRateVariabilityHint: Double? = null,
    val sourceBatchId: String,
)
