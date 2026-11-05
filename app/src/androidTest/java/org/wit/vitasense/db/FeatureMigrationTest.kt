package org.wit.vitasense.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.wit.vitasense.db.entity.AppSettingEntity
import org.wit.vitasense.db.entity.MoodRecordEntity

@RunWith(AndroidJUnit4::class)
class FeatureMigrationTest {
    @Test fun migrationFromFourPreservesExistingData() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-${java.util.UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            db.appSettingDao().upsert(AppSettingEntity("preserved","yes"))
            db.moodRecordDao().insert(MoodRecordEntity(date="2026-11-05",moodType="CALM",moodGroup="positive",note="private journal"))
            db.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                sqlite.execSQL("DROP TABLE trusted_contacts"); sqlite.execSQL("DROP TABLE reminder_schedules"); sqlite.version=4
            }
            db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(FeatureMigrations.MIGRATION_4_5,FeatureMigrations.MIGRATION_5_6).build()
            assertEquals("yes",db.appSettingDao().get("preserved")?.value)
            assertEquals("private journal",db.moodRecordDao().getLatestForDate("2026-11-05")?.note)
            assertTrue(db.reminderScheduleDao().all().isEmpty())
            assertNull(db.trustedContactDao().primary())
        } finally { db.close(); context.deleteDatabase(name) }
        Unit
    }
}
