package com.example.suraksha.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        EmergencyContact::class,
        AppSettings::class,
        SafetyRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SurakshaDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun safetyRecordDao(): SafetyRecordDao
}
