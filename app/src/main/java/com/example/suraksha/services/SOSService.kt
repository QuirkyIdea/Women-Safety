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
 *  3. Opens WhatsApp text alert to primary contact.
 *  4. Sequentially calls each contact for 10 s, then hangs up.
 *  5. Records 60-second audio clips in a loop; after each clip finishes
 *     the audio file is sent to the primary contact via WhatsApp, then deleted.
 *  6. Continues until ACTION_STOP is received.
 *
 * Runs as a foreground service so it survives activity destruction and
 * the disguised calculator screen.
 */
class SOSService : Service() {

    companion object {
        const val ACTION_START = "com.example.suraksha.SOS_START"
        const val ACTION_STOP  = "com.example.suraksha.SOS_STOP"
        private const val TAG = "SOSService"
        private const val CHANNEL_ID = "suraksha_sos_channel"
        private const val NOTIFICATION_ID = 3001
        private const val CALL_DURATION_MS = 10_000L
        private const val AUDIO_CLIP_MS = 60_000L
    }

    private val binder = SOSBinder()
    inner class SOSBinder : Binder() { fun getService() = this@SOSService }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var repository: SurakshaRepository
    private lateinit var audioRecorder: AudioRecorderManager

    private var currentLocation: Location? = null
    var isSOSActive = false; private set
    private var callingJob: Job? = null
    private var audioLoopJob: Job? = null
    private var locCallback: LocationCallback? = null

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = SurakshaRepository(
            SurakshaApplication.database.emergencyContactDao(),
            SurakshaApplication.database.appSettingsDao(),
            SurakshaApplication.database.safetyRecordDao()
        )
        audioRecorder = AudioRecorderManager(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSOS()
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

    private fun startSOS() {
        if (isSOSActive) return
        isSOSActive = true

        startForeground(NOTIFICATION_ID,
            buildNotification("🚨 SOS Active", "Emergency response running"))

        scope.launch {
            try {
                // 1 — Location
                startLocationTracking()
                val loc = fetchLocation()

                // 2 — Build message
                val phrase = repository.getSetting("sos_message")
                    ?: "I AM IN DANGER! Please help me immediately!"
                val mapUrl = loc?.let {
                    "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
                } ?: "Location unavailable"
                val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    .format(Date())

                val msg = buildString {
                    appendLine("🚨 EMERGENCY SOS ALERT 🚨")
                    appendLine()
                    appendLine(phrase)
                    appendLine()
                    appendLine("Time: $ts")
                    appendLine("Live Location: $mapUrl")
                    appendLine()
                    appendLine("Sent via Suraksha Safety App")
                }

                // 3 — SMS to all contacts
                sendSMSToAll(msg)

                // 4 — WhatsApp text to primary contact
                withContext(Dispatchers.Main) {
                    val primary = repository.getActiveContacts().first().firstOrNull()
                    if (primary != null) {
                        WhatsAppSender.sendTextMessage(
                            this@SOSService, primary.phoneNumber, msg)
                    }
                }

                // 5 — Sequential calling (10 s each)
                callingJob = scope.launch { sequentialCalling() }

                // 6 — 60-second audio recording loop with WhatsApp send
                audioLoopJob = scope.launch { audioLoop() }

                // Record event in DB
                repository.addSafetyRecord("SOS",
                    latitude = loc?.latitude, longitude = loc?.longitude)

            } catch (e: Exception) {
                Log.e(TAG, "startSOS error: ${e.message}", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STOP SOS  ("OK" button pressed)
    // ═══════════════════════════════════════════════════════════════════

    private fun stopSOS() {
        if (!isSOSActive) return
        isSOSActive = false

        callingJob?.cancel()
        audioLoopJob?.cancel()

        // Stop any in-progress recording and clean up temp file
        val lastFile = audioRecorder.stopRecording()
        audioRecorder.deleteFile(lastFile)
        audioRecorder.release()

        stopLocationTracking()

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        Log.d(TAG, "SOS stopped — voice trigger stays active in ViewModel")
    }

    // ── SMS ─────────────────────────────────────────────────────────────

    private suspend fun sendSMSToAll(message: String) {
        if (!PermissionManager.hasSmsPermission(this@SOSService)) return
        val contacts = repository.getActiveContacts().first()
        val smsManager = SmsManager.getDefault()
        for (c in contacts) {
            try {
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1)
                    smsManager.sendMultipartTextMessage(c.phoneNumber, null, parts, null, null)
                else
                    smsManager.sendTextMessage(c.phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS → ${c.name}")
                delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "SMS to ${c.name} failed: ${e.message}")
            }
        }
    }

    // ── Sequential calling ──────────────────────────────────────────────

    private suspend fun sequentialCalling() {
        val contacts = repository.getActiveContacts().first()
        for (c in contacts) {
            if (!isSOSActive) break
            try {
                val uri = Uri.parse("tel:${c.phoneNumber}")
                if (PermissionManager.hasCallPermission(this@SOSService)) {
                    startActivity(Intent(Intent.ACTION_CALL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    Log.d(TAG, "Calling ${c.name} for 10 s")
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
                delay(CALL_DURATION_MS)
                endCall()
                delay(2000)
            } catch (e: Exception) {
                Log.e(TAG, "Call to ${c.name} failed: ${e.message}")
            }
        }
    }

    private fun endCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
            }
        } catch (e: Exception) { Log.w(TAG, "endCall: ${e.message}") }
    }

    // ── 60-second audio loop ────────────────────────────────────────────

    private suspend fun audioLoop() {
        while (isSOSActive) {
            // Start recording on main thread (MediaRecorder requirement)
            val file = withContext(Dispatchers.Main) { audioRecorder.startRecording() }
            if (file == null) { delay(5000); continue }

            // Wait 60 seconds
            delay(AUDIO_CLIP_MS)
            if (!isSOSActive) break

            // Stop recording
            val done = withContext(Dispatchers.Main) { audioRecorder.stopRecording() }
            if (done == null || !done.exists()) continue

            // Build WhatsApp message with fresh location
            val loc = currentLocation
            val mapUrl = loc?.let {
                "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
            } ?: "Location unavailable"
            val phrase = repository.getSetting("sos_message") ?: "I AM IN DANGER!"
            val whatsMsg = buildString {
                appendLine("🚨 SOS AUDIO EVIDENCE 🚨")
                appendLine()
                appendLine(phrase)
                appendLine("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("Location: $mapUrl")
            }

            // Send to primary contact via WhatsApp
            val primary = repository.getActiveContacts().first().firstOrNull()
            if (primary != null) {
                withContext(Dispatchers.Main) {
                    WhatsAppSender.sendAudioWithMessage(
                        this@SOSService, primary.phoneNumber, whatsMsg, done)
                }
            }

            // Delete after WhatsApp has time to pick up the file
            delay(10_000)
            audioRecorder.deleteFile(done)
        }
    }

    // ── Location tracking ───────────────────────────────────────────────

    private fun startLocationTracking() {
        if (!PermissionManager.hasLocationPermission(this)) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setWaitForAccurateLocation(false).setMinUpdateIntervalMillis(5000).build()
        locCallback = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { currentLocation = it }
            }
        }
        try {
            locationClient.requestLocationUpdates(req, locCallback!!, mainLooper)
        } catch (e: Exception) { Log.e(TAG, "Location error: ${e.message}") }
    }

    private fun stopLocationTracking() {
        locCallback?.let {
            try { locationClient.removeLocationUpdates(it) } catch (_: Exception) {}
        }
        locCallback = null
    }

    private suspend fun fetchLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            if (!PermissionManager.hasLocationPermission(this@SOSService)) return@withContext null
            val last = locationClient.lastLocation.await()
            if (last != null) { currentLocation = last; return@withContext last }
            val fresh = locationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null).await()
            fresh?.let { currentLocation = it }
            fresh
        } catch (e: Exception) { Log.e(TAG, "fetchLocation: ${e.message}"); null }
    }

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
