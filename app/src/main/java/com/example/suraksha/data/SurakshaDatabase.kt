package com.example.suraksha.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EmergencyContact::class,
        AppSettings::class,
        SafetyRecord::class,
        IncidentLog::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SurakshaDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun safetyRecordDao(): SafetyRecordDao
    abstract fun incidentLogDao(): IncidentLogDao

    companion object {
        /** Migration from v1 → v2: adds the incident_logs table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `incident_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `details` TEXT NOT NULL
                    )
                """)
            }
        }
    }
}
