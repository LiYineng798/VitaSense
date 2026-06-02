package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.wit.vitasense.model.deterministicHeartRateCloudId

@Entity(
    tableName = "heart_rate_raw_samples",
    indices = [
        Index(value = ["date"]),
        Index(value = ["sourceBatchId"]),
        Index(value = ["sampleTimestamp", "heartRate", "sourceBatchId"], unique = true),
    ],
)
data class HeartRateRawSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sampleTimestamp: Long,
    val date: String,
    val heartRate: Int,
    val sourceBatchId: String,
    val cloudId: String = deterministicHeartRateCloudId(sampleTimestamp, heartRate, sourceBatchId),
    val updatedAt: Long = sampleTimestamp,
)
