package com.example.suraksha.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * SupportResourcesScreen — quick-access panel of emergency helpline
 * numbers that the user can tap to call instantly.
 *
 * Includes:
 *  • Women Helpline (India): 1091
 *  • National Emergency: 112
 *  • Ambulance: 102
 *  • Police: 100
 *  • Fire Brigade: 101
 *  • Women Helpline (Domestic Abuse): 181
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportResourcesScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Support Resources",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
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
                text = "Emergency Helplines",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tap any number to call instantly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary Emergency Numbers ───────────────────────────────

            SectionHeader(
                icon = Icons.Default.Emergency,
                title = "Primary Emergency"
            )
            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.Female,
                title = "Women Helpline (India)",
                number = "1091",
                description = "24/7 helpline for women in distress",
                iconTint = Color(0xFFE91E63),
                onCall = { dialNumber(context, "1091") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.LocalPhone,
                title = "National Emergency",
                number = "112",
                description = "Unified emergency number for police, fire & ambulance",
                iconTint = Color(0xFFD32F2F),
                onCall = { dialNumber(context, "112") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.LocalHospital,
                title = "Ambulance",
                number = "102",
                description = "Medical emergency and ambulance service",
                iconTint = Color(0xFF388E3C),
                onCall = { dialNumber(context, "102") }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Additional Helplines ────────────────────────────────────

            SectionHeader(
                icon = Icons.Default.SupportAgent,
                title = "Additional Helplines"
            )
            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.LocalPolice,
                title = "Police",
                number = "100",
                description = "Local police emergency line",
                iconTint = Color(0xFF1565C0),
                onCall = { dialNumber(context, "100") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.Fireplace,
                title = "Fire Brigade",
                number = "101",
                description = "Fire emergency services",
                iconTint = Color(0xFFE65100),
                onCall = { dialNumber(context, "101") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            HelplineCard(
                icon = Icons.Default.Shield,
                title = "Women Helpline (Domestic Abuse)",
                number = "181",
                description = "Support for domestic violence and abuse",
                iconTint = Color(0xFF7B1FA2),
                onCall = { dialNumber(context, "181") }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Safety Tip ──────────────────────────────────────────────

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Safety Tip",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Save these numbers in your phone for quick access. " +
                                "In an emergency, stay calm and provide your location clearly. " +
                                "The Suraksha app can automatically share your GPS location with your trusted contacts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Helper composables ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HelplineCard(
    icon: ImageVector,
    title: String,
    number: String,
    description: String,
    iconTint: Color,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Call button
            FilledTonalButton(
                onClick = onCall,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call $number",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** Open the phone dialer with the given number. */
private fun dialNumber(context: android.content.Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
