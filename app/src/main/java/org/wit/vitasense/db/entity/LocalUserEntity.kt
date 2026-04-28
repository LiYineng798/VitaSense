package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["username"], unique = true),
    ],
)
data class LocalUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val email: String,
    val username: String,
    val passwordHash: String,
    val birthDate: String,
    val createdAt: Long = System.currentTimeMillis(),
)
