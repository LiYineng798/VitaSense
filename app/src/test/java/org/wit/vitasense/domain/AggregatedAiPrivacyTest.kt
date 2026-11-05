package org.wit.vitasense.domain

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.wit.vitasense.data.repository.*
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.model.*
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AggregatedAiPrivacyTest {
    @Test fun reportRequestExcludesPrivateNotesAndRetainsLocalFallback() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings=DefaultSettingsRepository(db.appSettingDao())
            var called=false; var payload=""
            val remote=object:AiChatRemoteDataSource {
                override suspend fun streamChat(config:AiProviderConfig,messages:List<AiChatMessage>,healthContext:Map<String,Any?>,onEvent:suspend(AiChatStreamEvent)->Unit) {
                    called=true; payload=messages.single().content
                    assertTrue(healthContext.isEmpty())
                    onEvent(AiChatStreamEvent.Error("offline","Network unavailable"))
                }
            }
            val insights=HealthInsightsRepository(db,settings,remote)
            assertTrue(insights.explainAggregates("Local aggregate").contains("Local analysis retained")); assertFalse(called)
            db.moodRecordDao().insert(MoodRecordEntity(date=LocalDate.now().toString(),moodType="HAPPY",moodGroup="positive",note="PRIVATE_JOURNAL_SENTINEL"))
            val report=WeeklyReportsRepository(insights).generate()
            assertFalse(report.aggregate.contains("PRIVATE_JOURNAL_SENTINEL"))
            settings.setAiProviderConfig(AiProviderConfig(apiKey="test-only",baseUrl="https://provider.invalid",model="test"))
            assertTrue(insights.explainAggregates(report.aggregate).contains("Local analysis retained"))
            assertTrue(called); assertFalse(payload.contains("PRIVATE_JOURNAL_SENTINEL")); assertTrue(payload.contains("HAPPY"))
        } finally { db.close() }
        Unit
    }
}
