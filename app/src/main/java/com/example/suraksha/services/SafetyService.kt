package com.example.suraksha.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.media.MediaRecorder
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.suraksha.R
import com.example.suraksha.data.SurakshaRepository
import com.example.suraksha.MainActivity
import com.example.suraksha.SurakshaApplication
import com.example.suraksha.utils.PermissionManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SafetyService — legacy SOS + standalone recording foreground service.
 *
 * NOTE: The primary SOS flow is now handled by [SOSService].
 * SafetyService is retained for standalone audio recording, test SMS,
 * and location sharing features.
 */
class SafetyService : Service() {

    companion object {
        const val ACTION_SOS = "com.example.suraksha.SOS"
        const val ACTION_STOP_SOS = "com.example.suraksha.STOP_SOS"
        const val ACTION_START_RECORDING = "com.example.suraksha.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.suraksha.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.example.suraksha.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.example.suraksha.RESUME_RECORDING"
        const val ACTION_SEND_TEST_SMS = "com.example.suraksha.SEND_TEST_SMS"
        const val ACTION_SEND_EMERGENCY_SMS = "com.example.suraksha.SEND_EMERGENCY_SMS"
        const val ACTION_SEND_LOCATION_SMS = "com.example.suraksha.SEND_LOCATION_SMS"
        const val CHANNEL_ID = "suraksha_safety_channel"
        const val NOTIFICATION_ID = 1001
        const val LOCATION_UPDATE_INTERVAL = 10_000L        // GPS refresh every 10 s
        const val LOCATION_SMS_UPDATE_INTERVAL = 30_000L    // SMS location update every 30 s
        const val CALL_DURATION_MS = 10_000L                // Each call lasts 10 s
        private const val TAG = "SafetyService"
    }

    private val binder = SafetyBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var repository: SurakshaRepository

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var currentLocation: Location? = null
    private var isLocationTracking = false
    private var isSOSActive = false
    private var locationUpdateJob: Job? = null

    inner class SafetyBinder : Binder() {
        fun getService(): SafetyService = this@SafetyService
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = SurakshaRepository(
            SurakshaApplication.database.emergencyContactDao(),
            SurakshaApplication.database.appSettingsDao(),
            SurakshaApplication.database.safetyRecordDao()
        )
        setupLocationTracking()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SOS -> handleSOS()
            ACTION_STOP_SOS -> stopSOS()
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
            ACTION_SEND_TEST_SMS -> handleTestSMS(intent)
            ACTION_SEND_EMERGENCY_SMS -> handleEmergencySMS(intent)
            ACTION_SEND_LOCATION_SMS -> handleLocationSMS(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopLocationTracking()
        locationUpdateJob?.cancel()
        serviceScope.cancel()
    }

    // ── SOS orchestration ───────────────────────────────────────────────

    private fun handleSOS() {
        if (isSOSActive) {
            Log.d(TAG, "SOS already active")
            return
        }
        isSOSActive = true

        serviceScope.launch {
            try {
                // 1 – Foreground + location
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("🚨 SOS Activated", "Emergency response initiated")
                )
                startLocationTracking()

                // 2 – Get GPS location
                val location = getCurrentLocation()

                // 3 – Build customisable emergency message
                val customPhrase = repository.getSetting("sos_message")
                    ?: "I AM IN DANGER! Please help me immediately!"
                val locationUrl = location?.let {
                    "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
                } ?: "Location unavailable"
                val timestamp = SimpleDateFormat(
                    "dd/MM/yyyy HH:mm:ss", Locale.getDefault()
                ).format(Date())

                val emergencyMessage = """
                    🚨 EMERGENCY SOS ALERT 🚨
                    
                    $customPhrase
                    
                    User: ${getUserName()}
                    Time: $timestamp
                    Live Location: $locationUrl
                    
                    Sent via Suraksha Safety App.
                """.trimIndent()

                // 4 – Send SMS to all emergency contacts
                sendEmergencySMS(emergencyMessage)

                // 5 – Open WhatsApp for the first contact
                openWhatsApp(emergencyMessage)

                // 6 – Start audio evidence recording
                val autoRecordEnabled = repository.getBooleanSetting("auto_recording_enabled", true)
                if (autoRecordEnabled) {
                    startRecording()
                }

                // 7 – Sequential emergency calling (10 s each)
                sequentialCalling()

                // 8 – Periodic location SMS every 30 s
                startPeriodicLocationUpdates()

                // Record event
                repository.addSafetyRecord(
                    type = "SOS",
                    latitude = location?.latitude,
                    longitude = location?.longitude
                )

                showEmergencyNotification(location)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling SOS: ${e.message}", e)
            }
        }
    }

