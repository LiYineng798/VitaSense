package org.wit.vitasense

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.wit.vitasense.data.importer.DemoImportProvider
import org.wit.vitasense.data.repository.DefaultAiAdviceRepository
import org.wit.vitasense.data.repository.DefaultAuthRepository
import org.wit.vitasense.data.repository.DefaultCloudSyncRepository
import org.wit.vitasense.data.repository.DefaultHealthRepository
import org.wit.vitasense.data.repository.DefaultMoodRepository
import org.wit.vitasense.data.repository.DefaultSettingsRepository
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.domain.DerivedContentSync
import org.wit.vitasense.domain.HealthRecomputeEngine
import org.wit.vitasense.repository.AiAdviceRepository
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.CloudSyncRepository
import org.wit.vitasense.repository.HealthRepository
import org.wit.vitasense.repository.MoodRepository
import org.wit.vitasense.repository.SettingsRepository

class AppContainer(
    val context: Context,
) {
    private companion object {
        const val DEFAULT_AUTH_BASE_URL = "https://server.np5.top"
        const val DEFAULT_AI_PROXY_BASE_URL = "https://server.np5.top"
    }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "vitasense.db")
            .fallbackToDestructiveMigration()
            .build()
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
        DefaultSettingsRepository(
            appSettingDao = database.appSettingDao(),
            cloudSyncRepositoryProvider = { cloudSyncRepository },
        )
            .also { repository ->
                runBlocking {
                    if (repository.getAuthBaseUrl().isBlank()) {
                        repository.setAuthBaseUrl(DEFAULT_AUTH_BASE_URL)
                    }
                }
            }
    }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            settingsRepository = settingsRepository,
            cloudSyncRepository = cloudSyncRepository,
        )
    }

    val cloudSyncRepository: CloudSyncRepository by lazy {
        DefaultCloudSyncRepository(
            baseUrl = DEFAULT_AUTH_BASE_URL,
            settingsRepository = settingsRepository,
            database = database,
            moodRecordDao = database.moodRecordDao(),
            heartRateDao = database.heartRateRawSampleDao(),
            sleepRecordDao = database.sleepRecordDao(),
            dailySummaryDao = database.dailySummaryDao(),
            riskAssessmentDao = database.riskAssessmentDao(),
            importLogDao = database.importLogDao(),
            recomputeEngine = recomputeEngine,
        )
    }

    val aiAdviceRepository: AiAdviceRepository by lazy {
        DefaultAiAdviceRepository(proxyBaseUrl = DEFAULT_AI_PROXY_BASE_URL)
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
            cloudSyncRepositoryProvider = { cloudSyncRepository },
        )
    }

    val moodRepository: MoodRepository by lazy {
        DefaultMoodRepository(
            moodRecordDao = database.moodRecordDao(),
            cloudSyncRepositoryProvider = { cloudSyncRepository },
        )
    }
}
