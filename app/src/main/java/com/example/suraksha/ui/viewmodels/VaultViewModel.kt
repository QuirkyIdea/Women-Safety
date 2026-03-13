package com.example.suraksha.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.suraksha.data.VaultFile
import com.example.suraksha.utils.SecureVaultManager
import com.example.suraksha.utils.VaultEncryptionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VaultViewModel"
    }

    private val vaultManager = SecureVaultManager(application)

    // Initialize key once on creation
    init {
        VaultEncryptionHelper.getOrCreateKey()
    }

    // ── State ────────────────────────────────────────────────────────────

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _vaultFiles = MutableStateFlow<List<VaultFile>>(emptyList())
    val vaultFiles: StateFlow<List<VaultFile>> = _vaultFiles.asStateFlow()

    private val _statsText = MutableStateFlow("0 files · 0 B")
    val statsText: StateFlow<String> = _statsText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Public functions ─────────────────────────────────────────────────

    fun unlock() {
        _isUnlocked.value = true
        refreshFiles()
    }

    fun lock() {
        _isUnlocked.value = false
        _vaultFiles.value = emptyList()
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val files = vaultManager.getVaultFiles()
                _vaultFiles.value = files
                updateStats(files)
            } catch (e: Exception) {
                Log.e(TAG, "refreshFiles failed: ${e.message}", e)
                _errorMessage.value = "Failed to load vault files"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFile(vaultFile: VaultFile) {
        viewModelScope.launch {
            try {
                vaultManager.deleteFromVault(vaultFile)
                refreshFiles()
            } catch (e: Exception) {
                Log.e(TAG, "deleteFile failed: ${e.message}", e)
                _errorMessage.value = "Failed to delete file"
            }
        }
    }

    fun decryptForPlayback(vaultFile: VaultFile, onReady: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val tempFile = vaultManager.decryptToTemp(vaultFile)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    if (tempFile != null) {
                        onReady(tempFile)
                    } else {
                        _errorMessage.value = "Failed to decrypt file"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "decryptForPlayback failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _errorMessage.value = "Decryption error: ${e.message}"
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // Delete entire vault_temp/ folder recursively
        try {
            val tempDir = File(getApplication<Application>().cacheDir, "vault_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "vault_temp cleanup failed: ${e.message}")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun updateStats(files: List<VaultFile>) {
        val count = files.size
        val totalBytes = files.sumOf { it.sizeBytes }
        _statsText.value = "$count file${if (count != 1) "s" else ""} · ${formatBytes(totalBytes)}"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
