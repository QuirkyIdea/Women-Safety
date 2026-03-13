package com.example.suraksha.data

import kotlinx.coroutines.flow.Flow

class SurakshaRepository(
    private val emergencyContactDao: EmergencyContactDao,
    private val appSettingsDao: AppSettingsDao,
    private val safetyRecordDao: SafetyRecordDao
) {
    
    // Emergency Contacts
    fun getActiveContacts(): Flow<List<EmergencyContact>> = 
        emergencyContactDao.getActiveContacts()
    
    fun getAllActiveContacts(): Flow<List<EmergencyContact>> = 
        emergencyContactDao.getAllActiveContacts()
    
    suspend fun addContact(name: String, phoneNumber: String): Long {
        val contact = EmergencyContact(name = name, phoneNumber = phoneNumber)
        return emergencyContactDao.insertContact(contact)
    }
    
    suspend fun updateContact(contact: EmergencyContact) {
        emergencyContactDao.updateContact(contact)
    }
    
    suspend fun deleteContact(contact: EmergencyContact) {
        emergencyContactDao.deleteContact(contact)
    }
    
    suspend fun getActiveContactCount(): Int = 
        emergencyContactDao.getActiveContactCount()
    
    // App Settings
    suspend fun getSetting(key: String): String? = 
        appSettingsDao.getSettingValue(key)
    
    suspend fun setSetting(key: String, value: String) {
        val setting = AppSettings(key = key, value = value)
        appSettingsDao.insertSetting(setting)
    }
    
    suspend fun getBooleanSetting(key: String, defaultValue: Boolean = false): Boolean {
        return appSettingsDao.getSettingValue(key)?.toBoolean() ?: defaultValue
    }
    
    suspend fun setBooleanSetting(key: String, value: Boolean) {
        setSetting(key, value.toString())
    }
    
    // Safety Records
    fun getAllSafetyRecords(): Flow<List<SafetyRecord>> = 
        safetyRecordDao.getAllRecords()
    
    fun getSafetyRecordsByType(type: String): Flow<List<SafetyRecord>> = 
        safetyRecordDao.getRecordsByType(type)
    
    suspend fun addSafetyRecord(
        type: String,
        latitude: Double? = null,
        longitude: Double? = null,
        recordingPath: String? = null,
        contactsNotified: String? = null
    ): Long {
        val record = SafetyRecord(
            type = type,
            latitude = latitude,
            longitude = longitude,
            recordingPath = recordingPath,
            contactsNotified = contactsNotified
        )
        return safetyRecordDao.insertRecord(record)
    }
    
    suspend fun markRecordResolved(recordId: Long) {
        safetyRecordDao.markRecordResolved(recordId)
    }
    
    suspend fun getLatestUnresolvedRecord(): SafetyRecord? = 
        safetyRecordDao.getLatestUnresolvedRecord()
}
