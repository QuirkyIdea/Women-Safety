package com.example.suraksha.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.*
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.suraksha.MainActivity
import com.example.suraksha.R
import com.example.suraksha.SurakshaApplication
import com.example.suraksha.data.SurakshaRepository
import com.example.suraksha.utils.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * SOSService — foreground service that orchestrates the complete SOS flow:
 *
 *  1. Retrieves live GPS location.
 *  2. Sends SMS with help phrase + Google Maps link to all contacts.
 *  3. Sends silent alerts to trusted contacts (if enabled).
 *  4. Opens WhatsApp text alert to primary contact.
 *  5. Sequentially calls each contact for 10 s, then hangs up.
 *  6. Records 60-second audio clips in a loop; after each clip finishes
 *     the audio file is sent to the primary contact via WhatsApp, then deleted.
 *  7. Sends periodic location SMS every 30 seconds.
 *  8. Logs every event to the IncidentLogger.
 *  9. Continues until ACTION_STOP is received ("OK" button).
 *
 * Runs as a foreground service so it survives activity destruction and
 * the disguised calculator screen.
 */
class SOSService : Service() {

    companion object {
        const val ACTION_START = "com.example.suraksha.SOS_START"
        const val ACTION_STOP  = "com.example.suraksha.SOS_STOP"
        const val EXTRA_TRIGGER = "sos_trigger_source"
        private const val TAG = "SOSService"
        private const val CHANNEL_ID = "suraksha_sos_channel"
        private const val NOTIFICATION_ID = 3001
        private const val AUDIO_CLIP_MS = 60_000L
    }

    private val binder = SOSBinder()
    inner class SOSBinder : Binder() { fun getService() = this@SOSService }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: SurakshaRepository
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var emergencyCallManager: EmergencyCallManager
    private lateinit var locationTracker: LocationTracker
    private lateinit var incidentLogger: IncidentLogger
    private lateinit var silentAlertManager: SilentAlertManager
    private lateinit var whatsAppAlertManager: WhatsAppAlertManager

    var isSOSActive = false; private set
    private var callingJob: Job? = null
    private var audioLoopJob: Job? = null
    private var locationSmsJob: Job? = null

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        repository = SurakshaRepository(
            SurakshaApplication.database.emergencyContactDao(),
            SurakshaApplication.database.appSettingsDao(),
            SurakshaApplication.database.safetyRecordDao(),
            SurakshaApplication.database.incidentLogDao()
        )
        audioRecorder = AudioRecorderManager(this, scope)
        emergencyCallManager = EmergencyCallManager(this)
        locationTracker = LocationTracker(this)
        incidentLogger = IncidentLogger(SurakshaApplication.database.incidentLogDao())
        silentAlertManager = SilentAlertManager(this)
        whatsAppAlertManager = WhatsAppAlertManager(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val trigger = intent.getStringExtra(EXTRA_TRIGGER) ?: "manual"
                startSOS(trigger)
            }
            ACTION_STOP  -> stopSOS()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopSOS()
        scope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════════════════════════
    // START SOS
    // ═══════════════════════════════════════════════════════════════════

