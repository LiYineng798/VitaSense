package org.wit.vitasense.domain

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.entity.TrustedContactEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContactPersistenceTest {
    @Test fun contactCrudAndPrimarySelection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.trustedContactDao()
            val repository = org.wit.vitasense.data.repository.FamilySafetyRepository(db,
                org.wit.vitasense.data.repository.DefaultSettingsRepository(db.appSettingDao()),
                org.wit.vitasense.data.repository.DefaultFamilyRepository(baseUrlProvider={ "https://example.invalid" },tokenProvider={ "" }))
            assertTrue(dao.observeAll().first().isEmpty())
            val one = TrustedContactEntity(displayName="Ava",relationship="Sister",phone="123456789",email=null,isPrimary=true,consentConfirmed=true)
            val two = one.copy(id="second",displayName="Ben",isPrimary=false)
            repository.save(one); repository.save(two)
            assertEquals(one.id,dao.primary()?.id)
            repository.save(one.copy(phone="987654321"))
            assertEquals("987654321",dao.primary()?.phone)
            repository.save(two.copy(isPrimary=true))
            assertEquals(two.id,dao.primary()?.id)
            repository.delete(two.id); repository.delete(one.id)
            assertTrue(dao.observeAll().first().isEmpty())
        } finally { db.close() }
        Unit
    }
}
