package org.wit.vitasense.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.wit.vitasense.db.dao.AiChatMessageDao
import org.wit.vitasense.db.dao.AiChatSessionDao
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.dao.DailySummaryDao
import org.wit.vitasense.db.dao.HeartRateRawSampleDao
import org.wit.vitasense.db.dao.ImportLogDao
import org.wit.vitasense.db.dao.LocalUserDao
import org.wit.vitasense.db.dao.MoodRecordDao
import org.wit.vitasense.db.dao.RiskAssessmentDao
import org.wit.vitasense.db.dao.SleepRecordDao
import org.wit.vitasense.db.entity.AiChatMessageEntity
import org.wit.vitasense.db.entity.AiChatSessionEntity
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.ImportLogEntity
import org.wit.vitasense.db.entity.LocalUserEntity
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.db.entity.SleepRecordEntity

@Database(
    entities = [
        HeartRateRawSampleEntity::class,
        SleepRecordEntity::class,
        DailyPhysiologySummaryEntity::class,
        RiskAssessmentRecordEntity::class,
        MoodRecordEntity::class,
        AppSettingEntity::class,
        ImportLogEntity::class,
        LocalUserEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heartRateRawSampleDao(): HeartRateRawSampleDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun riskAssessmentDao(): RiskAssessmentDao
    abstract fun moodRecordDao(): MoodRecordDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun localUserDao(): LocalUserDao
    abstract fun aiChatSessionDao(): AiChatSessionDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
}
