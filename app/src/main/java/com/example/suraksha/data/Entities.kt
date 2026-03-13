package com.example.suraksha.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "safety_records")
data class SafetyRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "SOS", "FAKE_CALL", "TIMER", "RECORDING"
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val recordingPath: String? = null,
    val contactsNotified: String? = null, // JSON array of contact IDs
    val isResolved: Boolean = false
)
