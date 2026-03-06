package com.obfs.encrypt.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPasswordManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isPasswordSet(): Boolean {
        return securePrefs.contains(KEY_PASSWORD_HASH)
    }

    fun isPasswordEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_PASSWORD_ENABLED, false)
    }

    fun setPasswordEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_PASSWORD_ENABLED, enabled).apply()
    }

    fun setPassword(password: String): Boolean {
        return try {
            val salt = generateSalt()
            val hash = hashPassword(password, salt)

            securePrefs.edit()
                .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, hash)
                .putBoolean(KEY_PASSWORD_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun verifyPassword(password: String): Boolean {
        return try {
            val saltString = securePrefs.getString(KEY_PASSWORD_SALT, null) ?: return false
            val storedHash = securePrefs.getString(KEY_PASSWORD_HASH, null) ?: return false
            val salt = Base64.decode(saltString, Base64.NO_WRAP)
            val inputHash = hashPassword(password, salt)
            storedHash == inputHash
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearPassword() {
        securePrefs.edit()
            .remove(KEY_PASSWORD_SALT)
            .remove(KEY_PASSWORD_HASH)
            .putBoolean(KEY_PASSWORD_ENABLED, false)
            .apply()
        clearSecurityQuestions()
    }

    fun isSecurityQuestionsEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_SECURITY_QUESTIONS_ENABLED, false)
    }

    fun setSecurityQuestion(question: String, answer: String) {
        val salt = generateSalt()
        val answerHash = hashPassword(answer.lowercase().trim(), salt)

        securePrefs.edit()
            .putString(KEY_SECURITY_QUESTION, question)
            .putString(KEY_SECURITY_QUESTION_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_SECURITY_QUESTION_ANSWER, answerHash)
            .putBoolean(KEY_SECURITY_QUESTIONS_ENABLED, true)
            .apply()
    }

    fun getSecurityQuestion(): String? {
        return securePrefs.getString(KEY_SECURITY_QUESTION, null)
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        return try {
            val saltString = securePrefs.getString(KEY_SECURITY_QUESTION_SALT, null) ?: return false
            val storedAnswerHash = securePrefs.getString(KEY_SECURITY_QUESTION_ANSWER, null) ?: return false
            val salt = Base64.decode(saltString, Base64.NO_WRAP)
            val inputAnswerHash = hashPassword(answer.lowercase().trim(), salt)
            storedAnswerHash == inputAnswerHash
        } catch (e: Exception) {
            false
        }
    }

    fun clearSecurityQuestions() {
        securePrefs.edit()
            .remove(KEY_SECURITY_QUESTION)
            .remove(KEY_SECURITY_QUESTION_SALT)
            .remove(KEY_SECURITY_QUESTION_ANSWER)
            .putBoolean(KEY_SECURITY_QUESTIONS_ENABLED, false)
            .apply()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    companion object {
        private const val SECURE_PREFS_NAME = "secure_password_prefs"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_PASSWORD_ENABLED = "password_enabled"
        private const val KEY_SECURITY_QUESTION = "security_question"
        private const val KEY_SECURITY_QUESTION_SALT = "security_question_salt"
        private const val KEY_SECURITY_QUESTION_ANSWER = "security_question_answer"
        private const val KEY_SECURITY_QUESTIONS_ENABLED = "security_questions_enabled"

        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_LENGTH = 16
        private const val ITERATIONS = 100000
        private const val KEY_LENGTH = 256
    }
}
