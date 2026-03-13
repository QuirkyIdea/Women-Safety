package com.example.suraksha.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suraksha.MainActivity
import com.example.suraksha.ui.theme.SurakshaTheme
import com.example.suraksha.utils.*

class PermissionRequestActivity : ComponentActivity() {
    
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
                PermissionRequestScreen(
                    onRequestPermissions = { permissions -> requestPermissionLauncher.launch(permissions) },
                    onOpenSettings = { openAppSettings() },
                    onSkip = { skipPermissions() }
                )
            }
        }
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    private fun startMainActivity() {
        // Mark that user has seen permission request
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_seen_permission_request", true).apply()
        
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun skipPermissions() {
        // Mark that user has skipped permissions
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_skipped_permissions", true).apply()
        startMainActivity()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onRequestPermissions: (Array<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val permissionsByCategory = remember { PermissionManager.getPermissionsByCategory(context) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var selectedPermission by remember { mutableStateOf<PermissionInfo?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions Required") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Security",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Essential Permissions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Suraksha needs these permissions to keep you safe in emergencies",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Permission Categories
            permissionsByCategory.forEach { (category, permissions) ->
                PermissionCategoryCard(
                    category = category,
                    permissions = permissions,
                    onPermissionClick = { permission ->
                        selectedPermission = permission
                        showRationaleDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
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
                    onClick = {
                        val missingPermissions = PermissionManager.getMissingCriticalPermissions(context)
                        if (missingPermissions.isNotEmpty()) {
                            onRequestPermissions(missingPermissions.toTypedArray())
                        } else {
                            onSkip()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Grant Critical Permissions")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Settings Button
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open App Settings")
            }
        }
    }
    
    // Rationale Dialog
    if (showRationaleDialog && selectedPermission != null) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Permission Required") },
            text = { 
                Text(selectedPermission!!.description)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationaleDialog = false
                        onRequestPermissions(arrayOf(selectedPermission!!.permission))
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionCategoryCard(
    category: PermissionCategory,
    permissions: List<PermissionInfo>,
    onPermissionClick: (PermissionInfo) -> Unit
) {
    val categoryInfo = getCategoryInfo(category)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Category Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = categoryInfo.icon,
                    contentDescription = categoryInfo.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = categoryInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = categoryInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Permissions List
            permissions.forEach { permission ->
                PermissionItem(
                    permission = permission,
                    onClick = { onPermissionClick(permission) }
                )
                
                if (permission != permissions.last()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    permission: PermissionInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getPermissionDisplayName(permission.permission),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Status Icon
        Icon(
            imageVector = getStatusIcon(permission.status),
            contentDescription = permission.status.name,
            tint = getStatusColor(permission.status),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun getStatusIcon(status: PermissionStatus) = when (status) {
    PermissionStatus.GRANTED -> Icons.Default.CheckCircle
    PermissionStatus.DENIED_CAN_ASK_AGAIN -> Icons.Default.Warning
    PermissionStatus.DENIED_DONT_ASK_AGAIN -> Icons.Default.Close
}

@Composable
fun getStatusColor(status: PermissionStatus) = when (status) {
    PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primary
    PermissionStatus.DENIED_CAN_ASK_AGAIN -> MaterialTheme.colorScheme.error
    PermissionStatus.DENIED_DONT_ASK_AGAIN -> MaterialTheme.colorScheme.error
}

data class CategoryInfo(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

fun getCategoryInfo(category: PermissionCategory): CategoryInfo = when (category) {
    PermissionCategory.EMERGENCY -> CategoryInfo(
        "Emergency Features",
        "Required for sending SOS alerts and making emergency calls",
        Icons.Default.Warning
    )
    PermissionCategory.LOCATION -> CategoryInfo(
        "Location Services",
        "Required for sharing your location during emergencies",
        Icons.Default.LocationOn
    )
    PermissionCategory.AUDIO -> CategoryInfo(
        "Audio Recording",
        "Required for voice commands and emergency recordings",
        Icons.Default.PlayArrow
    )
    PermissionCategory.CAMERA -> CategoryInfo(
        "Camera",
        "Required for front-camera video evidence recording",
        Icons.Default.Videocam
    )
    PermissionCategory.NOTIFICATIONS -> CategoryInfo(
        "Notifications",
        "Required for emergency alerts and status updates",
        Icons.Default.Notifications
    )
    PermissionCategory.OTHER -> CategoryInfo(
        "Other Permissions",
        "Additional permissions for app functionality",
        Icons.Default.Info
    )
}

fun getPermissionDisplayName(permission: String): String = when (permission) {
    Manifest.permission.SEND_SMS -> "Send SMS"
    Manifest.permission.CALL_PHONE -> "Make Calls"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"
    Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate Location"
    Manifest.permission.RECORD_AUDIO -> "Record Audio"
    Manifest.permission.CAMERA -> "Camera"
    Manifest.permission.VIBRATE -> "Vibrate"
    Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
    else -> permission.substringAfterLast(".")
}
