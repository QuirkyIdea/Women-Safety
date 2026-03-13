package com.example.suraksha.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suraksha.ui.theme.SurakshaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.*
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneStateListener
import com.example.suraksha.data.SurakshaRepository
import com.example.suraksha.SurakshaApplication
import kotlinx.coroutines.runBlocking

class FakeCallActivity : ComponentActivity() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var repository: SurakshaRepository? = null
    private var emergencyContact: com.example.suraksha.data.EmergencyContact? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        repository = SurakshaRepository(
            SurakshaApplication.database.emergencyContactDao(),
            SurakshaApplication.database.appSettingsDao(),
            SurakshaApplication.database.safetyRecordDao()
        )
        
        // Get first emergency contact for real call
        runBlocking {
            val contacts = repository?.getActiveContacts()?.first()
            emergencyContact = contacts?.firstOrNull()
        }
        
        setContent {
            SurakshaTheme {
                FakeCallScreen(
                    onAnswer = { answerCall() },
                    onDecline = { declineCall() },
                    onEndCall = { endCall() }
                )
            }
        }
        
        // Start fake call
        startFakeCall()
    }
    
    private fun startFakeCall() {
        try {
            // Set audio mode to ringing
            audioManager?.mode = AudioManager.MODE_RINGTONE
            audioManager?.isSpeakerphoneOn = true
            
            // Start ringtone
            val ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@FakeCallActivity, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                setAudioStreamType(AudioManager.STREAM_RING)
                isLooping = true
                prepare()
                start()
            }
            
            // Start vibration
            startVibration()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startVibration() {
        vibrator?.let { vibrator ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 0)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1000, 500, 1000), 0)
            }
        }
    }
    
    private fun answerCall() {
        try {
            // Stop ringtone and vibration
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
            
            // Make real call to emergency contact
            emergencyContact?.let { contact ->
                if (com.example.suraksha.utils.PermissionManager.hasCallPermission(this)) {
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:${contact.phoneNumber}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } else {
                    android.widget.Toast.makeText(this, "Call permission not granted", android.widget.Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                // If no emergency contact, show error
                android.widget.Toast.makeText(this, "No emergency contact found", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // Set audio mode to call
            audioManager?.mode = AudioManager.MODE_IN_CALL
            audioManager?.isSpeakerphoneOn = true
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(this, "Error making call: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun declineCall() {
        try {
            // Stop ringtone and vibration
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
            
            // Reset audio mode
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            
            // End activity
            finish()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun endCall() {
        try {
            // Stop ringtone and vibration
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
            
            // Reset audio mode
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            
            // End activity
            finish()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false
    }
}

@Composable
fun FakeCallScreen(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit
) {
    var isCallAnswered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Animated scale for the phone icon
    val scale by animateFloatAsState(
        targetValue = if (isCallAnswered) 1f else 1.2f,
        animationSpec = tween(1000),
        label = "phone_scale"
    )
    
    // Pulse animation for incoming call
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isCallAnswered) 1f else 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Caller Info
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .scale(if (isCallAnswered) 1f else pulseAnimation),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Caller",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (isCallAnswered) "Call in Progress" else "Incoming Call",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isCallAnswered) "Emergency Contact" else "Emergency Contact",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isCallAnswered) "00:03" else "Calling...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Call Actions
            if (!isCallAnswered) {
                // Incoming call actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline Button
                    Button(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Answer Button
                    Button(
                        onClick = {
                            isCallAnswered = true
                            onAnswer()
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Green
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                // Call in progress actions
                Button(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Help Text
            Text(
                text = if (isCallAnswered) 
                    "Use this call to escape uncomfortable situations" 
                else 
                    "This is a fake call to help you escape",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
        
        // Emergency SOS Button (small, in corner)
        if (!isCallAnswered) {
            val context = LocalContext.current
            Button(
                onClick = {
                    // Trigger real SOS
                    val intent = Intent(context, com.example.suraksha.services.SafetyService::class.java).apply {
                        action = com.example.suraksha.services.SafetyService.ACTION_SOS
                    }
                    context.startService(intent)
                    onDecline() // End fake call
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text(
                    text = "🚨",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun FakeCallScheduler(
    onScheduleCall: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Default.Info, contentDescription = "Schedule Call")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Schedule Fake Call")
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Schedule Fake Call") },
                text = { Text("Choose when to receive a fake call:") },
                confirmButton = {
                    Column {
                        TextButton(
                            onClick = {
                                onScheduleCall(10)
                                showDialog = false
                            }
                        ) {
                            Text("10 seconds")
                        }
                        TextButton(
                            onClick = {
                                onScheduleCall(30)
                                showDialog = false
                            }
                        ) {
                            Text("30 seconds")
                        }
                        TextButton(
                            onClick = {
                                onScheduleCall(60)
                                showDialog = false
                            }
                        ) {
                            Text("1 minute")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
