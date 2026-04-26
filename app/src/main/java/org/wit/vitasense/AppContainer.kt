package org.wit.vitasense

import android.content.Context
import androidx.room.Room
import org.wit.vitasense.data.importer.DemoImportProvider
import org.wit.vitasense.data.repository.DefaultHealthRepository
import org.wit.vitasense.data.repository.DefaultMoodRepository
import org.wit.vitasense.data.repository.DefaultSettingsRepository
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.domain.DerivedContentSync
import org.wit.vitasense.domain.HealthRecomputeEngine
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository
import org.wit.vitasense.repository.SettingsRepository

class AppContainer(
    val context: Context,
) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "vitasense.db").build()
    }

    private val demoImportProvider: DemoImportProvider by lazy {
        DemoImportProvider()
    }

    private val recomputeEngine: HealthRecomputeEngine by lazy {
        HealthRecomputeEngine(
            heartRateDao = database.heartRateRawSampleDao(),
            sleepRecordDao = database.sleepRecordDao(),
            dailySummaryDao = database.dailySummaryDao(),
            riskAssessmentDao = database.riskAssessmentDao(),
        )
    }

    val derivedContentSync: DerivedContentSync by lazy {
        DerivedContentSync(
            appSettingDao = database.appSettingDao(),
            recompute = recomputeEngine::recomputeAllDates,
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(database.appSettingDao())
    }

    val healthRepository: HealthRepository by lazy {
        DefaultHealthRepository(
            database = database,
            heartRateDao = database.heartRateRawSampleDao(),
            sleepRecordDao = database.sleepRecordDao(),
            dailySummaryDao = database.dailySummaryDao(),
            riskAssessmentDao = database.riskAssessmentDao(),
            moodRecordDao = database.moodRecordDao(),
            importLogDao = database.importLogDao(),
            demoImportProvider = demoImportProvider,
            recomputeEngine = recomputeEngine,
        )
    }

    val moodRepository: MoodRepository by lazy {
        DefaultMoodRepository(database.moodRecordDao())
    }
}
