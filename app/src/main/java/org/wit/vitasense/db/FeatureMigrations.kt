package org.wit.vitasense.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FeatureMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `trusted_contacts` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `relationship` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT, `isPrimary` INTEGER NOT NULL, `consentConfirmed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_schedules` (`kind` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, `repeatDays` INTEGER NOT NULL, `description` TEXT NOT NULL, `quietStart` INTEGER, `quietEnd` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`))")
        }
    }
}
