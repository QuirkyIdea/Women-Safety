package com.example.suraksha.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suraksha.MainActivity
import com.example.suraksha.ui.theme.SurakshaTheme
import com.example.suraksha.utils.PermissionManager

class OnboardingActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startMainActivity()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SurakshaTheme {
                OnboardingScreen(
                    onGetStarted = { requestPermissions() },
                    onSkip = { skipOnboarding() }
                )
            }
        }
    }
    
    private fun requestPermissions() {
        val missingPermissions = PermissionManager.getMissingCriticalPermissions(this)
        if (missingPermissions.isEmpty()) {
            startMainActivity()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun skipOnboarding() {
        // Mark onboarding as completed
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
        startMainActivity()
    }
    
    private fun startMainActivity() {
        // Mark onboarding as completed
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
        
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Icon/Logo
            Card(
                modifier = Modifier.size(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛡️",
                        fontSize = 48.sp
                    )
                }
            }
            
            // Title
            Text(
                text = "Welcome to Suraksha",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Your Personal Safety Companion",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Features
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Key Features",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    FeatureItem(
                        icon = "🚨",
                        title = "One-Tap SOS",
                        description = "Send emergency alerts with your location to trusted contacts"
                    )
                    
                    FeatureItem(
                        icon = "🔋",
                        title = "Power Button SOS",
                        description = "Press power button 3 times to trigger SOS automatically"
                    )
                    
                    FeatureItem(
                        icon = "🎤",
                        title = "Voice Command SOS",
                        description = "Say custom voice command to trigger SOS hands-free"
                    )
                    
                    FeatureItem(
                        icon = "📞",
                        title = "Fake Call",
                        description = "Simulate incoming calls to escape uncomfortable situations"
                    )
                    
                    FeatureItem(
                        icon = "⏰",
                        title = "Safety Timer",
                        description = "Set countdown timers that trigger SOS if not cancelled"
                    )
                    
                    FeatureItem(
                        icon = "🎙️",
                        title = "Emergency Recording",
                        description = "Automatically record audio during SOS events"
                    )
                }
            }
            
            // Permissions Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Required Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "• SMS: To send emergency messages\n" +
                              "• Location: To share your location with contacts\n" +
                              "• Microphone: To record emergency audio\n" +
                              "• Notifications: To show emergency alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip for Now")
                }
                
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get Started")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FeatureItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