    /** Gracefully stop SOS: stop recordings, calling, periodic SMS. */
    private fun stopSOS() {
        isSOSActive = false
        locationUpdateJob?.cancel()
        stopRecording()

        stopLocationTracking()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        Log.d(TAG, "SOS stopped")
    }

    // ── WhatsApp integration ────────────────────────────────────────────

    /**
     * Attempts to open WhatsApp (or WhatsApp Business) with a pre-filled
     * emergency message for the first saved contact.
     *
     * Uses ACTION_SEND so it works even if the contact is not a WhatsApp user
     * (the user can pick a chat manually).
     */
    private suspend fun openWhatsApp(message: String) {
        try {
            val contacts = repository.getActiveContacts().first()
            if (contacts.isEmpty()) return

            val firstContact = contacts[0]
            // Format phone for WhatsApp: strip non-digit, ensure country code
            val phone = firstContact.phoneNumber.replace(Regex("[^0-9+]"), "")
                .let { if (it.startsWith("+")) it.substring(1) else "91$it" } // Default India

            val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Check if WhatsApp is installed before launching
            if (whatsappIntent.resolveActivity(packageManager) != null) {
                startActivity(whatsappIntent)
                Log.d(TAG, "WhatsApp opened for ${firstContact.name}")
            } else {
                Log.w(TAG, "WhatsApp not installed; skipping")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp launch error: ${e.message}")
        }
    }

    // ── Sequential calling ──────────────────────────────────────────────

    /**
     * Dials each emergency contact for [CALL_DURATION_MS] (10 s), then
     * disconnects and moves to the next contact.
     *
     * Uses ACTION_CALL (auto-dial) when CALL_PHONE permission is granted,
     * otherwise falls back to ACTION_DIAL which shows the dialer UI.
     */
    private suspend fun sequentialCalling() {
        val contacts = repository.getActiveContacts().first()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No contacts for sequential calling")
            return
        }

        for (contact in contacts) {
            try {
                val phoneUri = Uri.parse("tel:${contact.phoneNumber}")

                if (PermissionManager.hasCallPermission(this@SafetyService)) {
                    // Auto call
                    val callIntent = Intent(Intent.ACTION_CALL, phoneUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(callIntent)
                    Log.d(TAG, "Calling ${contact.name} (${contact.phoneNumber}) for ${CALL_DURATION_MS / 1000}s")
                } else {
                    // Fallback: open dialer
                    val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(dialIntent)
                    Log.d(TAG, "Opened dialer for ${contact.name}")
                }

                // Wait for call duration
                delay(CALL_DURATION_MS)

                // Hang up using TelecomManager (API 28+)
                endCurrentCall()

                // Brief pause between calls
                delay(2000)

            } catch (e: Exception) {
                Log.e(TAG, "Error calling ${contact.name}: ${e.message}")
            }
        }
    }

    /**
     * Programmatically end the current call using [TelecomManager].
     * Requires API 28+ and ANSWER_PHONE_CALLS permission.
     */
    private fun endCurrentCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.endCall()
                Log.d(TAG, "Call ended via TelecomManager")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not end call programmatically: ${e.message}")
        }
    }

    // ── Periodic 30-second location SMS ─────────────────────────────────

    /**
     * While SOS is active, sends an updated location SMS to every contact
     * every 30 seconds.
     */
    private fun startPeriodicLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = serviceScope.launch {
            while (isSOSActive) {
                delay(LOCATION_SMS_UPDATE_INTERVAL) // first update at T+30 s
                if (!isSOSActive) break

                val location = getCurrentLocation()
                val locationUrl = location?.let {
                    "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
                } ?: "Location unavailable"

                val updateMsg = """
                    📍 LIVE LOCATION UPDATE 📍
                    User: ${getUserName()}
                    Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}
                    Location: $locationUrl
                """.trimIndent()

                val contacts = repository.getActiveContacts().first()
                for (contact in contacts) {
                    try {
                        sendSMS(contact.phoneNumber, updateMsg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Periodic SMS to ${contact.name} failed: ${e.message}")
                    }
                    delay(500) // small gap between contacts
                }
            }
        }
    }



    // ── Location tracking (GPS) ─────────────────────────────────────────

    private fun setupLocationTracking() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL
        )
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    currentLocation = loc
                    Log.d(TAG, "Location: ${loc.latitude}, ${loc.longitude}")
                    if (isRecording || isLocationTracking) {
                        updateLocationNotification(loc)
                    }
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (isLocationTracking) return
        if (!PermissionManager.hasLocationPermission(this)) {
            Log.w(TAG, "Location permission not granted")
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, mainLooper
            )
            isLocationTracking = true
        } catch (e: Exception) {
            Log.e(TAG, "Location tracking start error: ${e.message}")
        }
    }

    private fun stopLocationTracking() {
        if (!isLocationTracking) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationTracking = false
        } catch (e: Exception) {
            Log.e(TAG, "Location tracking stop error: ${e.message}")
        }
    }

    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            if (!PermissionManager.hasLocationPermission(this@SafetyService)) return@withContext null
            val last = fusedLocationClient.lastLocation.await()
            if (last != null) { currentLocation = last; return@withContext last }
            val fresh = fusedLocationClient.getCurrentLocation(
                LocationRequest.PRIORITY_HIGH_ACCURACY, null
            ).await()
            fresh?.let { currentLocation = it }
            fresh
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation error: ${e.message}")
            null
        }
    }

    // ── SMS sending ─────────────────────────────────────────────────────

    /**
     * Send the [message] to every active emergency contact.
     * Uses the user's custom SOS phrase from settings.
     */
    private suspend fun sendEmergencySMS(message: String) {
        val contacts = repository.getActiveContacts().first()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts found")
            return
        }
        for (contact in contacts) {
            try {
                sendSMS(contact.phoneNumber, message)
                Log.d(TAG, "SMS sent to ${contact.name}")
                delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "SMS to ${contact.name} failed: ${e.message}")
            }
        }
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        if (!PermissionManager.hasSmsPermission(this)) {
            Log.w(TAG, "SMS permission not granted")
            return
        }
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendSMS error: ${e.message}")
            throw e
        }
    }

    // ── Intent-based SMS handlers (test / single contact) ───────────────

    private fun handleTestSMS(intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: return
        val contactName = intent.getStringExtra("contact_name") ?: "Contact"
        serviceScope.launch {
            val location = getCurrentLocation()
            val locationText = location?.let {
                "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
            } ?: "Location not available"

            val testMessage = """
                🧪 TEST SMS FROM SURAKSHA 🧪
                
                Hello $contactName,
                This is a test message from Suraksha Safety App.
                
                Time: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                Location: $locationText
                
                If you received this, SMS is working properly.
            """.trimIndent()

            try { sendSMS(phoneNumber, testMessage) } catch (e: Exception) {
                Log.e(TAG, "Test SMS failed: ${e.message}")
            }
        }
    }

    private fun handleEmergencySMS(intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: return
        val contactName = intent.getStringExtra("contact_name") ?: "Contact"
        serviceScope.launch {
            if (!isLocationTracking) startLocationTracking()
            val location = getCurrentLocation()
            val locationUrl = location?.let {
                "https://www.google.com/maps/search/?api=1&query=${it.latitude},${it.longitude}"
            } ?: "Location not available"
            val customPhrase = repository.getSetting("sos_message")
                ?: "I AM IN DANGER! Please help me immediately!"

            val emergencyMessage = """
                🚨 EMERGENCY SOS ALERT 🚨
                
                $customPhrase
                
                User: ${getUserName()}
                Time: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                Location: $locationUrl
                
                Sent via Suraksha Safety App. Respond immediately.
            """.trimIndent()

            try {
                sendSMS(phoneNumber, emergencyMessage)
                Log.d(TAG, "Emergency SMS sent to $contactName")
            } catch (e: Exception) {
                Log.e(TAG, "Emergency SMS to $contactName failed: ${e.message}")
            }
        }
    }

    private fun handleLocationSMS(intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: return
        val contactName = intent.getStringExtra("contact_name") ?: "Contact"
        serviceScope.launch {
            if (!isLocationTracking) startLocationTracking()
            val location = getCurrentLocation()
            val locationUrl = location?.let {
                "https://maps.google.com/?q=${it.latitude},${it.longitude}"
            } ?: "Location not available"

            val locationMessage = """
                📍 LOCATION SHARE FROM SURAKSHA 📍
                
                Hello $contactName,
                I'm sharing my current location with you.
                
                User: ${getUserName()}
                Time: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
                Live Location: $locationUrl
                
                Tap the link to open Google Maps.
            """.trimIndent()

            try {
                sendSMS(phoneNumber, locationMessage)
                showLocationSharedNotification(contactName, location)
            } catch (e: Exception) {
                Log.e(TAG, "Location SMS to $contactName failed: ${e.message}")
            }
        }
    }

    // ── Audio recording (MediaRecorder) ─────────────────────────────────

    private fun startRecording() {
        if (isRecording) return
        if (!PermissionManager.hasAudioPermission(this)) {
            Log.e(TAG, "Audio permission not granted"); return
        }
        try {
            val outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            recordingFile = File(outputDir, "emergency_recording_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            isPaused = false
            Log.d(TAG, "Audio recording started: ${recordingFile?.absolutePath}")

            serviceScope.launch {
                try {
                    repository.setBooleanSetting("recording_active", true)
                    repository.setSetting("recording_started_at", System.currentTimeMillis().toString())
                } catch (_: Exception) {}
            }

            val notification = createNotification("Recording Active", "Emergency audio recording in progress")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Start recording error: ${e.message}")
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (!isRecording && mediaRecorder == null) return
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false
            Log.d(TAG, "Audio recording stopped: ${recordingFile?.absolutePath}")

            serviceScope.launch {
                try {
                    repository.addSafetyRecord(
                        type = "RECORDING",
                        recordingPath = recordingFile?.absolutePath
                    )
                    repository.setBooleanSetting("recording_active", false)
                    repository.setSetting("recording_started_at", "0")
                } catch (_: Exception) {}
            }

            try { stopForeground(true) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording error: ${e.message}")
            isRecording = false
        }
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            mediaRecorder?.pause()
            isPaused = true
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, createNotification("Recording Paused", "Tap Resume to continue"))
        } catch (e: Exception) {
            Log.e(TAG, "Pause error: ${e.message}")
        }
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            mediaRecorder?.resume()
            isPaused = false
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, createNotification("Recording Active", "Emergency audio recording in progress"))
        } catch (e: Exception) {
            Log.e(TAG, "Resume error: ${e.message}")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun getUserName(): String = try {
        runBlocking { repository.getSetting("user_name") ?: "Suraksha User" }
    } catch (_: Exception) { "Suraksha User" }

    private fun updateLocationNotification(location: Location) {
        val notification = createNotification(
            "Location Tracking Active",
            "Current: ${location.latitude}, ${location.longitude}"
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showLocationSharedNotification(contactName: String, location: Location?) {
        val text = if (location != null)
            "Location shared with $contactName: ${location.latitude}, ${location.longitude}"
        else "Location shared with $contactName (location unavailable)"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, createNotification("Location Shared", text))
    }

    private fun showEmergencyNotification(location: Location?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 SOS Emergency Alert")
            .setContentText("Emergency response activated. Tap to open app.")
            .setSmallIcon(R.drawable.ic_safety)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setLights(0xFF0000, 3000, 3000)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_safety)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suraksha Safety",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency notifications from Suraksha Safety App"
                enableVibration(true)
                enableLights(true)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
