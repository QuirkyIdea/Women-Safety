package com.example.suraksha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.suraksha.data.IncidentLog
import com.example.suraksha.data.IncidentLogDao
import com.example.suraksha.SurakshaApplication
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * IncidentLogScreen — displays the list of SOS incident log entries
 * stored in the Room database.
 *
 * Entries are sorted newest-first and each shows:
 *  • Timestamp  (yyyy-MM-dd HH:mm:ss)
 *  • Event type (SOS_TRIGGERED, CALL_PLACED, SMS_SENT, etc.)
 *  • Human-readable details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentLogScreen(
    onBack: () -> Unit = {}
) {
    val dao = remember { SurakshaApplication.database.incidentLogDao() }
    val logs by dao.getAllLogs().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Incident Logs",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No incident logs yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Logs appear here when SOS is triggered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Summary card
                item {
                    SummaryCard(logs)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(logs, key = { it.id }) { log ->
                    LogEntryCard(log)
                }
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        val coroutineScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all incident logs. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    coroutineScope.launch { dao.clearAll() }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Summary card ─────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(logs: List<IncidentLog>) {
    val sosCount = logs.count { it.eventType == "SOS_TRIGGERED" }
    val callCount = logs.count { it.eventType == "CALL_PLACED" }
    val smsCount = logs.count { it.eventType == "SMS_SENT" }
    val locationCount = logs.count { it.eventType == "LOCATION_UPDATE" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("SOS", sosCount, Icons.Default.Warning)
                StatItem("Calls", callCount, Icons.Default.Call)
                StatItem("SMS", smsCount, Icons.Default.Message)
                StatItem("GPS", locationCount, Icons.Default.LocationOn)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Total: ${logs.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

// ── Individual log entry ─────────────────────────────────────────────────

private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
private fun LogEntryCard(log: IncidentLog) {
    val (icon, tint) = getEventIconAndColor(log.eventType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Event icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = log.eventType,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Event type badge
                Text(
                    text = formatEventType(log.eventType),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Details
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp
                Text(
                    text = log.timestamp.format(timeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun getEventIconAndColor(eventType: String): Pair<ImageVector, Color> = when (eventType) {
    "SOS_TRIGGERED"     -> Icons.Default.Warning to Color(0xFFD32F2F)
    "SOS_STOPPED"       -> Icons.Default.CheckCircle to Color(0xFF388E3C)
    "CALL_PLACED"       -> Icons.Default.Call to Color(0xFF1565C0)
    "CALL_ENDED"        -> Icons.Default.CallEnd to Color(0xFF1565C0)
    "SMS_SENT"          -> Icons.Default.Message to Color(0xFF7B1FA2)
    "SILENT_ALERT_SENT" -> Icons.Default.NotificationsOff to Color(0xFF455A64)
    "AUDIO_RECORDED"    -> Icons.Default.Mic to Color(0xFFE65100)
    "LOCATION_UPDATE"   -> Icons.Default.LocationOn to Color(0xFF00838F)
    "WHATSAPP_SENT"     -> Icons.Default.Share to Color(0xFF2E7D32)
    "ERROR"             -> Icons.Default.Error to Color(0xFFB71C1C)
    else                -> Icons.Default.Info to Color(0xFF616161)
}

private fun formatEventType(eventType: String): String =
    eventType.replace("_", " ")
