package com.example.suraksha.utils

import android.content.Context
import android.location.Location
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import com.example.suraksha.data.EmergencyContact
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * LocationTracker — wraps [FusedLocationProviderClient] for live GPS tracking
 * and sends periodic location SMS every 30 seconds while SOS is active.
 *
 * Usage:
 *  1. [startTracking] — begins high-accuracy GPS updates every 10 s.
 *  2. [startPeriodicLocationSMS] — sends location SMS every 30 s.
 *  3. [stopTracking] — stops GPS tracking and periodic SMS.
 *  4. [getCurrentLocation] — one-shot fetch of current GPS coordinates.
 */
class LocationTracker(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracker"
        private const val GPS_INTERVAL_MS = 10_000L           // GPS refresh every 10 s
        private const val LOCATION_SMS_INTERVAL_MS = 30_000L  // SMS location every 30 s
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private var locationSmsJob: Job? = null

    @Volatile
    var currentLocation: Location? = null
        private set

    @Volatile
    var isTracking: Boolean = false
        private set

    // ── GPS tracking ────────────────────────────────────────────────────

    /**
     * Start receiving high-accuracy GPS updates.
     * Updates [currentLocation] on every fix.
     */
    fun startTracking() {
        if (isTracking) return
        if (!PermissionManager.hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateDelayMillis(15_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    currentLocation = loc
                    Log.d(TAG, "Location fix: ${loc.latitude}, ${loc.longitude} (acc: ${loc.accuracy}m)")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback!!, Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "GPS tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting location: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Location tracking start error: ${e.message}")
        }
    }

    /** Stop GPS updates and periodic SMS. */
    fun stopTracking() {
        locationSmsJob?.cancel()
        locationSmsJob = null

        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.e(TAG, "Location tracking stop error: ${e.message}")
            }
        }
        locationCallback = null
        isTracking = false
        Log.d(TAG, "GPS tracking stopped")
    }

    // ── One-shot fetch ──────────────────────────────────────────────────

    /**
     * Fetch the current GPS location (suspending).
     * Tries [getLastLocation] first, then falls back to [getCurrentLocation].
     */
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            if (!PermissionManager.hasLocationPermission(context)) return@withContext null

            // Try last known
            val last = fusedLocationClient.lastLocation.await()
            if (last != null) {
                currentLocation = last
                return@withContext last
            }

            // Fresh fix
            val fresh = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null
            ).await()
            fresh?.let { currentLocation = it }
            fresh
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation error: ${e.message}")
            null
        }
    }

    // ── Periodic location SMS ───────────────────────────────────────────

    /**
     * While SOS is active, send an updated location SMS to every contact
     * every 30 seconds.
     *
     * @param scope         coroutine scope (cancelled when SOS stops).
     * @param contacts      the list of emergency contacts.
     * @param userName      display name of the user.
     * @param isActive      lambda returning whether SOS is still active.
     * @param onSmsSent     optional callback for logging.
     */
    fun startPeriodicLocationSMS(
        scope: CoroutineScope,
        contacts: List<EmergencyContact>,
        userName: String,
        isActive: () -> Boolean,
        onSmsSent: ((String) -> Unit)? = null
    ) {
        locationSmsJob?.cancel()
        locationSmsJob = scope.launch {
            while (isActive()) {
                delay(LOCATION_SMS_INTERVAL_MS) // first update at T+30 s
                if (!isActive()) break

                val loc = currentLocation ?: getCurrentLocation()
                val locationUrl = loc?.let {
                    "https://maps.google.com/?q=${it.latitude},${it.longitude}"
                } ?: "Location unavailable"

                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                val updateMsg = buildString {
                    appendLine("I need help. My live location is:")
                    appendLine(locationUrl)
                    appendLine()
                    appendLine("User: $userName")
                    appendLine("Time: $timestamp")
                    appendLine("Sent via Suraksha Safety App")
                }

                for (contact in contacts) {
                    try {
                        sendSMS(contact.phoneNumber, updateMsg)
                        onSmsSent?.invoke(contact.name)
                    } catch (e: Exception) {
                        Log.e(TAG, "Periodic SMS to ${contact.name} failed: ${e.message}")
                    }
                    delay(500) // small gap between contacts
                }
            }
        }
    }

    // ── SMS helper ──────────────────────────────────────────────────────

    private fun sendSMS(phoneNumber: String, message: String) {
        if (!PermissionManager.hasSmsPermission(context)) {
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

    // ── Utility ─────────────────────────────────────────────────────────

    /** Build a Google Maps URL from the current location, or a fallback string. */
    fun getLocationUrl(): String {
        val loc = currentLocation ?: return "Location unavailable"
        return "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
    }
}
