package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_records",
    indices = [
        Index(value = ["date"]),
        Index(value = ["moodGroup"]),
        Index(value = ["moodType"]),
        Index(value = ["createdAt"]),
    ],
)
data class MoodRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val moodType: String,
    val moodGroup: String,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
)
