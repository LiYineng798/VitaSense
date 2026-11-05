package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val displayName: String, val relationship: String, val phone: String, val email: String?,
    val isPrimary: Boolean = false, val consentConfirmed: Boolean,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
)
