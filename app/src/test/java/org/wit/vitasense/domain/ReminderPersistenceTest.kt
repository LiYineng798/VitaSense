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
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.data.repository.RemindersRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ReminderPersistenceTest {
    @Test fun initializationUpdatesClaimsAndWidgetPrivacy() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository=RemindersRepository(db)
            repository.initialize(); repository.initialize()
            assertEquals(4,repository.all().size)
            assertTrue(repository.all().none { it.enabled }); assertFalse(repository.widgetDetails())
            repository.setWidgetDetails(true); assertTrue(repository.widgetDetails())
            val first=repository.get("mood")!!
            val edited=repository.save(first.copy(enabled=true,minuteOfDay=500))
            assertTrue(edited.updatedAt>first.updatedAt); assertEquals(500,repository.get("mood")?.minuteOfDay)
            assertTrue(repository.claimNotification("mood",100))
            assertFalse(repository.claimNotification("mood",100)); assertFalse(repository.claimNotification("mood",90))
            assertTrue(repository.claimNotification("mood",101))
        } finally { db.close() }
        Unit
    }
}
