package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_sessions",
    indices = [
        Index(value = ["updatedAt"]),
    ],
)
data class AiChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isCurrent: Boolean = false,
)
