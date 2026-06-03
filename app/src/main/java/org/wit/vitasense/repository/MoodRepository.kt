package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodType

interface MoodRepository {
    fun observeMoodRecords(filter: MoodFilter): Flow<List<MoodRecordEntity>>

    suspend fun addMood(
        date: String,
        moodType: MoodType,
        note: String?,
    )

    suspend fun deleteMood(id: Long)

    suspend fun getLatestMoodForDate(date: String): MoodRecordEntity?
}
