package com.obfs.encrypt.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manages biometric authentication for the app with secure Keystore integration.
 * Provides fingerprint/face unlock as an extra security layer for accessing stored credentials.
 * 
 * Security Features:
 * - Biometric authentication required to access stored passwords
 * - All sensitive data encrypted with Android Keystore-backed keys
 * - Hardware-backed security when available (StrongBox)
 * - Automatic key generation and management
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val biometricManager = BiometricManager.from(context)
    private val secureKeyStore = SecureKeyStore()

    init {
        secureKeyStore.initialize()
    }

    /**
     * Check if biometric authentication is available on this device.
     */
    fun canAuthenticate(): BiometricStatus {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Check if biometric authentication is available and ready to use.
     */
    fun isBiometricAvailable(): Boolean {
        return canAuthenticate() == BiometricStatus.AVAILABLE && isKeystoreReady()
    }

    /**
     * Check if biometric authentication is enabled in app settings.
     */
    fun isBiometricEnabled(): Boolean {
        val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Enable or disable biometric authentication.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    /**
     * Check if Keystore is properly initialized and ready.
     */
    fun isKeystoreReady(): Boolean = secureKeyStore.isInitialized()

    /**
     * Authenticate the user using biometric (fingerprint/face).
     * Returns true if authentication succeeds, false otherwise.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to continue",
        negativeButtonText: String = "Use Password",
        cryptoObject: BiometricPrompt.CryptoObject? = null
    ): BiometricResult = suspendCancellableCoroutine { continuation ->

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                continuation.resume(BiometricResult.Success)
            }

            override fun onAuthenticationFailed() {
                // Don't resume on failure - wait for error
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        continuation.resume(BiometricResult.Cancelled)
                    }
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                        continuation.resume(BiometricResult.NotEnrolled)
                    }
                    else -> {
                        continuation.resume(BiometricResult.Error(errString.toString()))
                    }
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        // If cryptoObject is provided, use it for authenticated encryption/decryption
        cryptoObject?.let {
            prompt.authenticate(promptInfo, it)
        } ?: prompt.authenticate(promptInfo)
    }

    /**
     * Store encrypted password using biometric-protected key.
     * The password is encrypted with a key stored in the Android Keystore
     * that requires device lock screen authentication to use.
     * 
     * @param password The password to store (CharArray for security)
     * @return true if storage succeeded, false otherwise
     */
    fun storePasswordWithBiometric(password: CharArray): Boolean {
        try {
            if (!secureKeyStore.isInitialized()) {
                secureKeyStore.initialize()
            }

            val encrypted = secureKeyStore.encryptPassword(password)
                ?: return false

            val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_STORED_PASSWORD_IV, encrypted.iv)
                .putString(KEY_STORED_PASSWORD_CIPHERTEXT, encrypted.ciphertext)
                .putBoolean(KEY_HAS_STORED_PASSWORD, true)
                .apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Retrieve the stored password after biometric authentication.
     * The Keystore will only release the decryption key after successful
     * device authentication (biometric or lock screen).
     * 
     * @return Decrypted password as CharArray, or null if retrieval fails
     */
    fun retrieveStoredPassword(): CharArray? {
        try {
            if (!secureKeyStore.isInitialized()) {
                secureKeyStore.initialize()
            }

            val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            val iv = prefs.getString(KEY_STORED_PASSWORD_IV, null) ?: return null
            val ciphertext = prefs.getString(KEY_STORED_PASSWORD_CIPHERTEXT, null) ?: return null

            val encryptedData = EncryptedData(iv = iv, ciphertext = ciphertext)
            val decrypted = secureKeyStore.decrypt(encryptedData)
            
            return decrypted?.toCharArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Clear any stored password and delete the Keystore key.
     */
    fun clearStoredPassword() {
        context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_STORED_PASSWORD_IV)
            .remove(KEY_STORED_PASSWORD_CIPHERTEXT)
            .putBoolean(KEY_HAS_STORED_PASSWORD, false)
            .apply()
        
        secureKeyStore.deleteKey()
    }

    fun hasStoredPassword(): Boolean {
        return context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_STORED_PASSWORD, false)
    }

    companion object {
        private const val BIOMETRIC_PREFS = "biometric_auth_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_STORED_PASSWORD_IV = "stored_password_iv"
        private const val KEY_STORED_PASSWORD_CIPHERTEXT = "stored_password_ciphertext"
        private const val KEY_HAS_STORED_PASSWORD = "has_stored_password"
    }
}

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NOT_ENROLLED,
    UNKNOWN
}

sealed class BiometricResult {
    object Success : BiometricResult()
    object Cancelled : BiometricResult()
    object UserCancelled : BiometricResult()
    object LockedOut : BiometricResult()
    object NotEnrolled : BiometricResult()
    data class Error(val message: String) : BiometricResult()
}
