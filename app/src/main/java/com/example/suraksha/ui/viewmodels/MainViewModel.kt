package com.example.suraksha.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.suraksha.data.SurakshaRepository
import com.example.suraksha.services.SafetyService
import com.example.suraksha.services.SOSService
import com.example.suraksha.utils.PermissionManager
import com.example.suraksha.utils.PowerButtonDetector
import com.example.suraksha.utils.ShakeDetector
import com.example.suraksha.utils.VoiceTriggerManager
import com.example.suraksha.utils.IconManager
import com.example.suraksha.SurakshaApplication
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Central ViewModel for the Suraksha app.
 *
 * SOS trigger sources: Voice phrase, Shake (~10 s), Power button (4x).
 * On SOS  → starts [SOSService] (SMS, calls, audio loop, WhatsApp).
 * On "OK" → stops [SOSService] + shake detection; voice trigger stays active.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SurakshaRepository(
        SurakshaApplication.database.emergencyContactDao(),
        SurakshaApplication.database.appSettingsDao(),
        SurakshaApplication.database.safetyRecordDao(),
        SurakshaApplication.database.incidentLogDao()
    )

    val contactsViewModel = ContactsViewModel(application)

    // Location
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // Detectors
    private val powerButtonDetector = PowerButtonDetector(application)
    private val voiceTrigger = VoiceTriggerManager(application)
    private val shakeDetector = ShakeDetector(application)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val emergencyContacts = repository.getActiveContacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // ── Init ────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val hasOnboarding = repository.getBooleanSetting("onboarding_completed", false)
            val disguised = repository.getBooleanSetting("disguised_mode", false)
            val voiceEnabled = repository.getBooleanSetting("voice_detection_enabled", true)
            val shakeEnabled = repository.getBooleanSetting("shake_detection_enabled", true)
            val autoRec = repository.getBooleanSetting("auto_recording_enabled", true)
            val silentAlerts = repository.getBooleanSetting("silent_alerts_enabled", false)
            val sosMsg = repository.getSetting("sos_message")
                ?: "I AM IN DANGER! Please help me immediately!"

            _uiState.value = _uiState.value.copy(
                hasCompletedOnboarding = hasOnboarding,
                isDisguisedMode = disguised,
                isVoiceDetectionEnabled = voiceEnabled,
                isShakeDetectionEnabled = shakeEnabled,
                isAutoRecordingEnabled = autoRec,
                isSilentAlertsEnabled = silentAlerts,
                sosMessage = sosMsg
            )

            if (PermissionManager.hasLocationPermission(getApplication())) startLocationUpdates()

            // Always start power-button detection
            powerButtonDetector.setOnPowerButtonListener { triggerSOS("power button") }
            powerButtonDetector.startListening()

            // Voice trigger — stays on even after SOS stops
            val cmd = repository.getSetting("voice_command") ?: "emergency help me"
            voiceTrigger.setCommand(cmd)
            voiceTrigger.setOnTriggerListener { triggerSOS("voice command") }
            voiceTrigger.setOnErrorListener { err ->
                _uiState.value = _uiState.value.copy(errorMessage = "Voice: $err")
            }
            if (voiceEnabled && PermissionManager.hasAudioPermission(getApplication())) {
                voiceTrigger.startListening()
            }

            // Shake trigger
            if (shakeEnabled) {
                shakeDetector.setOnShakeListener { triggerSOS("shake detection") }
                shakeDetector.startListening()
            }

            // Recording ticker
            val active = repository.getBooleanSetting("recording_active", false)
            val startedAt = repository.getSetting("recording_started_at")?.toLongOrNull() ?: 0L
            _uiState.value = _uiState.value.copy(
                isRecordingActive = active,
                recordingElapsedMs = if (active && startedAt > 0) System.currentTimeMillis() - startedAt else 0
            )
            if (active) startRecordingTicker()
        }
    }

    // ── SOS trigger → starts SOSService ─────────────────────────────────

    fun triggerSOS(trigger: String = "manual") {
        if (_uiState.value.isSOSTriggered) return          // already running
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSOSTriggered = true)
            val intent = Intent(getApplication(), SOSService::class.java).apply {
                action = SOSService.ACTION_START
                putExtra(SOSService.EXTRA_TRIGGER, trigger)
            }
            getApplication<Application>().startService(intent)

            repository.addSafetyRecord("SOS",
                latitude = _currentLocation.value?.latitude,
                longitude = _currentLocation.value?.longitude)
        }
    }

    /**
     * "OK" button handler.
     * Stops: SOSService, audio recording, calls, SMS updates, shake detection.
     * Keeps: VoiceTriggerManager active.
     */
    fun stopSOS() {
        val intent = Intent(getApplication(), SOSService::class.java).apply {
            action = SOSService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)

        // Stop shake detection (per requirements)
        shakeDetector.stopListening()

        // Voice trigger stays active — do NOT stop it

        _uiState.value = _uiState.value.copy(
            isSOSTriggered = false,
            isShakeDetectionEnabled = false      // reflect in UI
        )

        viewModelScope.launch {
            repository.setBooleanSetting("shake_detection_enabled", false)
        }
    }

    // ── Location ────────────────────────────────────────────────────────

    fun startLocationUpdates() {
        if (!PermissionManager.hasLocationPermission(getApplication())) return
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false).setMinUpdateIntervalMillis(5000).build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    r.lastLocation?.let { loc ->
                        _currentLocation.value = loc
                        _uiState.value = _uiState.value.copy(
                            lastKnownLocation = "Lat: ${loc.latitude}, Lng: ${loc.longitude}")
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(req, cb,
                getApplication<Application>().mainLooper)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Location error: ${e.message}")
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────

    fun startTimer(minutes: Int) {
        viewModelScope.launch {
            val end = LocalDateTime.now().plusMinutes(minutes.toLong())
            _timerState.value = TimerState(true, end, minutes)
            repository.addSafetyRecord(type = "TIMER_START")
            while (_timerState.value.isActive && LocalDateTime.now().isBefore(end)) {
                kotlinx.coroutines.delay(1000)
                _timerState.value = _timerState.value.copy(
                    remainingSeconds = java.time.Duration.between(LocalDateTime.now(), end).seconds)
            }
            if (_timerState.value.isActive) { triggerSOS("safety timer"); _timerState.value = TimerState() }
        }
    }

    fun cancelTimer() {
        _timerState.value = TimerState()
        viewModelScope.launch { repository.addSafetyRecord(type = "TIMER_CANCELLED") }
    }

    // ── Disguised mode ──────────────────────────────────────────────────

    fun setDisguisedMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBooleanSetting("disguised_mode", enabled)
            _uiState.value = _uiState.value.copy(isDisguisedMode = enabled)
            if (enabled) IconManager.setCalculatorIcon(getApplication())
            else IconManager.setNormalIcon(getApplication())
        }
    }

    // ── Onboarding ──────────────────────────────────────────────────────

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setBooleanSetting("onboarding_completed", true)
            _uiState.value = _uiState.value.copy(hasCompletedOnboarding = true)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    // ── Voice trigger ───────────────────────────────────────────────────

    fun setVoiceCommand(command: String) {
        viewModelScope.launch {
            repository.setSetting("voice_command", command)
            voiceTrigger.setCommand(command)
        }
    }
    fun getVoiceCommand(): String = voiceTrigger.getCommand()

    fun toggleVoiceDetection(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBooleanSetting("voice_detection_enabled", enabled)
            if (enabled && PermissionManager.hasAudioPermission(getApplication()))
                voiceTrigger.startListening()
            else voiceTrigger.stopListening()
            _uiState.value = _uiState.value.copy(isVoiceDetectionEnabled = enabled)
        }
    }
    fun isVoiceDetectionActive(): Boolean = voiceTrigger.isActive()

    // ── Shake detection ─────────────────────────────────────────────────

    fun toggleShakeDetection(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBooleanSetting("shake_detection_enabled", enabled)
            if (enabled) {
                shakeDetector.setOnShakeListener { triggerSOS("shake detection") }
                shakeDetector.startListening()
            } else shakeDetector.stopListening()
            _uiState.value = _uiState.value.copy(isShakeDetectionEnabled = enabled)
        }
    }
    fun isShakeDetectionActive(): Boolean = shakeDetector.isActive()

    // ── Auto-recording toggle ───────────────────────────────────────────

    fun toggleAutoRecording(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBooleanSetting("auto_recording_enabled", enabled)
            _uiState.value = _uiState.value.copy(isAutoRecordingEnabled = enabled)
        }
    }

    // ── Silent alerts toggle ────────────────────────────────────────────

    fun toggleSilentAlerts(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBooleanSetting("silent_alerts_enabled", enabled)
            _uiState.value = _uiState.value.copy(isSilentAlertsEnabled = enabled)
        }
    }

    // ── SOS message ─────────────────────────────────────────────────────

    fun setSOSMessage(message: String) {
        viewModelScope.launch {
            repository.setSetting("sos_message", message)
            _uiState.value = _uiState.value.copy(sosMessage = message)
        }
    }
    fun getSOSMessage(): String = _uiState.value.sosMessage

    // ── Recording ticker (for UI) ───────────────────────────────────────

    private fun startRecordingTicker() {
        viewModelScope.launch {
            while (_uiState.value.isRecordingActive) {
                val at = repository.getSetting("recording_started_at")?.toLongOrNull() ?: 0L
                _uiState.value = _uiState.value.copy(
                    recordingElapsedMs = if (at > 0) System.currentTimeMillis() - at else 0)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun refreshRecordingState() {
        viewModelScope.launch {
            val active = repository.getBooleanSetting("recording_active", false)
            val at = repository.getSetting("recording_started_at")?.toLongOrNull() ?: 0L
            _uiState.value = _uiState.value.copy(
                isRecordingActive = active,
                recordingElapsedMs = if (active && at > 0) System.currentTimeMillis() - at else 0)
            if (active) startRecordingTicker()
        }
    }

    // ── Share location ──────────────────────────────────────────────────

    fun shareLocationWithContacts() {
        viewModelScope.launch {
            val contacts = repository.getActiveContacts().first()
            if (contacts.isEmpty()) {
                _uiState.value = _uiState.value.copy(errorMessage = "No emergency contacts found.")
                return@launch
            }
            val loc = _currentLocation.value
            if (loc == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Location not available.")
                return@launch
            }
            contacts.forEach { c ->
                val intent = Intent(getApplication(), SafetyService::class.java).apply {
                    action = SafetyService.ACTION_SEND_LOCATION_SMS
                    putExtra("phone_number", c.phoneNumber)
                    putExtra("contact_name", c.name)
                }
                getApplication<Application>().startService(intent)
            }
            _uiState.value = _uiState.value.copy(
                errorMessage = "Location shared with ${contacts.size} contacts!")
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        powerButtonDetector.stopListening()
        voiceTrigger.destroy()
        shakeDetector.stopListening()
    }
}

// ── Data classes ─────────────────────────────────────────────────────────

data class MainUiState(
    val hasCompletedOnboarding: Boolean = false,
    val isDisguisedMode: Boolean = false,
    val isSOSTriggered: Boolean = false,
    val lastKnownLocation: String = "Location not available",
    val errorMessage: String? = null,
    val isVoiceDetectionEnabled: Boolean = false,
    val isShakeDetectionEnabled: Boolean = true,
    val isAutoRecordingEnabled: Boolean = true,
    val isSilentAlertsEnabled: Boolean = false,
    val sosMessage: String = "I AM IN DANGER! Please help me immediately!",
    val isRecordingActive: Boolean = false,
    val recordingElapsedMs: Long = 0
)

data class TimerState(
    val isActive: Boolean = false,
    val endTime: LocalDateTime? = null,
    val totalMinutes: Int = 0,
    val remainingSeconds: Long = 0
)
