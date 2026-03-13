package com.example.suraksha.utils

import android.util.Log
import com.example.suraksha.data.IncidentLog
import com.example.suraksha.data.IncidentLogDao
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * IncidentLogger — secure internal logging system that records
 * every SOS-related event to the Room database.
 *
 * Each entry contains:
 *  • timestamp
 *  • event type (e.g. SOS_TRIGGERED, CALL_PLACED, SMS_SENT, etc.)
 *  • human-readable details
 *
 * Logs are stored locally and never transmitted externally.
 */
class IncidentLogger(private val incidentLogDao: IncidentLogDao) {

    companion object {
        private const val TAG = "IncidentLogger"

        // Event type constants
        const val EVENT_SOS_TRIGGERED       = "SOS_TRIGGERED"
        const val EVENT_SOS_STOPPED         = "SOS_STOPPED"
        const val EVENT_CALL_PLACED         = "CALL_PLACED"
        const val EVENT_CALL_ENDED          = "CALL_ENDED"
        const val EVENT_SMS_SENT            = "SMS_SENT"
        const val EVENT_SILENT_ALERT_SENT   = "SILENT_ALERT_SENT"
        const val EVENT_AUDIO_RECORDING     = "AUDIO_RECORDED"
        const val EVENT_LOCATION_UPDATE     = "LOCATION_UPDATE"
        const val EVENT_WHATSAPP_SENT       = "WHATSAPP_SENT"
        const val EVENT_ERROR               = "ERROR"
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Record an incident log entry.
     *
     * @param eventType one of the EVENT_* constants.
     * @param details   human-readable description of the event.
     */
    suspend fun log(eventType: String, details: String) {
        try {
            val entry = IncidentLog(
                eventType = eventType,
                details = details
            )
            incidentLogDao.insertLog(entry)

            val ts = entry.timestamp.format(formatter)
            Log.d(TAG, "[$ts] $eventType — $details")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    // ── Convenience methods ─────────────────────────────────────────────

    suspend fun logSOSTriggered(trigger: String) {
        log(EVENT_SOS_TRIGGERED, "SOS triggered by $trigger")
    }

    suspend fun logSOSStopped() {
        log(EVENT_SOS_STOPPED, "SOS stopped by user (OK button)")
    }

    suspend fun logCallPlaced(contactName: String, phoneNumber: String) {
        log(EVENT_CALL_PLACED, "Call placed to $contactName ($phoneNumber)")
    }

    suspend fun logCallEnded(contactName: String) {
        log(EVENT_CALL_ENDED, "Call ended to $contactName after 10s")
    }

    suspend fun logSmsSent(contactName: String) {
        log(EVENT_SMS_SENT, "SMS sent to $contactName")
    }

    suspend fun logSilentAlertSent(contactName: String) {
        log(EVENT_SILENT_ALERT_SENT, "Silent alert sent to $contactName")
    }

    suspend fun logAudioRecording(filePath: String) {
        log(EVENT_AUDIO_RECORDING, "Audio evidence recorded: $filePath")
    }

    suspend fun logLocationUpdate(lat: Double, lon: Double) {
        log(EVENT_LOCATION_UPDATE, "Location update: $lat, $lon")
    }

    suspend fun logWhatsAppSent(contactName: String) {
        log(EVENT_WHATSAPP_SENT, "WhatsApp alert sent to $contactName")
    }

    suspend fun logError(description: String) {
        log(EVENT_ERROR, description)
    }

    // ── Retrieval ───────────────────────────────────────────────────────

    /** Get all incident logs in reverse chronological order. */
    fun getAllLogs() = incidentLogDao.getAllLogs()

    /** Get logs filtered by event type. */
    fun getLogsByType(eventType: String) = incidentLogDao.getLogsByType(eventType)

    /** Delete all incident logs. */
    suspend fun clearAllLogs() {
        incidentLogDao.clearAll()
        Log.d(TAG, "All incident logs cleared")
    }
}