    private fun startSOS(trigger: String = "manual") {
        if (isSOSActive) return
        isSOSActive = true

        startForeground(NOTIFICATION_ID,
            buildNotification("🚨 SOS Active", "Emergency response running"))

        scope.launch {
            try {
                // Log: SOS triggered
                incidentLogger.logSOSTriggered(trigger)

                // 1 — Start GPS tracking
                locationTracker.startTracking()
                val loc = locationTracker.getCurrentLocation()

                // 2 — Build emergency message
                val phrase = repository.getSetting("sos_message")
                    ?: "I AM IN DANGER! Please help me immediately!"
                val locationUrl = loc?.let {
                    "https://maps.google.com/?q=${it.latitude},${it.longitude}"
                } ?: "Location unavailable"
                val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    .format(Date())

                val msg = buildString {
                    appendLine("🚨 EMERGENCY SOS ALERT 🚨")
                    appendLine()
                    appendLine(phrase)
                    appendLine()
                    appendLine("I need help. My live location is:")
                    appendLine(locationUrl)
                    appendLine()
                    appendLine("Time: $ts")
                    appendLine("Sent via Suraksha Safety App")
                }

                // 3 — Get all contacts
                val contacts = repository.getAllActiveContacts().first()

                // 4 — Send SMS to all contacts
                sendSMSToAll(msg, contacts)

                // 5 — Send silent alerts (if enabled)
                val silentAlertsEnabled = repository.getBooleanSetting("silent_alerts_enabled", false)
                if (silentAlertsEnabled) {
                    silentAlertManager.sendSilentAlerts(contacts, loc) { contactName ->
                        scope.launch { incidentLogger.logSilentAlertSent(contactName) }
                    }
                }

                // 6 — STEP 1: Call ALL contacts sequentially (BLOCKING)
                // Each call lasts exactly 10 seconds, then disconnects.
                // The loop completes only after every contact has been called.
                emergencyCallManager.callAllContacts(
                    contacts = contacts,
                    onCallStarted = { contact ->
                        scope.launch {
                            incidentLogger.logCallPlaced(contact.name, contact.phoneNumber)
                        }
                    },
                    onCallEnded = { contact ->
                        scope.launch {
                            incidentLogger.logCallEnded(contact.name)
                        }
                    }
                )

                // 7 — STEP 2: WhatsApp alert ONLY AFTER all calls finish
                // Brief delay to let the phone dialer UI close before
                // launching WhatsApp — prevents the intent being swallowed.
                delay(3000)

                val freshLoc = locationTracker.currentLocation ?: loc
                withContext(Dispatchers.Main) {
                    val primary = contacts.firstOrNull()
                    if (primary != null) {
                        val sent = whatsAppAlertManager.sendSOSAlert(
                            phoneNumber = primary.phoneNumber,
                            latitude = freshLoc?.latitude,
                            longitude = freshLoc?.longitude,
                            customMessage = phrase
                        )
                        if (sent) {
                            incidentLogger.logWhatsAppSent(primary.name)
                        } else {
                            incidentLogger.logError("WhatsApp alert failed for ${primary.name}")
                        }
                    }
                }

                // 8 — 60-second audio recording loop (runs concurrently)
                audioLoopJob = scope.launch { audioLoop(contacts) }

                // 9 — Periodic location SMS every 30 seconds
                val userName = getUserName()
                locationTracker.startPeriodicLocationSMS(
                    scope = scope,
                    contacts = contacts,
                    userName = userName,
                    isActive = { isSOSActive },
                    onSmsSent = { contactName ->
                        scope.launch {
                            val currentLoc = locationTracker.currentLocation
                            if (currentLoc != null) {
                                incidentLogger.logLocationUpdate(
                                    currentLoc.latitude, currentLoc.longitude
                                )
                            }
                        }
                    }
                )

                // Record event in DB
                repository.addSafetyRecord("SOS",
                    latitude = loc?.latitude, longitude = loc?.longitude)

            } catch (e: Exception) {
                Log.e(TAG, "startSOS error: ${e.message}", e)
                incidentLogger.logError("SOS start error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STOP SOS  ("OK" button pressed)
    // ═══════════════════════════════════════════════════════════════════

    private fun stopSOS() {
        if (!isSOSActive) return
        isSOSActive = false

        // Cancel all running jobs
        callingJob?.cancel()
        audioLoopJob?.cancel()

        // Stop emergency call sequence
        emergencyCallManager.stop()

        // Stop any in-progress recording and clean up temp file
        try {
            val lastFile = audioRecorder.stopRecording()
            audioRecorder.deleteFile(lastFile)
            audioRecorder.release()
        } catch (e: Exception) {
            Log.w(TAG, "Audio cleanup error: ${e.message}")
        }

        // Stop location tracking + periodic SMS
        locationTracker.stopTracking()

        // Log: SOS stopped
        scope.launch {
            try { incidentLogger.logSOSStopped() } catch (_: Exception) {}
        }

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        Log.d(TAG, "SOS stopped — voice trigger stays active in ViewModel")
    }

    // ── SMS ─────────────────────────────────────────────────────────────

    private suspend fun sendSMSToAll(
        message: String,
        contacts: List<com.example.suraksha.data.EmergencyContact>
    ) {
        if (!PermissionManager.hasSmsPermission(this@SOSService)) return
        val smsManager = SmsManager.getDefault()
        for (c in contacts) {
            try {
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1)
                    smsManager.sendMultipartTextMessage(c.phoneNumber, null, parts, null, null)
                else
                    smsManager.sendTextMessage(c.phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS → ${c.name}")
                incidentLogger.logSmsSent(c.name)
                delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "SMS to ${c.name} failed: ${e.message}")
                incidentLogger.logError("SMS to ${c.name} failed: ${e.message}")
            }
        }
    }

    // ── 60-second audio loop ────────────────────────────────────────────

    private suspend fun audioLoop(contacts: List<com.example.suraksha.data.EmergencyContact>) {
        while (isSOSActive) {
            try {
                // Start recording on main thread (MediaRecorder requirement)
                val file = withContext(Dispatchers.Main) { audioRecorder.startRecording() }
                if (file == null) { delay(5000); continue }

                incidentLogger.logAudioRecording(file.absolutePath)

                // Wait 60 seconds
                delay(AUDIO_CLIP_MS)
                if (!isSOSActive) break

                // Stop recording
                val done = withContext(Dispatchers.Main) { audioRecorder.stopRecording() }
                if (done == null || !done.exists()) continue

                // Send audio evidence to primary contact via WhatsApp
                val loc = locationTracker.currentLocation
                val phrase = repository.getSetting("sos_message") ?: "I AM IN DANGER!"
                val primary = contacts.firstOrNull()
                if (primary != null) {
                    withContext(Dispatchers.Main) {
                        whatsAppAlertManager.sendAudioEvidence(
                            phoneNumber = primary.phoneNumber,
                            audioFile = done,
                            latitude = loc?.latitude,
                            longitude = loc?.longitude,
                            customMessage = phrase
                        )
                    }
                    incidentLogger.logWhatsAppSent(primary.name)
                }

                // Delete after WhatsApp has time to pick up the file
                delay(10_000)
                audioRecorder.deleteFile(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio loop error: ${e.message}")
                incidentLogger.logError("Audio loop error: ${e.message}")
                delay(5000)
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private suspend fun getUserName(): String = try {
        repository.getSetting("user_name") ?: "Suraksha User"
    } catch (_: Exception) { "Suraksha User" }

    // ── Notification ────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "SOS Emergency",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active during SOS"; enableVibration(true); enableLights(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(R.drawable.ic_safety)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).setContentIntent(pi).build()
    }
}
