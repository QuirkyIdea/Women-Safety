package com.example.suraksha.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256/GCM encryption helper backed by Android Keystore.
 *
 * Encrypted file format: [12 bytes IV][GCM ciphertext + tag]
 * No IV-length header — IV is always 12 bytes (AES-GCM standard).
 */
object VaultEncryptionHelper {

    private const val TAG = "VaultEncryption"
    private const val KEY_ALIAS = "suraksha_vault_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /**
     * Returns the existing AES-256 key from Android Keystore, or generates a new one.
     */
    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Return existing key if present
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        // Generate new AES-256 key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Auth enforced at UI layer via BiometricPrompt, not at key level
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt [source] and write the result to [destFile].
     * Format: [12-byte IV][GCM ciphertext].
     *
     * Does NOT delete or modify [source] — always copies.
     *
     * @return true on success, false on any failure.
     */
    fun encryptFileTo(source: File, destFile: File, key: SecretKey): Boolean {
        try {
            // Generate random 12-byte IV
            val iv = ByteArray(IV_SIZE_BYTES)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val plainBytes = source.readBytes()
            val encryptedBytes = cipher.doFinal(plainBytes)

            // Write: [12 bytes IV][encrypted bytes]
            destFile.outputStream().use { out ->
                out.write(iv)
                out.write(encryptedBytes)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "encryptFileTo failed: ${e.message}", e)
            return false
        }
    }

    /**
     * Decrypt [encFile] and write the plaintext to [destFile].
     * Expects format: [12-byte IV][GCM ciphertext].
     *
     * @return true on success, false on any failure.
     */
    fun decryptFileTo(encFile: File, destFile: File, key: SecretKey): Boolean {
        try {
            val allBytes = encFile.readBytes()
            if (allBytes.size < IV_SIZE_BYTES) {
                Log.e(TAG, "Encrypted file too small: ${allBytes.size} bytes")
                return false
            }

            val iv = allBytes.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = allBytes.copyOfRange(IV_SIZE_BYTES, allBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val plainBytes = cipher.doFinal(ciphertext)
            destFile.writeBytes(plainBytes)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "decryptFileTo failed: ${e.message}", e)
            return false
        }
    }
}
