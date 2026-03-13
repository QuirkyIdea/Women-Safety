package com.example.suraksha.data

import kotlinx.serialization.Serializable

/**
 * Represents a single file stored in the secure vault.
 * Serializable for vault_index.json persistence.
 */
@Serializable
data class VaultFile(
    val id: String,           // UUID
    val originalName: String, // e.g. "sos_audio_20240315_143022.m4a"
    val type: VaultFileType,
    val timestamp: Long,      // System.currentTimeMillis() at vault time
    val sizeBytes: Long
)

@Serializable
enum class VaultFileType { AUDIO, VIDEO, LOG, UNKNOWN }

/**
 * Wrapper for the vault_index.json file — single source of truth for vault metadata.
 */
@Serializable
data class VaultIndex(val files: List<VaultFile> = emptyList())
