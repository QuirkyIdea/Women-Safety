package com.example.suraksha.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getAllActiveContacts(): Flow<List<EmergencyContact>>
    
    @Query("SELECT * FROM emergency_contacts WHERE isActive = 1 ORDER BY createdAt ASC LIMIT 3")
    fun getActiveContacts(): Flow<List<EmergencyContact>>
    
    @Insert
    suspend fun insertContact(contact: EmergencyContact): Long
    
    @Update
    suspend fun updateContact(contact: EmergencyContact)
    
    @Delete
    suspend fun deleteContact(contact: EmergencyContact)
    
    @Query("UPDATE emergency_contacts SET isActive = 0 WHERE id = :contactId")
    suspend fun deactivateContact(contactId: Long)
    
    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE isActive = 1")
    suspend fun getActiveContactCount(): Int
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSettings?
    
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSettings)
    
    @Update
    suspend fun updateSetting(setting: AppSettings)
    
    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}

@Dao
interface SafetyRecordDao {
    @Query("SELECT * FROM safety_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<SafetyRecord>>
    
    @Query("SELECT * FROM safety_records WHERE type = :type ORDER BY timestamp DESC")
    fun getRecordsByType(type: String): Flow<List<SafetyRecord>>
    
    @Insert
    suspend fun insertRecord(record: SafetyRecord): Long
    
    @Update
    suspend fun updateRecord(record: SafetyRecord)
    
    @Query("UPDATE safety_records SET isResolved = 1 WHERE id = :recordId")
    suspend fun markRecordResolved(recordId: Long)
    
    @Query("SELECT * FROM safety_records WHERE isResolved = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnresolvedRecord(): SafetyRecord?
}
