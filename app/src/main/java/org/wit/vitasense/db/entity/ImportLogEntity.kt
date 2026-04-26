package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_logs",
    indices = [
        Index(value = ["batchId"], unique = true),
        Index(value = ["importedAt"]),
        Index(value = ["checksum"]),
    ],
)
data class ImportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val sourceType: String,
    val sourceName: String,
    val importedAt: Long = System.currentTimeMillis(),
    val status: String,
    val message: String,
    val rawCount: Int,
    val insertedCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val checksum: String,
)
