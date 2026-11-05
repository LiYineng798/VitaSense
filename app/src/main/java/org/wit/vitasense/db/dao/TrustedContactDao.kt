package org.wit.vitasense.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.TrustedContactEntity

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY isPrimary DESC, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<TrustedContactEntity>>
    @Query("SELECT * FROM trusted_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun primary(): TrustedContactEntity?
    @Upsert suspend fun upsert(contact: TrustedContactEntity)
    @Query("DELETE FROM trusted_contacts WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE trusted_contacts SET isPrimary = 0, updatedAt = :now WHERE isPrimary = 1") suspend fun clearPrimary(now: Long)
}
