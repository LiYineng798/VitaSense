package org.wit.vitasense.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.wit.vitasense.widget.WidgetUpdates
import org.wit.vitasense.db.entity.MoodRecordEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WidgetInvalidationTest {
    @Test fun moodDatabaseChangeRequestsWidgetRefresh() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase
            val signal=CountDownLatch(1)
            WidgetUpdates.observe(context,db) { signal.countDown() }
            db.moodRecordDao().insert(MoodRecordEntity(date="2026-11-05",moodType="CALM",moodGroup="positive",note=null))
            assertTrue("Widget refresh should follow a mood write",signal.await(5,TimeUnit.SECONDS))
        } finally { db.close() }
        Unit
    }
}
