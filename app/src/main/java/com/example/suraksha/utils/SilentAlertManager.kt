package com.example.suraksha.utils

import android.content.Context
import android.location.Location
import android.telephony.SmsManager
import android.util.Log
import com.example.suraksha.data.EmergencyContact
import java.text.SimpleDateFormat
import java.util.*

/**
 * SilentAlertManager — sends discreet SMS alerts to trusted contacts
 * without any sound or vibration from the app side.
 *
 * When enabled in settings, a silent SMS is dispatched immediately
 * when SOS starts. The message includes the user's location and
 * timestamp but is marked as a "silent" alert so contacts know
 * the user may not be able to speak.
 */
class SilentAlertManager(private val context: Context) {

    companion object {
        private const val TAG = "SilentAlertManager"
    }

    /**
     * Send a silent alert SMS to all [contacts].
     *
     * @param contacts list of trusted emergency contacts.
     * @param location current GPS location (nullable).
     * @param onAlertSent optional callback per contact for logging.
     */
    fun sendSilentAlerts(
        contacts: List<EmergencyContact>,
        location: Location?,
        onAlertSent: ((String) -> Unit)? = null
    ) {
        if (!PermissionManager.hasSmsPermission(context)) {
            Log.w(TAG, "SMS permission not granted — silent alerts skipped")
            return
        }

        if (contacts.isEmpty()) {
            Log.w(TAG, "No contacts for silent alerts")
            return
        }

        val locationUrl = location?.let {
            "https://maps.google.com/?q=${it.latitude},${it.longitude}"
        } ?: "Location unavailable"

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(Date())

        val message = buildString {
            appendLine("SOS ALERT (silent).")
            appendLine("User may be in danger.")
            appendLine("Location: $locationUrl")
            appendLine("Time: $timestamp")
            appendLine()
            appendLine("Sent via Suraksha Safety App")
        }

        for (contact in contacts) {
            try {
                sendSMS(contact.phoneNumber, message)
                onAlertSent?.invoke(contact.name)
                Log.d(TAG, "Silent alert sent to ${contact.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Silent alert to ${contact.name} failed: ${e.message}")
            }
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun sendSMS(phoneNumber: String, message: String) {
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        }
    }
}
