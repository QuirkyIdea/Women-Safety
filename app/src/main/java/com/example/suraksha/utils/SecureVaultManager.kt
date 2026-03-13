package com.example.suraksha.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.suraksha.data.VaultFile
import com.example.suraksha.data.VaultFileType
import com.example.suraksha.data.VaultIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manages the secure vault — encrypts evidence files into internal storage
 * and maintains a vault_index.json as single source of truth for metadata.
 *
 * Vault folder: context.filesDir/secure_vault/
 * SD demo folder: Downloads/SurakshaVault/ (best-effort copy)
 */
class SecureVaultManager(private val context: Context) {

    companion object {
        private const val TAG = "SecureVaultManager"
    }

    private val vaultDir = File(context.filesDir, "secure_vault").also { it.mkdirs() }
    private val indexFile = File(vaultDir, "vault_index.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /**
     * SD card demo directory — Downloads/SurakshaVault/.
     * Null if external storage is unavailable.
     */
    private val sdDemoDir: File? by lazy {
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (downloads != null && (downloads.exists() || downloads.mkdirs())) {
                File(downloads, "SurakshaVault").also { it.mkdirs() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "SD demo dir unavailable: ${e.message}")
            null
        }
    }

    // ── Index helpers (private) ──────────────────────────────────────────

    private fun readIndex(): VaultIndex {
        return try {
            if (indexFile.exists()) {
                json.decodeFromString<VaultIndex>(indexFile.readText())
            } else {
                VaultIndex()
            }
        } catch (e: Exception) {
            Log.e(TAG, "readIndex failed: ${e.message}", e)
            VaultIndex()
        }
    }

    private fun writeIndex(index: VaultIndex) {
        try {
            indexFile.writeText(json.encodeToString(VaultIndex.serializer(), index))
        } catch (e: Exception) {
            Log.e(TAG, "writeIndex failed: ${e.message}", e)
        }
    }

    // ── Public suspend functions ─────────────────────────────────────────

    /**
     * Encrypt [source] and add to vault. COPIES the file — never deletes the original.
     *
     * @return true if vault encryption succeeded (SD copy failure does not affect return).
     */
    suspend fun addToVault(
        source: File,
        type: VaultFileType,
        originalName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val encFileName = "${timestamp}_${type.name}_${id}.enc"
            val encFile = File(vaultDir, encFileName)

            val key = VaultEncryptionHelper.getOrCreateKey()
            val success = VaultEncryptionHelper.encryptFileTo(source, encFile, key)

            if (success) {
                val entry = VaultFile(
                    id = id,
                    originalName = originalName,
                    type = type,
                    timestamp = timestamp,
                    sizeBytes = source.length()
                )
                val index = readIndex()
                writeIndex(VaultIndex(files = index.files + entry))

                // SD demo: copy .enc file as-is (no re-encryption)
                try {
                    sdDemoDir?.let { dir ->
                        encFile.copyTo(File(dir, "${id}.enc"), overwrite = true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SD demo copy failed (non-fatal): ${e.message}")
                }

                Log.d(TAG, "Added to vault: $originalName → $encFileName")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "addToVault failed: ${e.message}", e)
            false
        }
    }

    /**
     * List all vault files sorted by timestamp (newest first).
     */
    suspend fun getVaultFiles(): List<VaultFile> = withContext(Dispatchers.IO) {
        try {
            readIndex().files.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "getVaultFiles failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Decrypt a vault file to a temp directory for playback.
     * Temp location: cacheDir/vault_temp/{randomUUID}_{originalName}
     *
     * @return the decrypted temp file, or null on failure.
     */
    suspend fun decryptToTemp(vaultFile: VaultFile): File? = withContext(Dispatchers.IO) {
        try {
            // Find the .enc file by scanning for the one ending with the vault file id
            val encFile = vaultDir.listFiles()?.firstOrNull { f ->
                f.name.endsWith("${vaultFile.id}.enc")
            }
            if (encFile == null || !encFile.exists()) {
                Log.e(TAG, "Encrypted file not found for id: ${vaultFile.id}")
                return@withContext null
            }

            val tempDir = File(context.cacheDir, "vault_temp").also { it.mkdirs() }
            val destFile = File(tempDir, "${UUID.randomUUID()}_${vaultFile.originalName}")

            val key = VaultEncryptionHelper.getOrCreateKey()
            val success = VaultEncryptionHelper.decryptFileTo(encFile, destFile, key)

            if (success) destFile else null
        } catch (e: Exception) {
            Log.e(TAG, "decryptToTemp failed: ${e.message}", e)
            null
        }
    }

    /**
     * Delete a vault file — removes .enc from vault dir, SD copy, and index entry.
     */
    suspend fun deleteFromVault(vaultFile: VaultFile): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete .enc file from vault
            val encFile = vaultDir.listFiles()?.firstOrNull { f ->
                f.name.endsWith("${vaultFile.id}.enc")
            }
            encFile?.delete()

            // Remove from index
            val index = readIndex()
            writeIndex(VaultIndex(files = index.files.filter { it.id != vaultFile.id }))

            // Delete SD copy if exists
            try {
                sdDemoDir?.let { dir ->
                    File(dir, "${vaultFile.id}.enc").takeIf { it.exists() }?.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "SD demo delete failed (non-fatal): ${e.message}")
            }

            Log.d(TAG, "Deleted from vault: ${vaultFile.originalName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteFromVault failed: ${e.message}", e)
            false
        }
    }

    /**
     * Returns (fileCount, totalBytes) from the index. Safe to call on main thread.
     */
    fun getVaultStats(): Pair<Int, Long> {
        return try {
            val files = readIndex().files
            Pair(files.size, files.sumOf { it.sizeBytes })
        } catch (e: Exception) {
            Pair(0, 0L)
        }
    }
}
