package com.obfs.encrypt.crypto

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EncryptionHelper.
 * 
 * These tests verify the correctness of encryption/decryption operations,
 * header HMAC verification, and integrity checks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptionHelperTest {

    private lateinit var encryptionHelper: EncryptionHelper

    @Before
    fun setup() {
        encryptionHelper = EncryptionHelper()
    }

    @Test
    fun testEncryptDecrypt_RoundTrip_Success() = runTest {
        // Given
        val originalData = "Hello, World! This is a test of encryption.".toByteArray()
        val password = "testPassword123".toCharArray()
        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(originalData)

        // When - Encrypt
        encryptionHelper.encrypt(
            inputStream = inputStream,
            outputStream = outputStream,
            password = password,
            method = EncryptionMethod.STANDARD,
            progressCallback = { _, _, _ -> },
            totalSize = originalData.size.toLong(),
            enableIntegrityCheck = false
        )

        // Then - Decrypt
        val decryptedStream = ByteArrayOutputStream()
        val encryptedInputStream = ByteArrayInputStream(outputStream.toByteArray())
        
        encryptionHelper.decrypt(
            inputStream = encryptedInputStream,
            outputStream = decryptedStream,
            password = password,
            progressCallback = { _, _, _ -> },
            totalSize = outputStream.size().toLong()
        )

        // Verify
        assertThat(decryptedStream.toByteArray()).isEqualTo(originalData)
    }

    @Test
    fun testEncryptDecrypt_WithIntegrityCheck_Success() = runTest {
        // Given
        val originalData = "Sensitive data requiring integrity verification".toByteArray()
        val password = "securePassword456".toCharArray()
        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(originalData)

        // When - Encrypt with integrity check
        encryptionHelper.encrypt(
            inputStream = inputStream,
            outputStream = outputStream,
            password = password,
            method = EncryptionMethod.STANDARD,
            progressCallback = { _, _, _ -> },
            totalSize = originalData.size.toLong(),
            enableIntegrityCheck = true
        )

        // Then - Decrypt with integrity verification
        val decryptedStream = ByteArrayOutputStream()
        val encryptedInputStream = ByteArrayInputStream(outputStream.toByteArray())
        
        val result = encryptionHelper.decrypt(
            inputStream = encryptedInputStream,
            outputStream = decryptedStream,
            password = password,
            progressCallback = { _, _, _ -> },
            totalSize = outputStream.size().toLong(),
            verifyIntegrity = true
        )

        // Verify
        assertThat(decryptedStream.toByteArray()).isEqualTo(originalData)
        assertThat(result.success).isTrue()
    }

    @Test
    fun testDecrypt_WrongPassword_ThrowsException() = runTest {
        // Given
        val originalData = "Test data".toByteArray()
        val correctPassword = "correctPassword".toCharArray()
        val wrongPassword = "wrongPassword".toCharArray()
        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(originalData)

        // Encrypt with correct password
        encryptionHelper.encrypt(
            inputStream = inputStream,
            outputStream = outputStream,
            password = correctPassword,
            progressCallback = { _, _, _ -> },
            totalSize = originalData.size.toLong()
        )

        // When & Then - Try to decrypt with wrong password
        val decryptedStream = ByteArrayOutputStream()
        val encryptedInputStream = ByteArrayInputStream(outputStream.toByteArray())
        
        assertThrows<javax.crypto.AEADBadTagException> {
            encryptionHelper.decrypt(
                inputStream = encryptedInputStream,
                outputStream = decryptedStream,
                password = wrongPassword,
                progressCallback = { _, _, _ -> },
                totalSize = outputStream.size().toLong()
            )
        }
    }

    @Test
    fun testDecrypt_CorruptedData_ThrowsException() = runTest {
        // Given
        val originalData = "Test data".toByteArray()
        val password = "testPassword".toCharArray()
        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(originalData)

        // Encrypt
        encryptionHelper.encrypt(
            inputStream = inputStream,
            outputStream = outputStream,
            password = password,
            progressCallback = { _, _, _ -> },
            totalSize = originalData.size.toLong()
        )

        // Corrupt the encrypted data
        val corruptedData = outputStream.toByteArray().apply {
            this[20] = (this[20].toInt() xor 0xFF).toByte() // Flip bits in middle
        }

        // When & Then - Try to decrypt corrupted data
        val decryptedStream = ByteArrayOutputStream()
        val encryptedInputStream = ByteArrayInputStream(corruptedData)
        
        assertThrows<Exception> {
            encryptionHelper.decrypt(
                inputStream = encryptedInputStream,
                outputStream = decryptedStream,
                password = password,
                progressCallback = { _, _, _ -> },
                totalSize = corruptedData.size.toLong()
            )
        }
    }

    @Test
    fun testEncryptDecrypt_LargeData_Success() = runTest {
        // Given - 5MB of data
        val originalData = ByteArray(5 * 1024 * 1024) { it.toByte() }
        val password = "largeDataPassword".toCharArray()
        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(originalData)

        // When - Encrypt
        encryptionHelper.encrypt(
            inputStream = inputStream,
            outputStream = outputStream,
            password = password,
            method = EncryptionMethod.STANDARD,
            progressCallback = { _, _, _ -> },
            totalSize = originalData.size.toLong()
        )

        // Then - Decrypt
        val decryptedStream = ByteArrayOutputStream()
        val encryptedInputStream = ByteArrayInputStream(outputStream.toByteArray())
        
        encryptionHelper.decrypt(
            inputStream = encryptedInputStream,
            outputStream = decryptedStream,
            password = password,
            progressCallback = { _, _, _ -> },
            totalSize = outputStream.size().toLong()
        )

        // Verify
        assertThat(decryptedStream.toByteArray()).isEqualTo(originalData)
    }

    @Test
    fun testKeyDerivation_ConsistentOutput() = runTest {
        // Given
        val password = "consistentPassword".toCharArray()
        val salt = ByteArray(16) { 1 } // Fixed salt for reproducibility

        // When - Derive key twice
        val key1 = encryptionHelper.deriveKey(password, salt, EncryptionMethod.STANDARD)
        val key2 = encryptionHelper.deriveKey(password, salt, EncryptionMethod.STANDARD)

        // Then - Keys should be identical
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun testKeyDerivation_DifferentSalts_DifferentOutput() = runTest {
        // Given
        val password = "passwordWithDifferentSalts".toCharArray()
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }

        // When - Derive keys with different salts
        val key1 = encryptionHelper.deriveKey(password, salt1, EncryptionMethod.STANDARD)
        val key2 = encryptionHelper.deriveKey(password, salt2, EncryptionMethod.STANDARD)

        // Then - Keys should be different
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun testChunkNonce_UniquePerChunk() {
        // Given
        val baseNonce = ByteArray(12) { 0 }
        
        // When - Generate nonces for different chunks
        val nonce0 = encryptionHelper.chunkNonce(baseNonce, 0)
        val nonce1 = encryptionHelper.chunkNonce(baseNonce, 1)
        val nonce2 = encryptionHelper.chunkNonce(baseNonce, 2)

        // Then - All nonces should be unique
        assertThat(nonce0).isNotEqualTo(nonce1)
        assertThat(nonce1).isNotEqualTo(nonce2)
        assertThat(nonce0).isNotEqualTo(nonce2)
    }

    @Test
    fun testChecksum_Compute_Success() = runTest {
        // Given
        val data = "Test data for checksum".toByteArray()

        // When - Compute checksum
        val checksum = encryptionHelper.computeChecksum(data)

        // Then - Checksum should be 32 bytes (SHA-256)
        assertThat(checksum.size).isEqualTo(32)
        
        // Same data should produce same checksum
        val checksum2 = encryptionHelper.computeChecksum(data)
        assertThat(checksum).isEqualTo(checksum2)
        
        // Different data should produce different checksum
        val differentData = "Different data".toByteArray()
        val checksum3 = encryptionHelper.computeChecksum(differentData)
        assertThat(checksum).isNotEqualTo(checksum3)
    }

    @Test
    fun testHmac_ComputeAndVerify_Success() = runTest {
        // Given
        val data = "Data to authenticate".toByteArray()
        val key = ByteArray(32) { 1 }

        // When - Compute HMAC
        val hmac = encryptionHelper.computeHmac(data, key)

        // Then - HMAC should be 32 bytes
        assertThat(hmac.size).isEqualTo(32)

        // Verify should succeed with correct key
        val isValid = encryptionHelper.verifyHmac(data, key, hmac)
        assertThat(isValid).isTrue()

        // Verify should fail with wrong key
        val wrongKey = ByteArray(32) { 2 }
        val isInvalid = encryptionHelper.verifyHmac(data, wrongKey, hmac)
        assertThat(isInvalid).isFalse()
    }

    // Helper function for asserting exceptions
    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName} but no exception was thrown")
        } catch (e: Throwable) {
            if (e !is T) {
                throw AssertionError("Expected ${T::class.java.simpleName} but got ${e::class.java.simpleName}", e)
            }
        }
    }
}
