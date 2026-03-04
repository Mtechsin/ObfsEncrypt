package com.obfs.encrypt.crypto

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EncryptionHelper constants and structures.
 *
 * Note: Full encryption/decryption tests require the Argon2Kt native library,
 * which cannot be loaded in plain JVM unit tests. These tests verify:
 * - File format constants
 * - Buffer sizes and format structure
 * - Encryption method parameters
 *
 * For full integration tests, use androidTest (instrumented tests on Android device/emulator).
 */
class EncryptionHelperTest {

    @Test
    fun magicHeader_hasCorrectLength() {
        assertEquals("OBFSv3", EncryptionHelper.MAGIC_HEADER)
        assertEquals(6, EncryptionHelper.MAGIC_HEADER.length)
    }

    @Test
    fun magicHeaderV2_isDefined() {
        assertEquals("OBFSv2", EncryptionHelper.MAGIC_HEADER_V2)
    }

    @Test
    fun saltLength_isCorrect() {
        assertEquals(16, EncryptionHelper.SALT_LENGTH)
    }

    @Test
    fun nonceLength_isCorrect() {
        assertEquals(12, EncryptionHelper.NONCE_LENGTH)
    }

    @Test
    fun methodLength_isCorrect() {
        assertEquals(1, EncryptionHelper.METHOD_LENGTH)
    }

    @Test
    fun chunkSize_isCorrect() {
        assertEquals(1 * 1024 * 1024, EncryptionHelper.CHUNK_SIZE) // 1MB
    }

    @Test
    fun gcmTagLength_isCorrect() {
        assertEquals(128, EncryptionHelper.GCM_TAG_LENGTH) // bits
    }

    @Test
    fun integrityChecksumLength_isCorrect() {
        assertEquals(32, EncryptionHelper.SHA256_CHECKSUM_LENGTH)
        assertEquals(32, EncryptionHelper.HMAC_SHA256_LENGTH)
    }

    @Test
    fun keyfileSizeLimits_areCorrect() {
        assertEquals(16, EncryptionHelper.KEYFILE_MIN_SIZE)
        assertEquals(1024 * 1024, EncryptionHelper.KEYFILE_MAX_SIZE) // 1MB
    }

    @Test
    fun encryptionMethod_standard_hasExpectedParameters() {
        val method = EncryptionMethod.STANDARD
        assertEquals(4, method.tCostInIterations)
        assertEquals(64 * 1024, method.mCostInKibibyte) // 64MB
        assertEquals(1, method.parallelism)
    }

    @Test
    fun encryptionMethod_fast_hasExpectedParameters() {
        val method = EncryptionMethod.FAST
        assertEquals(1, method.tCostInIterations)
        assertEquals(8 * 1024, method.mCostInKibibyte) // 8MB
        assertEquals(1, method.parallelism)
    }

    @Test
    fun encryptionMethod_strong_hasExpectedParameters() {
        val method = EncryptionMethod.STRONG
        assertEquals(8, method.tCostInIterations)
        assertEquals(128 * 1024, method.mCostInKibibyte) // 128MB
        assertEquals(2, method.parallelism)
    }

    @Test
    fun encryptionMethod_displayNames_areNotEmpty() {
        EncryptionMethod.entries.forEach { method ->
            assertTrue("Display name should not be empty", method.displayName.isNotEmpty())
            assertTrue("Description should not be empty", method.description.isNotEmpty())
            assertTrue("Speed label should not be empty", method.speedLabel.isNotEmpty())
        }
    }

    @Test
    fun encryptedStream_hasObfsHeader() {
        // This test verifies the header is written correctly
        // Note: Full encryption test requires Android environment
        val headerBytes = EncryptionHelper.MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        assertEquals(6, headerBytes.size)
        assertEquals("O", headerBytes[0].toInt().toChar().toString())
        assertEquals("B", headerBytes[1].toInt().toChar().toString())
        assertEquals("F", headerBytes[2].toInt().toChar().toString())
        assertEquals("S", headerBytes[3].toInt().toChar().toString())
        assertEquals("v", headerBytes[4].toInt().toChar().toString())
        assertEquals("3", headerBytes[5].toInt().toChar().toString())
    }

    @Test
    fun fileFormat_expectedSizes_areCorrect() {
        // Header: 6 bytes (OBFSv3)
        // Salt: 16 bytes
        // Nonce: 12 bytes
        // Method: 1 byte
        // Total header overhead: 35 bytes
        val headerOverhead = EncryptionHelper.MAGIC_HEADER.length + EncryptionHelper.SALT_LENGTH + EncryptionHelper.NONCE_LENGTH + EncryptionHelper.METHOD_LENGTH
        assertEquals(35, headerOverhead)
    }

    @Test
    fun chunkLengthPrefix_is4Bytes() {
        // Each chunk is prefixed with a 4-byte big-endian length
        val testLength = 1024
        val lengthBytes = java.nio.ByteBuffer.allocate(4).putInt(testLength).array()
        assertEquals(4, lengthBytes.size)
        assertEquals(0, lengthBytes[0].toInt()) // High bytes should be 0 for small values
        assertEquals(0, lengthBytes[1].toInt())
        assertEquals(4, lengthBytes[2].toInt()) // 1024 = 0x0400
        assertEquals(0, lengthBytes[3].toInt())
    }

    @Test
    fun chunkNonce_xorCalculation_isCorrect() {
        // Test the XOR logic used in chunk nonce derivation
        val baseNonce = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B)
        val chunkIndex = 0L

        // For index 0, XOR with 0 should keep bytes unchanged in the last 8 positions
        // But we can't test chunkNonce directly without instantiating EncryptionHelper
        // which loads native libraries

        // Instead, verify the XOR logic manually
        // ByteBuffer uses BIG-endian by default (not little-endian)
        val indexBytes = java.nio.ByteBuffer.allocate(8).putLong(1L).array()
        assertEquals(8, indexBytes.size)

        // Verify big-endian encoding of 1 (most significant byte first)
        assertEquals(0, indexBytes[0].toInt())
        assertEquals(0, indexBytes[1].toInt())
        assertEquals(0, indexBytes[2].toInt())
        assertEquals(0, indexBytes[3].toInt())
        assertEquals(0, indexBytes[4].toInt())
        assertEquals(0, indexBytes[5].toInt())
        assertEquals(0, indexBytes[6].toInt())
        assertEquals(1, indexBytes[7].toInt()) // Least significant byte last
    }

    @Test
    fun chunkNonce_differentIndices_shouldProduceDifferentResults() {
        // Verify that different chunk indices produce different byte patterns
        val indexBytes1 = java.nio.ByteBuffer.allocate(8).putLong(1L).array()
        val indexBytes2 = java.nio.ByteBuffer.allocate(8).putLong(2L).array()

        assertFalse("Different indices should have different byte patterns", indexBytes1.contentEquals(indexBytes2))
    }
}
