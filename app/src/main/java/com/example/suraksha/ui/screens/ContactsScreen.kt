package com.example.suraksha.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.suraksha.data.EmergencyContact
import com.example.suraksha.ui.viewmodels.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ContactsViewModel) {
    val context = LocalContext.current
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<EmergencyContact?>(null) }
    
    // Permission launcher for contacts
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, can access contacts
        }
    }
    
    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { contactUri ->
            val contact = getContactFromUri(context, contactUri)
            contact?.let { picked ->
                // Auto-save picked contact and close dialog
                viewModel.addContact(picked.name, picked.phoneNumber)
                selectedContact = null
                showAddDialog = false
                android.widget.Toast.makeText(
                    context,
                    "Added ${picked.name} to emergency contacts",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOS Numbers", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState.canAddMore) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Emergency Contacts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "These contacts will receive SOS alerts with your location. Add trusted family members, friends, or emergency services.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "${contacts.size} contacts added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // SMS Buttons
                    if (contacts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            
                            // Send SMS & Location to All Contacts
                            OutlinedButton(
                                onClick = { 
                                    contacts.forEach { contact ->
                                        val intent = Intent(context, com.example.suraksha.services.SafetyService::class.java).apply {
                                            action = "com.example.suraksha.SEND_EMERGENCY_SMS"
                                            putExtra("phone_number", contact.phoneNumber)
                                            putExtra("contact_name", contact.name)
                                        }
                                        context.startService(intent)
                                    }
                                    android.widget.Toast.makeText(
                                        context, 
                                        "SMS with location sent to ${contacts.size} contacts", 
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF1E3A8A), // Navy blue
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Send SMS & Location")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send SMS & Location to All Contacts")
                            }
                            
                            
                            // Send SMS & Location to Emergency Contacts (subset)
                            OutlinedButton(
                                onClick = { 
                                    // Send emergency SMS to all contacts
                                    contacts.take(3).forEach { contact ->
                                        val intent = Intent(context, com.example.suraksha.services.SafetyService::class.java).apply {
                                            action = "com.example.suraksha.SEND_EMERGENCY_SMS"
                                            putExtra("phone_number", contact.phoneNumber)
                                            putExtra("contact_name", contact.name)
                                        }
                                        context.startService(intent)
                                    }
                                    android.widget.Toast.makeText(
                                        context, 
                                        "Emergency SMS with location sent to ${kotlin.math.min(3, contacts.size)} contacts", 
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Send Emergency SMS")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send SMS & Location to Emergency Contacts")
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Contacts List
            if (contacts.isEmpty()) {
                EmptyContactsState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts) { contact ->
                        ContactCard(
                            contact = contact,
                            onEdit = { selectedContact = contact },
                            onDelete = { viewModel.deleteContact(contact) }
                        )
                    }
                }
            }
        }
    }
    
    // Add/Edit Contact Dialog
    if (showAddDialog || selectedContact != null) {
        ContactDialog(
            contact = selectedContact,
            onDismiss = {
                showAddDialog = false
                selectedContact = null
            },
            onSave = { name, phoneNumber ->
                if (selectedContact != null) {
                    viewModel.updateContact(
                        selectedContact!!.copy(
                            name = name,
                            phoneNumber = phoneNumber
                        )
                    )
                } else {
                    viewModel.addContact(name, phoneNumber)
                }
                showAddDialog = false
                selectedContact = null
            },
            onPickContact = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    contactPickerLauncher.launch(null)
                } else {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        )
    }
}

@Composable
fun EmptyContactsState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "No Contacts",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Emergency Contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Add trusted contacts who will receive SOS alerts when you're in danger.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.isActive) 
                MaterialTheme.colorScheme.surfaceContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contact Avatar
                Card(
                    modifier = Modifier.size(48.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Contact Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Actions
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Action Buttons
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                
                
                // Test SMS Button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, com.example.suraksha.services.SafetyService::class.java).apply {
                            action = "com.example.suraksha.SEND_TEST_SMS"
                            putExtra("phone_number", contact.phoneNumber)
                            putExtra("contact_name", contact.name)
                        }
                        context.startService(intent)
                        android.widget.Toast.makeText(
                            context,
                            "Test SMS sent to ${contact.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send Test SMS",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test SMS", style = MaterialTheme.typography.bodySmall)
                }
                
                // Emergency SMS Button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, com.example.suraksha.services.SafetyService::class.java).apply {
                            action = "com.example.suraksha.SEND_EMERGENCY_SMS"
                            putExtra("phone_number", contact.phoneNumber)
                            putExtra("contact_name", contact.name)
                        }
                        context.startService(intent)
                        android.widget.Toast.makeText(
                            context,
                            "Emergency SMS sent to ${contact.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Send Emergency SMS",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SOS", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact") },
            text = { Text("Are you sure you want to delete ${contact.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDialog(
    contact: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (name: String, phoneNumber: String) -> Unit,
    onPickContact: () -> Unit
) {
    var name by remember(contact?.id, contact?.name) { mutableStateOf(contact?.name ?: "") }
    var phoneNumber by remember(contact?.id, contact?.phoneNumber) { mutableStateOf(contact?.phoneNumber ?: "") }
    var nameError by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (contact == null) "Add Emergency Contact" else "Edit Contact") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pick from contacts button
                OutlinedButton(
                    onClick = onPickContact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Pick Contact")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick from Contacts")
                }
                
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = ""
                    },
                    label = { Text("Name *") },
                    isError = nameError.isNotEmpty(),
                    supportingText = { if (nameError.isNotEmpty()) Text(nameError) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Phone number field
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { 
                        phoneNumber = it
                        phoneError = ""
                    },
                    label = { Text("Phone Number *") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    isError = phoneError.isNotEmpty(),
                    supportingText = { if (phoneError.isNotEmpty()) Text(phoneError) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate inputs
                    if (name.trim().isEmpty()) {
                        nameError = "Name is required"
                        return@TextButton
                    }
                    if (phoneNumber.trim().isEmpty()) {
                        phoneError = "Phone number is required"
                        return@TextButton
                    }
                    if (!isValidPhoneNumber(phoneNumber.trim())) {
                        phoneError = "Please enter a valid phone number"
                        return@TextButton
                    }
                    
                    onSave(name.trim(), phoneNumber.trim())
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getContactFromUri(context: android.content.Context, uri: Uri): EmergencyContact? {
    return try {
        val resolver = context.contentResolver
        // Resolve contact ID from the picked Uri
        val idCursor = resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )
        val contactId = idCursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: return null

        // Now query the Phone table for the given contact ID, prefer MOBILE type
        val phoneCursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        var pickedName: String? = null
        var pickedPhone: String? = null
        phoneCursor?.use { c ->
            var fallbackName: String? = null
            var fallbackPhone: String? = null
            while (c.moveToNext()) {
                val name = c.getString(0) ?: ""
                val rawPhone = c.getString(1) ?: ""
                val type = c.getInt(2)
                val cleaned = rawPhone.replace("\\s".toRegex(), "").replace("-", "")
                if (fallbackName == null && fallbackPhone == null) {
                    fallbackName = name
                    fallbackPhone = cleaned
                }
                if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                    pickedName = name
                    pickedPhone = cleaned
                    break
                }
            }
            if (pickedName == null || pickedPhone == null) {
                pickedName = fallbackName
                pickedPhone = fallbackPhone
            }
        }

        if (!pickedName.isNullOrEmpty() && !pickedPhone.isNullOrEmpty()) {
            return EmergencyContact(
                id = 0,
                name = pickedName!!,
                phoneNumber = pickedPhone!!,
                isActive = true
            )
        }
        null
    } catch (e: Exception) {
        android.util.Log.e("ContactsScreen", "Error getting contact from URI: ${e.message}")
        null
    }
}

private fun isValidPhoneNumber(phoneNumber: String): Boolean {
    // Remove all non-digit characters
    val digitsOnly = phoneNumber.replace("\\D".toRegex(), "")
    
    // Check if it's a valid Indian phone number (10 digits) or international format
    return digitsOnly.length == 10 || (digitsOnly.length >= 10 && digitsOnly.length <= 15)
}


