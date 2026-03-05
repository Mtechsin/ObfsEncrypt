package com.obfs.encrypt.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Secure KeyStore wrapper for Android Keystore system.
 * 
 * Provides hardware-backed encryption for sensitive data like passwords and keyfiles.
 * Uses AES-256-GCM with a key stored in the Android Keystore, which is protected by
 * device lock screen (PIN/Pattern/Password) and optionally biometric authentication.
 * 
 * Security Features:
 * - Hardware-backed key storage (if available)
 * - Key requires user authentication (device lock)
 * - AES-256-GCM authenticated encryption
 * - Unique IV per encryption operation
 * - Automatic key generation on first use
 * 
 * Usage:
 * ```kotlin
 * val keyStore = SecureKeyStore()
 * keyStore.initialize()
 * 
 * // Encrypt sensitive data
 * val encrypted = keyStore.encrypt("my-secret-password")
 * 
 * // Decrypt sensitive data
 * val decrypted = keyStore.decrypt(encrypted)
 * ```
 */
class SecureKeyStore {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID).apply {
            load(null)
        }
    }

    private var masterKey: SecretKey? = null

    /**
     * Initialize the KeyStore and generate/load the master key.
     * Call this once at app startup.
     */
    fun initialize() {
        loadOrCreateKey()
    }

    /**
     * Check if the KeyStore is initialized and ready.
     */
    fun isInitialized(): Boolean = masterKey != null

    /**
     * Encrypt plaintext using the stored master key.
     * 
     * @param plaintext The sensitive data to encrypt (e.g., password)
     * @return EncryptedData containing IV and ciphertext, or null if encryption fails
     */
    fun encrypt(plaintext: String): EncryptedData? {
        try {
            val key = masterKey ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            return EncryptedData(
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Decrypt ciphertext using the stored master key.
     * 
     * @param encryptedData The encrypted data containing IV and ciphertext
     * @return Decrypted plaintext, or null if decryption fails
     */
    fun decrypt(encryptedData: EncryptedData): String? {
        try {
            val key = masterKey ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encryptedData.iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)
            val plaintext = cipher.doFinal(ciphertext)

            return String(plaintext, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            // Tampered or corrupted data
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Encrypt a CharArray (password) directly.
     * More secure than converting to String first.
     */
    fun encryptPassword(password: CharArray): EncryptedData? {
        try {
            val key = masterKey ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val plaintextBytes = password.concatToString().toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Clear password from memory
            password.fill('0')

            return EncryptedData(
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Delete the stored key. Use this when user wants to clear all sensitive data.
     */
    fun deleteKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
            masterKey = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if a key exists in the KeyStore.
     */
    fun keyExists(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load existing key or generate a new one.
     */
    private fun loadOrCreateKey() {
        masterKey = if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        } else {
            generateKey()
        }?.let { entry ->
            entry as? SecretKey
        } ?: generateKey()
    }

    /**
     * Generate a new AES-256 key in the Android Keystore.
     */
    private fun generateKey(): SecretKey? {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER_ANDROID
            )

            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Require user authentication (device lock screen)
                .setUserAuthenticationRequired(true)
                // Allow key to be used even if user hasn't authenticated recently
                // (protected by device lock screen)
                .setUserAuthenticationValidityDurationSeconds(-1)
                // Prevent key extraction even with root access
                .setIsStrongBoxBacked(true)
                .build()

            keyGenerator.init(keyGenSpec)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // StrongBox not available, fall back to TEE-backed key
            try {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER_ANDROID
                )

                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(-1)
                    .build()

                keyGenerator.init(keyGenSpec)
                return keyGenerator.generateKey()
            } catch (e2: Exception) {
                e2.printStackTrace()
                return null
            }
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore"
        private const val KEY_ALIAS = "obfs_encrypt_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

/**
 * Container for encrypted data.
 * 
 * @param iv Initialization vector (Base64 encoded)
 * @param ciphertext Encrypted data (Base64 encoded)
 */
data class EncryptedData(
    val iv: String,
    val ciphertext: String
)
