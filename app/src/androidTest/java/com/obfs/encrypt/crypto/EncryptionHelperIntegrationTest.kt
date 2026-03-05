package com.obfs.encrypt.crypto

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Instrumented integration tests for EncryptionHelper.
 * 
 * These tests run on an Android device/emulator and test:
 * - Full encryption/decryption cycles
 * - File format correctness
 * - Integrity verification
 * - Keyfile support
 * 
 * Note: These tests create temporary files and should clean up after themselves.
 */
@RunWith(AndroidJUnit4::class)
class EncryptionHelperIntegrationTest {

    private lateinit var encryptionHelper: EncryptionHelper
    private lateinit var testDir: File

    @Before
    fun setup() {
        encryptionHelper = EncryptionHelper()
        
        // Create temporary test directory
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        testDir = File(context.cacheDir, "encryption_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        // Clean up test files
        testDir.listFiles()?.forEach { it.delete() }
        testDir.delete()
    }

    // region: Basic Encryption/Decryption Tests

    @Test
    fun encryptAndDecrypt_smallFile_success() = runTest {
        // Create test file
        val testContent = "Hello, World! This is a test file for encryption."
        val inputFile = File(testDir, "test_input.txt").apply {
            writeText(testContent)
        }

        val encryptedFile = File(testDir, "test_output.obfs")
        val decryptedFile = File(testDir, "test_decrypted.txt")

        val password = charArrayOf('t', 'e', 's', 't', '1', '2', '3')

        // Encrypt
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = null,
                    enableIntegrityCheck = false
                )
            }
        }

        // Verify encrypted file exists and is larger than original (due to header/overhead)
        assertTrue("Encrypted file should exist", encryptedFile.exists())
        assertTrue("Encrypted file should be larger than original", encryptedFile.length() > inputFile.length())

        // Verify magic header
        val header = encryptedFile.readBytes().take(6).toByteArray()
        assertEquals("OBFSv3", String(header, Charsets.UTF_8))

        // Decrypt
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = null,
                    verifyIntegrity = false
                )
                assertTrue("Decryption should succeed", result.success)
            }
        }

        // Verify decrypted content matches original
        val decryptedContent = decryptedFile.readText()
        assertEquals("Decrypted content should match original", testContent, decryptedContent)
    }

    @Test
    fun encryptAndDecrypt_withIntegrityCheck_success() = runTest {
        val testContent = "This file has integrity verification enabled."
        val inputFile = File(testDir, "test_integrity_input.txt").apply {
            writeText(testContent)
        }

        val encryptedFile = File(testDir, "test_integrity_output.obfs")
        val decryptedFile = File(testDir, "test_integrity_decrypted.txt")

        val password = charArrayOf('s', 'e', 'c', 'u', 'r', 'e')

        // Encrypt with integrity check
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = null,
                    enableIntegrityCheck = true
                )
            }
        }

        // Decrypt with integrity verification
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = null,
                    verifyIntegrity = true
                )

                assertTrue("Decryption should succeed", result.success)
                assertNotNull("Integrity result should be present", result.integrityResult)
                assertTrue("Integrity should be valid", result.integrityResult?.isValid == true)
            }
        }

        // Verify content
        val decryptedContent = decryptedFile.readText()
        assertEquals(testContent, decryptedContent)
    }

    // endregion

    // region: Encryption Method Tests

    @Test
    fun encryptWithFastMethod_success() = runTest {
        val testContent = "Fast encryption test"
        val inputFile = File(testDir, "test_fast_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_fast_output.obfs")

        val password = charArrayOf('f', 'a', 's', 't')

        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.FAST,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())
        
        // Verify method byte in header
        val bytes = encryptedFile.readBytes()
        val methodByte = bytes[6 + 16 + 12] // After magic, salt, nonce
        assertEquals("Method should be FAST (0)", 0, methodByte.toInt())
    }

    @Test
    fun encryptWithStrongMethod_success() = runTest {
        val testContent = "Strong encryption test"
        val inputFile = File(testDir, "test_strong_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_strong_output.obfs")

        val password = charArrayOf('s', 't', 'r', 'o', 'n', 'g')

        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STRONG,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())
        
        // Verify method byte in header
        val bytes = encryptedFile.readBytes()
        val methodByte = bytes[6 + 16 + 12]
        assertEquals("Method should be STRONG (2)", 2, methodByte.toInt())
    }

    // endregion

    // region: Keyfile Tests

    @Test
    fun encryptAndDecrypt_withKeyfile_success() = runTest {
        val testContent = "Keyfile encryption test"
        val inputFile = File(testDir, "test_keyfile_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_keyfile_output.obfs")
        val decryptedFile = File(testDir, "test_keyfile_decrypted.txt")

        val password = charArrayOf('p', 'a', 's', 's')
        val keyfileBytes = encryptionHelper.generateKeyfile(256)

        // Encrypt with keyfile
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = keyfileBytes,
                    enableIntegrityCheck = false
                )
            }
        }

        // Decrypt with keyfile
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = keyfileBytes,
                    verifyIntegrity = false
                )
                assertTrue("Decryption with keyfile should succeed", result.success)
            }
        }

        val decryptedContent = decryptedFile.readText()
        assertEquals(testContent, decryptedContent)
    }

    @Test
    fun decrypt_withWrongKeyfile_fails() = runTest {
        val testContent = "Wrong keyfile test"
        val inputFile = File(testDir, "test_wrong_keyfile_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_wrong_keyfile_output.obfs")
        val decryptedFile = File(testDir, "test_wrong_keyfile_decrypted.txt")

        val password = charArrayOf('p', 'a', 's', 's')
        val correctKeyfile = encryptionHelper.generateKeyfile(256)
        val wrongKeyfile = encryptionHelper.generateKeyfile(256)

        // Encrypt with correct keyfile
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = correctKeyfile,
                    enableIntegrityCheck = false
                )
            }
        }

        // Decrypt with wrong keyfile - should fail
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length(),
                        keyfileBytes = wrongKeyfile,
                        verifyIntegrity = false
                    )
                    // Should not reach here
                    assertTrue("Decryption with wrong keyfile should fail", false)
                } catch (e: Exception) {
                    // Expected - decryption should fail
                    assertTrue("Should throw security exception", e is SecurityException || e.message?.contains("corrupted") == true)
                }
            }
        }
    }

    // endregion

    // region: Error Handling Tests

    @Test
    fun decrypt_withWrongPassword_throwsException() = runTest {
        val testContent = "Wrong password test"
        val inputFile = File(testDir, "test_wrong_pass_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_wrong_pass_output.obfs")
        val decryptedFile = File(testDir, "test_wrong_pass_decrypted.txt")

        val correctPassword = charArrayOf('c', 'o', 'r', 'r', 'e', 'c', 't')
        val wrongPassword = charArrayOf('w', 'r', 'o', 'n', 'g')

        // Encrypt with correct password
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = correctPassword,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        // Decrypt with wrong password - should fail
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = wrongPassword,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length()
                    )
                    assertTrue("Decryption with wrong password should fail", false)
                } catch (e: Exception) {
                    assertTrue("Should throw security exception", e is SecurityException || e.message?.contains("password") == true)
                }
            }
        }
    }

    @Test
    fun decrypt_invalidFileFormat_throwsException() = runTest {
        val invalidFile = File(testDir, "invalid.obfs").apply {
            writeText("This is not a valid encrypted file")
        }
        val decryptedFile = File(testDir, "invalid_decrypted.txt")

        val password = charArrayOf('t', 'e', 's', 't')

        FileInputStream(invalidFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = invalidFile.length()
                    )
                    assertTrue("Should throw exception for invalid format", false)
                } catch (e: Exception) {
                    assertTrue("Should throw IllegalArgumentException", e is IllegalArgumentException)
                }
            }
        }
    }

    // endregion

    // region: Large File Tests

    @Test
    fun encryptAndDecrypt_largeFile_success() = runTest {
        // Create a 5MB test file
        val inputFile = File(testDir, "test_large_input.bin")
        val fileSize = 5 * 1024 * 1024 // 5MB
        
        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(1024) { i -> (i % 256).toByte() }
            var written = 0
            while (written < fileSize) {
                val toWrite = minOf(buffer.size, fileSize - written)
                output.write(buffer, 0, toWrite)
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_large_output.obfs")
        val decryptedFile = File(testDir, "test_large_decrypted.bin")

        val password = charArrayOf('l', 'a', 'r', 'g', 'e')

        // Encrypt
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())

        // Decrypt
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length()
                )
                assertTrue("Decryption should succeed", result.success)
            }
        }

        // Verify file sizes match
        assertEquals("File sizes should match", inputFile.length(), decryptedFile.length())

        // Verify content (sample check for performance)
        val originalBytes = inputFile.readBytes()
        val decryptedBytes = decryptedFile.readBytes()
        assertTrue("Content should match", originalBytes.contentEquals(decryptedBytes))
    }

    // endregion
}
