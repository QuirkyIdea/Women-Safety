package com.example.suraksha.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.suraksha.ui.viewmodels.MainViewModel
import com.example.suraksha.utils.*
import android.content.Context
import com.example.suraksha.ui.screens.OnboardingActivity

/**
 * Settings screen — lets the user configure every aspect of the safety system:
 *  • Emergency contacts (via Contacts tab)
 *  • SOS message phrase
 *  • Voice trigger phrase & toggle
 *  • Shake detection toggle
 *  • Auto-recording toggle
 *  • Disguised mode
 *  • Permission status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Text(
                text = "Configure Your Safety",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Customize your safety features and preferences",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // ── Permission Status ───────────────────────────────────────
            PermissionStatusSection()
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── SOS Message Phrase ──────────────────────────────────────
            SettingsSection(
                title = "SOS Message",
                icon = Icons.Default.Message,
                description = "Customize the help phrase sent during emergencies"
            ) {
                SOSMessageSetting(
                    currentMessage = uiState.sosMessage,
                    onMessageChange = { mainViewModel.setSOSMessage(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── Safety Features ─────────────────────────────────────────
            SettingsSection(
                title = "Safety Features",
                icon = Icons.Default.Security,
                description = "Configure emergency triggers and safety tools"
            ) {
                // Disguised Mode
                SettingsSwitch(
                    icon = Icons.Default.Lock,
                    title = "Disguise as Calculator",
                    subtitle = "App will appear as a Calculator on your home screen",
                    checked = uiState.isDisguisedMode,
                    onCheckedChange = { mainViewModel.setDisguisedMode(it) }
                )
                
                SettingsDivider()
                
                // Power Button SOS
                SettingsSwitch(
                    icon = Icons.Default.Power,
                    title = "Power Button SOS",
                    subtitle = "Press power button 4 times to trigger emergency",
                    checked = true,
                    onCheckedChange = { /* Always enabled */ }
                )
                
                SettingsDivider()
                
                // Shake Detection SOS
                SettingsSwitch(
                    icon = Icons.Default.Vibration,
                    title = "Shake Detection SOS",
                    subtitle = "Shake phone continuously for ~10 s to trigger SOS",
                    checked = uiState.isShakeDetectionEnabled,
                    onCheckedChange = { mainViewModel.toggleShakeDetection(it) }
                )
                
                SettingsDivider()
                
                // Voice Command SOS
                SettingsSwitch(
                    icon = Icons.Default.Mic,
                    title = "Voice Command SOS",
                    subtitle = "Trigger SOS with custom voice command",
                    checked = uiState.isVoiceDetectionEnabled,
                    onCheckedChange = { mainViewModel.toggleVoiceDetection(it) }
                )
                
                if (uiState.isVoiceDetectionEnabled) {
                    SettingsDivider()
                    VoiceCommandSetting(
                        currentCommand = mainViewModel.getVoiceCommand(),
                        onCommandChange = { mainViewModel.setVoiceCommand(it) }
                    )
                }
                
                SettingsDivider()
                
                // Auto Recording
                SettingsSwitch(
                    icon = Icons.Default.Videocam,
                    title = "Auto Evidence Recording",
                    subtitle = "Record front camera video + audio during SOS",
                    checked = uiState.isAutoRecordingEnabled,
                    onCheckedChange = { mainViewModel.toggleAutoRecording(it) }
                )
                
                SettingsDivider()
                
                // Silent Alerts
                SettingsSwitch(
                    icon = Icons.Default.NotificationsOff,
                    title = "Silent Alerts",
                    subtitle = "Send discreet SMS to contacts when SOS triggers",
                    checked = uiState.isSilentAlertsEnabled,
                    onCheckedChange = { mainViewModel.toggleSilentAlerts(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── Location Sharing ────────────────────────────────────────
            SettingsSection(
                title = "Location Sharing",
                icon = Icons.Default.LocationOn,
                description = "Location updates sent every 30 s during active SOS"
            ) {
                SettingsItem(
                    icon = Icons.Default.GpsFixed,
                    title = "Location Accuracy",
                    subtitle = "High accuracy GPS for precise location sharing",
                    onClick = { }
                )
                
                SettingsDivider()
                
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "Test Location Sharing",
                    subtitle = "Send test location to all emergency contacts",
                    onClick = { mainViewModel.shareLocationWithContacts() }
                )
                
                SettingsDivider()
                
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Location Privacy",
                    subtitle = "Location only shared when you choose or during SOS",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── Notifications ───────────────────────────────────────────
            SettingsSection(
                title = "Notifications",
                icon = Icons.Default.Notifications,
                description = "Configure alert preferences and notifications"
            ) {
                SettingsSwitch(
                    icon = Icons.Default.Emergency,
                    title = "Emergency Notifications",
                    subtitle = "Show notifications for safety events",
                    checked = true,
                    onCheckedChange = { }
                )
                
                SettingsDivider()
                
                SettingsSwitch(
                    icon = Icons.Default.Vibration,
                    title = "Vibration Alerts",
                    subtitle = "Vibrate on SOS trigger",
                    checked = true,
                    onCheckedChange = { }
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── Privacy ─────────────────────────────────────────────────
            SettingsSection(
                title = "Privacy & Security",
                icon = Icons.Default.Lock,
                description = "Data protection and security settings"
            ) {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "Data Storage",
                    subtitle = "All data stored locally on device",
                    onClick = { }
                )
                
                SettingsDivider()
                
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "Location Accuracy",
                    subtitle = "High accuracy GPS for precise location",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // ── About ───────────────────────────────────────────────────
            SettingsSection(
                title = "About",
                icon = Icons.Default.Info,
                description = "App information and support"
            ) {
                SettingsItem(
                    icon = Icons.Default.Apps,
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
                
                SettingsDivider()
                
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    subtitle = "How to use the app",
                    onClick = { }
                )
                
                SettingsDivider()
                
                SettingsItem(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy",
                    subtitle = "How we protect your data",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            EmergencyInfoCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Reset App State
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Reset App State",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reset onboarding and permission states for testing purposes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App State")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset App State", fontWeight = FontWeight.Bold) },
            text = { Text("This will reset the app to its initial state. You'll see the onboarding screen again. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val sharedPrefs = context.getSharedPreferences("suraksha_prefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().clear().apply()
                    showResetDialog = false
                    val intent = Intent(context, OnboardingActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Shared composables
// ═══════════════════════════════════════════════════════════════════════

/** Editable SOS message phrase setting. */
@Composable
fun SOSMessageSetting(
    currentMessage: String,
    onMessageChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(currentMessage) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { draft = currentMessage; showDialog = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit SOS Message",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SOS Help Phrase",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "\"$currentMessage\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2
            )
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set SOS Message", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter the help phrase that will be included in emergency SMS and WhatsApp messages.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("SOS Message") },
                        placeholder = { Text("I AM IN DANGER! Please help me immediately!") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.trim().isNotEmpty()) {
                        onMessageChange(draft.trim())
                        showDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { draft = currentMessage; showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    description: String,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
fun EmergencyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Emergency", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Emergency Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "• SOS button sends your location to emergency contacts\n" +
                      "• Press power button 4 times to trigger SOS\n" +
                      "• Shake phone for ~10 seconds to trigger SOS\n" +
                      "• Say custom voice command to trigger SOS\n" +
                      "• WhatsApp message sent automatically during SOS\n" +
                      "• Each contact is called for 10 s sequentially\n" +
                      "• Front camera + audio recorded as evidence\n" +
                      "• Location updates sent every 30 seconds during SOS\n" +
                      "• All data is stored locally for your privacy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4
            )
        }
    }
}

@Composable
fun VoiceCommandSetting(
    currentCommand: String,
    onCommandChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempCommand by remember { mutableStateOf(currentCommand) }
    
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Voice Command", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Voice Command", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("\"$currentCommand\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ArrowForward, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Voice Command", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a custom phrase to trigger SOS. Keep it simple and easy to remember.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempCommand,
                        onValueChange = { tempCommand = it },
                        label = { Text("Voice Command") },
                        placeholder = { Text("emergency help me") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempCommand.trim().isNotEmpty()) {
                        onCommandChange(tempCommand.trim())
                        showDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { tempCommand = currentCommand; showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PermissionStatusSection() {
    val context = LocalContext.current
    val permissionsByCategory = remember { PermissionManager.getPermissionsByCategory(context) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    SettingsSection(
        title = "Permission Status",
        icon = Icons.Default.Security,
        description = "Monitor and manage app permissions"
    ) {
        permissionsByCategory.forEach { (category, permissions) ->
            val categoryInfo = getCategoryInfo(category)
            val grantedCount = permissions.count { it.status == PermissionStatus.GRANTED }
            val totalCount = permissions.size
            
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showPermissionDialog = true }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (grantedCount == totalCount) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryInfo.icon,
                        contentDescription = categoryInfo.title,
                        tint = if (grantedCount == totalCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(categoryInfo.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("$grantedCount of $totalCount permissions granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.Default.ArrowForward, contentDescription = "View Details", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            
            if (category != permissionsByCategory.keys.last()) {
                SettingsDivider()
            }
        }
    }
    
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Details", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    permissionsByCategory.forEach { (category, permissions) ->
                        val categoryInfo = getCategoryInfo(category)
                        Text(categoryInfo.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        permissions.forEach { permission ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getStatusIcon(permission.status),
                                    contentDescription = permission.status.name,
                                    tint = getStatusColor(permission.status),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(getPermissionDisplayName(permission.permission), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(context, PermissionRequestActivity::class.java)
                    context.startActivity(intent)
                }) { Text("Manage Permissions") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Close") }
            }
        )
    }
}
