package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_schedules")
data class ReminderScheduleEntity(
    @PrimaryKey val kind: String,
    val enabled: Boolean = false,
    val minuteOfDay: Int = 20*60,
    /** Monday = bit 0, Sunday = bit 6. */
    val repeatDays: Int = 127,
    val description: String,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
