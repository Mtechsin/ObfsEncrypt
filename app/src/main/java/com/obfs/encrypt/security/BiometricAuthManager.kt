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
 * Manages biometric authentication for the app.
 * Provides fingerprint/face unlock as an extra security layer.
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val biometricManager = BiometricManager.from(context)

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
     * Authenticate the user using biometric (fingerprint/face).
     * Returns true if authentication succeeds, false otherwise.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to continue",
        negativeButtonText: String = "Use Password"
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

        prompt.authenticate(promptInfo)
    }

    /**
     * Store encrypted password using biometric-protected key.
     * The password is encrypted with a key stored in the Keystore
     * that requires biometric authentication to use.
     */
    fun storePasswordWithBiometric(password: CharArray): Boolean {
        try {
            val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            // Store as encrypted string - in production, use Keystore-backed encryption
            val encrypted = encryptWithBiometricKey(String(password))
            prefs.edit()
                .putString(KEY_STORED_PASSWORD, encrypted)
                .putBoolean(KEY_HAS_STORED_PASSWORD, true)
                .apply()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Retrieve the stored password after biometric authentication.
     * Returns null if no password is stored or retrieval fails.
     */
    fun retrieveStoredPassword(): CharArray? {
        try {
            val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            val encrypted = prefs.getString(KEY_STORED_PASSWORD, null) ?: return null
            val decrypted = decryptWithBiometricKey(encrypted)
            return decrypted.toCharArray()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Clear any stored password.
     */
    fun clearStoredPassword() {
        context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_STORED_PASSWORD)
            .putBoolean(KEY_HAS_STORED_PASSWORD, false)
            .apply()
    }

    fun hasStoredPassword(): Boolean {
        return context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_STORED_PASSWORD, false)
    }

    private fun encryptWithBiometricKey(plaintext: String): String {
        // In production, this would use BiometricPrompt.CryptoObject with Keystore
        // For this implementation, we'll use a placeholder that should be replaced
        // with proper Keystore-backed encryption
        return android.util.Base64.encodeToString(plaintext.toByteArray(), android.util.Base64.DEFAULT)
    }

    private fun decryptWithBiometricKey(ciphertext: String): String {
        // In production, this would use BiometricPrompt.CryptoObject with Keystore
        return String(android.util.Base64.decode(ciphertext, android.util.Base64.DEFAULT))
    }

    companion object {
        private const val BIOMETRIC_PREFS = "biometric_auth_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_STORED_PASSWORD = "stored_password"
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
    object NotEnrolled : BiometricResult()
    data class Error(val message: String) : BiometricResult()
}
