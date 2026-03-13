package com.example.suraksha.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * WhatsAppSender — sends audio evidence + text to WhatsApp.
 *
 * Two modes:
 *  1. [sendAudioWithMessage] — shares an audio file with text (used every 60 s).
 *  2. [sendTextMessage] — sends a text-only message (initial SOS alert).
 *
 * Limitation: Android does not allow fully automated WhatsApp sends.
 * The intent opens WhatsApp pre-filled; the user taps Send once.
 */
object WhatsAppSender {

    private const val TAG = "WhatsAppSender"
    private const val WHATSAPP_PKG = "com.whatsapp"

    /**
     * Open WhatsApp with an audio file attached and emergency text.
     * @return true if the intent was launched successfully.
     */
    fun sendAudioWithMessage(
        context: Context,
        phoneNumber: String,
        message: String,
        audioFile: File
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
                Log.d(TAG, "WhatsApp audio intent launched → $phone")
                return true
            }

            // Fallback: generic share chooser
            val chooser = Intent.createChooser(intent, "Send emergency audio").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "sendAudioWithMessage failed: ${e.message}")
            return false
        }
    }

    /**
     * Open WhatsApp with a text-only message (initial SOS alert).
     */
    fun sendTextMessage(
        context: Context,
        phoneNumber: String,
        message: String
    ): Boolean {
        return try {
            val phone = formatPhone(phoneNumber)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "WhatsApp text intent launched → $phone")
                true
            } else {
                Log.w(TAG, "WhatsApp not installed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendTextMessage failed: ${e.message}")
            false
        }
    }

    /** Strip non-digits; default to India (+91) if no country code. */
    private fun formatPhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        return if (digits.startsWith("+")) digits.substring(1) else "91$digits"
    }
}
