package com.example.suraksha.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * WhatsAppAlertManager — automates WhatsApp emergency alerts as far
 * as the Android platform allows.
 *
 * Due to WhatsApp API limitations, apps cannot programmatically press
 * the "Send" button. This manager gets as close as possible:
 *
 *  1. [sendSOSAlert] — opens a WhatsApp chat with the primary contact,
 *     pre-populated with the emergency message including live location.
 *
 *  2. [sendAudioEvidence] — opens WhatsApp with an audio file attached
 *     and location text, directed at the primary contact.
 *
 * The user only needs to tap the send button in WhatsApp.
 */
class WhatsAppAlertManager(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppAlertManager"
        private const val WHATSAPP_PKG = "com.whatsapp"
    }

    /**
     * Open WhatsApp chat with the primary contact, pre-populated with:
     *
     *   🚨 I NEED HELP.
     *   Live Location:
     *   https://maps.google.com/?q=LAT,LNG
     *
     * @param phoneNumber  primary contact phone number.
     * @param latitude     current GPS latitude (nullable).
     * @param longitude    current GPS longitude (nullable).
     * @param customMessage optional custom SOS phrase.
     * @return true if WhatsApp was launched successfully.
     */
    fun sendSOSAlert(
        phoneNumber: String,
        latitude: Double?,
        longitude: Double?,
        customMessage: String = "I AM IN DANGER! Please help me immediately!"
    ): Boolean {
        val locationUrl = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "Location unavailable"
        }

        val message = buildString {
            appendLine("🚨 I NEED HELP. 🚨")
            appendLine()
            appendLine(customMessage)
            appendLine()
            appendLine("Live Location:")
            appendLine(locationUrl)
            appendLine()
            appendLine("Sent via Suraksha Safety App")
        }

        return openWhatsAppChat(phoneNumber, message)
    }

    /**
     * Open WhatsApp with an audio evidence file attached, directed at
     * the primary contact. Message includes current location.
     *
     * @param phoneNumber  primary contact phone number.
     * @param audioFile    audio evidence file to attach.
     * @param latitude     current GPS latitude (nullable).
     * @param longitude    current GPS longitude (nullable).
     * @param customMessage optional custom SOS phrase.
     * @return true if WhatsApp was launched successfully.
     */
    fun sendAudioEvidence(
        phoneNumber: String,
        audioFile: File,
        latitude: Double?,
        longitude: Double?,
        customMessage: String = "I AM IN DANGER!"
    ): Boolean {
        try {
            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file does not exist: ${audioFile.absolutePath}")
                return false
            }

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", audioFile
            )

            val phone = formatPhone(phoneNumber)

            val locationUrl = if (latitude != null && longitude != null) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else {
                "Location unavailable"
            }

            val message = buildString {
                appendLine("🚨 SOS AUDIO EVIDENCE 🚨")
                appendLine()
                appendLine(customMessage)
                appendLine()
                appendLine("Location: $locationUrl")
                appendLine("Sent via Suraksha Safety App")
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                `package` = WHATSAPP_PKG
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$phone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "WhatsApp audio+location intent launched → $phone")
                return true
            }

            // Fallback: generic share chooser
            val chooser = Intent.createChooser(intent, "Send emergency audio").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "sendAudioEvidence failed: ${e.message}")
            return false
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    /**
     * Open WhatsApp chat with a text-only message.
     * Uses the WhatsApp deep-link API for direct chat opening.
     */
    private fun openWhatsAppChat(phoneNumber: String, message: String): Boolean {
        return try {
            val phone = formatPhone(phoneNumber)
            val encodedMsg = Uri.encode(message)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "WhatsApp SOS alert launched → $phone")
                true
            } else {
                Log.w(TAG, "WhatsApp not installed — cannot send alert")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "openWhatsAppChat failed: ${e.message}")
            false
        }
    }

    /**
     * Strip non-digits from phone; default to India (+91) if no country code.
     */
    private fun formatPhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        return if (digits.startsWith("+")) digits.substring(1) else "91$digits"
    }
}
