package com.example.suraksha.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.example.suraksha.data.EmergencyContact
import kotlinx.coroutines.delay

/**
 * EmergencyCallManager — sequentially calls every emergency contact.
 *
 * Algorithm:
 *  1. Iterate through the contact queue.
 *  2. Place a call to the current contact using ACTION_CALL.
 *  3. Wait exactly [CALL_DURATION_MS] (10 s).
 *  4. End the call using [TelecomManager] (API 28+).
 *  5. Brief pause, then move to the next contact.
 *  6. Continue until all contacts have been called or [stop] is invoked.
 *
 * Requires: CALL_PHONE and ANSWER_PHONE_CALLS runtime permissions.
 */
class EmergencyCallManager(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyCallManager"
        private const val CALL_DURATION_MS = 10_000L   // 10 seconds per call
        private const val INTER_CALL_DELAY_MS = 2_000L // 2 seconds between calls
    }

    @Volatile
    private var isActive = false

    /**
     * Call every contact in [contacts] sequentially.
     *
     * This is a **suspend** function — it blocks the coroutine until all
     * contacts have been called (or [stop] cancels early).
     *
     * @param contacts  ordered list of emergency contacts to call.
     * @param onCallStarted optional callback invoked when each call begins.
     * @param onCallEnded   optional callback invoked when each call ends.
     */
    suspend fun callAllContacts(
        contacts: List<EmergencyContact>,
        onCallStarted: ((EmergencyContact) -> Unit)? = null,
        onCallEnded: ((EmergencyContact) -> Unit)? = null
    ) {
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts to call")
            return
        }

        isActive = true
        Log.d(TAG, "Starting sequential calls to ${contacts.size} contacts")

        for ((index, contact) in contacts.withIndex()) {
            if (!isActive) {
                Log.d(TAG, "Call sequence stopped at contact #${index + 1}")
                break
            }

            try {
                // Place the call
                placeCall(contact)
                onCallStarted?.invoke(contact)
                Log.d(TAG, "Calling ${contact.name} (${contact.phoneNumber}) — contact #${index + 1}/${contacts.size}")

                // Wait exactly 10 seconds
                delay(CALL_DURATION_MS)

                // End the call
                endCurrentCall()
                onCallEnded?.invoke(contact)
                Log.d(TAG, "Ended call to ${contact.name}")

                // Brief pause before next call (allow system to settle)
                if (index < contacts.size - 1 && isActive) {
                    delay(INTER_CALL_DELAY_MS)
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Honour cancellation — try to hang up the current call
                endCurrentCall()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error calling ${contact.name}: ${e.message}", e)
                // Still try to end any active call before continuing
                endCurrentCall()
                delay(INTER_CALL_DELAY_MS)
            }
        }

        isActive = false
        Log.d(TAG, "Sequential calling complete")
    }

    /** Immediately cancel the call sequence and hang up the active call. */
    fun stop() {
        isActive = false
        endCurrentCall()
        Log.d(TAG, "Emergency call sequence stopped")
    }

    /** Whether calls are currently in progress. */
    fun isCallSequenceActive(): Boolean = isActive

    // ── Internal ────────────────────────────────────────────────────────

    private fun placeCall(contact: EmergencyContact) {
        val phoneUri = Uri.parse("tel:${contact.phoneNumber}")

        if (PermissionManager.hasCallPermission(context)) {
            val callIntent = Intent(Intent.ACTION_CALL, phoneUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(callIntent)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException placing call: ${e.message}")
                // Fallback to dialer
                val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } else {
            // Fallback: open dialer UI
            val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            Log.w(TAG, "CALL_PHONE not granted; opened dialer for ${contact.name}")
        }
    }

    /**
     * Programmatically end the current phone call using [TelecomManager].
     * Requires API 28+ and ANSWER_PHONE_CALLS permission.
     */
    private fun endCurrentCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager =
                    context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null) {
                    @Suppress("DEPRECATION")
                    val ended = telecomManager.endCall()
                    Log.d(TAG, "TelecomManager.endCall() → $ended")
                } else {
                    Log.w(TAG, "TelecomManager is null")
                }
            } else {
                Log.w(TAG, "endCall() requires API 28+; running on API ${Build.VERSION.SDK_INT}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not end call: ${e.message}")
        }
    }
}
