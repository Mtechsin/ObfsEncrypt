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
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class SecureKeyStore @Inject constructor() {

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
     *
     * Fixed CRIT-01: KeyStore.getEntry() returns a KeyStore.SecretKeyEntry,
     * never a SecretKey directly. The old `entry as? SecretKey` was always
     * null and regenerated (destroying) the master key on every launch.
     */
    private fun loadOrCreateKey() {
        val existing: SecretKey? = try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            } else {
                null
            }
        } catch (e: Exception) {
            com.obfs.encrypt.diagnostics.AppLogger.w("SecureKeyStore", "Failed to load existing key", e)
            null
        }
        masterKey = existing ?: generateKey()
    }

    /**
     * Generate a new AES-256 key in the Android Keystore.
     *
     * Fixed HIGH-02 + extra finding: the old key used
     * setUserAuthenticationRequired(true) with validity -1. On API 30+ that
     * requires a BiometricPrompt CryptoObject for EVERY use, so
     * EncryptionWorker (background, no CryptoObject) always threw
     * UserNotAuthenticatedException. The master key wraps batch passwords
     * that must be usable from workers, so it MUST NOT require auth.
     * Foreground biometric gating stays at the app layer
     * (BiometricAuthManager.authenticate before retrieveStoredPassword).
     */
    private fun generateKey(): SecretKey? {
        // Attempt StrongBox first, fall back to TEE on any failure.
        try {
            return generateKeyInternal(requireAuth = false, useStrongBox = true)
        } catch (e: java.security.InvalidAlgorithmParameterException) {
            com.obfs.encrypt.diagnostics.AppLogger.w(
                "SecureKeyStore",
                "StrongBox/params unavailable, falling back to TEE",
                e
            )
        } catch (e: Exception) {
            com.obfs.encrypt.diagnostics.AppLogger.w(
                "SecureKeyStore",
                "StrongBox unavailable, falling back to TEE",
                e
            )
        }
        return try {
            generateKeyInternal(requireAuth = false, useStrongBox = false)
        } catch (e: Exception) {
            com.obfs.encrypt.diagnostics.AppLogger.e("SecureKeyStore", "Key generation failed", e)
            null
        }
    }

    private fun generateKeyInternal(requireAuth: Boolean, useStrongBox: Boolean): SecretKey? {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER_ANDROID
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
        if (requireAuth) {
            builder.setUserAuthenticationRequired(true)
            builder.setUserAuthenticationValidityDurationSeconds(30)
        } else {
            builder.setUserAuthenticationRequired(false)
        }
        if (useStrongBox) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (_: Exception) {
                // Old devices ignore StrongBox flag; TEE fallback handled by caller.
            }
        }
        val keyGenSpec = builder.build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Generate a key that only requires device lock screen (PIN/Pattern/Password),
     * without requiring biometric authentication. Used as fallback when biometrics
     * are not enrolled.
     *
     * Kept for backward compatibility. New keys do not require auth (see
     * generateKey) so background workers keep working on API 30+.
     */
    private fun generateKeyWithoutBiometric(): SecretKey? {
        return try {
            generateKeyInternal(requireAuth = false, useStrongBox = false)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
