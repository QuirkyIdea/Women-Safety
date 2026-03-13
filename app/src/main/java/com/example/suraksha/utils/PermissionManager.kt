package com.example.suraksha.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central permission helper for the Suraksha safety app.
 *
 * Groups all runtime permissions used by the app, provides convenience
 * check methods for individual permission categories, and supports
 * the Settings UI "permission status" section.
 */
object PermissionManager {
    
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.VIBRATE,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.CALL_PHONE
    )
    
    val PERMISSION_DESCRIPTIONS = mapOf(
        Manifest.permission.SEND_SMS to "Send emergency SMS messages to your contacts",
        Manifest.permission.ACCESS_FINE_LOCATION to "Get your precise location for emergency alerts",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Get your approximate location for emergency alerts",
        Manifest.permission.RECORD_AUDIO to "Record emergency audio and enable voice commands",
        Manifest.permission.CAMERA to "Record front-camera video evidence during SOS",
        Manifest.permission.VIBRATE to "Provide vibration alerts during emergencies",
        Manifest.permission.POST_NOTIFICATIONS to "Show emergency notifications",
        Manifest.permission.CALL_PHONE to "Make emergency calls to your contacts"
    )
    
    val CRITICAL_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    // Optional permissions that can be skipped
    val OPTIONAL_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.VIBRATE,
        Manifest.permission.POST_NOTIFICATIONS
    )
    
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasCriticalPermissions(context: Context): Boolean {
        return CRITICAL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getMissingCriticalPermissions(context: Context): List<String> {
        return CRITICAL_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 13, notifications are granted by default
        }
    }
    
    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasSystemAlertWindowPermission(context: Context): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }
    
    fun getPermissionDescription(permission: String): String {
        return PERMISSION_DESCRIPTIONS[permission] ?: "Required for app functionality"
    }
    
    fun shouldShowPermissionRationale(context: Context, permission: String): Boolean {
        return if (context is android.app.Activity) {
            context.shouldShowRequestPermissionRationale(permission)
        } else {
            false
        }
    }
    
    fun getPermissionStatus(context: Context, permission: String): PermissionStatus {
        return when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> 
                PermissionStatus.GRANTED
            shouldShowPermissionRationale(context, permission) -> 
                PermissionStatus.DENIED_CAN_ASK_AGAIN
            else -> 
                PermissionStatus.DENIED_DONT_ASK_AGAIN
        }
    }
    
    fun getPermissionsByCategory(context: Context): Map<PermissionCategory, List<PermissionInfo>> {
        val categories = mutableMapOf<PermissionCategory, MutableList<PermissionInfo>>()
        
        REQUIRED_PERMISSIONS.forEach { permission ->
            val status = getPermissionStatus(context, permission)
            val category = getPermissionCategory(permission)
            val description = getPermissionDescription(permission)
            
            if (!categories.containsKey(category)) {
                categories[category] = mutableListOf()
            }
            
            categories[category]?.add(PermissionInfo(permission, description, status))
        }
        
        return categories
    }
    
    private fun getPermissionCategory(permission: String): PermissionCategory {
        return when (permission) {
            Manifest.permission.SEND_SMS -> PermissionCategory.EMERGENCY
            Manifest.permission.CALL_PHONE -> PermissionCategory.EMERGENCY
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> PermissionCategory.LOCATION
            Manifest.permission.RECORD_AUDIO -> PermissionCategory.AUDIO
            Manifest.permission.CAMERA -> PermissionCategory.CAMERA
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS -> PermissionCategory.NOTIFICATIONS
            else -> PermissionCategory.OTHER
        }
    }
}

enum class PermissionStatus {
    GRANTED,
    DENIED_CAN_ASK_AGAIN,
    DENIED_DONT_ASK_AGAIN
}

enum class PermissionCategory {
    EMERGENCY,
    LOCATION,
    AUDIO,
    CAMERA,
    NOTIFICATIONS,
    OTHER
}

data class PermissionInfo(
    val permission: String,
    val description: String,
    val status: PermissionStatus
)
