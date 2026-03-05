package com.obfs.encrypt.crypto

import android.net.Uri
import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Core Encryption Logic — Chunked AES-256-GCM with Integrity Verification
 *
 * WHY CHUNKED ENCRYPTION:
 * Android's Conscrypt AES-GCM implementation buffers the *entire* ciphertext internally
 * until doFinal() is called (to verify the authentication tag at the end). This means
 * calling cipher.update() on a 160MB file tries to hold 160MB+tag in RAM, causing OOM.
 *
 * The fix: split the plaintext into fixed-size chunks (CHUNK_SIZE = 1MB). Each chunk
 * is independently encrypted with AES-256-GCM using its own nonce (base_nonce XOR chunk_index).
 * Each encrypted chunk is prefixed by a 4-byte big-endian length so the decryptor knows
 * exactly how many bytes to read for each chunk.
 *
 * FILE FORMAT (OBFSv4):
 *   [6 bytes]  magic   = "OBFSv4"
 *   [16 bytes] argon2 salt
 *   [12 bytes] base nonce
 *   [1 byte]  encryption method ordinal (0=FAST, 1=STANDARD, 2=STRONG)
 *   [1 byte]  integrity check enabled flag
 *   [1 byte]  header HMAC enabled flag (always 1 for v4)
 *   [32 bytes] HMAC-SHA256 of header (for tamper detection)
 *   [N chunks] each chunk:
 *       [4 bytes] big-endian ciphertext length (including 16-byte GCM tag)
 *       [M bytes] AES-GCM ciphertext + tag
 *   [if integrity check enabled:]
 *       [32 bytes] SHA-256 checksum of plaintext
 *       [32 bytes] HMAC-SHA256 of checksum
 *
 * FILE FORMAT (OBFSv3):
 *   [6 bytes]  magic   = "OBFSv3"
 *   [16 bytes] argon2 salt
 *   [12 bytes] base nonce
 *   [1 byte]  encryption method ordinal (0=FAST, 1=STANDARD, 2=STRONG)
 *   [1 byte]  integrity check enabled flag
 *   [N chunks] each chunk:
 *       [4 bytes] big-endian ciphertext length (including 16-byte GCM tag)
 *       [M bytes] AES-GCM ciphertext + tag
 *   [if integrity check enabled:]
 *       [32 bytes] SHA-256 checksum of plaintext
 *       [32 bytes] HMAC-SHA256 of checksum
 *
 * LEGACY FORMAT (OBFSv2):
 *   [6 bytes]  magic   = "OBFSv2"
 *   [16 bytes] argon2 salt
 *   [12 bytes] base nonce
 *   [N chunks] each chunk (method defaults to STANDARD)
 *
 * NONCE DERIVATION:
 *   chunk_nonce = base_nonce XOR little-endian(chunk_index as 8 bytes padded to 12)
 *   This is safe because each (key, nonce) pair is unique per chunk.
 */
class EncryptionHelper {

    private val argon2Kt = Argon2Kt()

    companion object {
        const val MAGIC_HEADER = "OBFSv4"  // Updated to v4 with header HMAC
        const val MAGIC_HEADER_V3 = "OBFSv3"
        const val MAGIC_HEADER_V2 = "OBFSv2"
        const val SALT_LENGTH = 16
        const val NONCE_LENGTH = 12
        const val METHOD_LENGTH = 1
        const val GCM_TAG_LENGTH = 128 // bits
        const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB per chunk — safe for GCM buffering

        // Integrity verification constants
        const val SHA256_CHECKSUM_LENGTH = 32
        const val HMAC_SHA256_LENGTH = 32
        const val HEADER_HMAC_LENGTH = 32

        // Keyfile constants
        const val KEYFILE_MIN_SIZE = 16
        const val KEYFILE_MAX_SIZE = 1024 * 1024 // 1MB max keyfile size
    }

    suspend fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD
    ): ByteArray = withContext(Dispatchers.Default) {
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)

        val result = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passwordBytes,
            salt = salt,
            tCostInIterations = method.tCostInIterations,
            mCostInKibibyte = method.mCostInKibibyte,
            parallelism = method.parallelism,
            hashLengthInBytes = 32
        )

        Arrays.fill(passwordBytes, 0.toByte())
        return@withContext result.rawHashAsByteArray()
    }
    
    /**
     * Derive a key from a keyfile.
     * The keyfile bytes are hashed with SHA-256 to produce a 32-byte key.
     * Optionally combines with a password if provided.
     */
    suspend fun deriveKeyFromKeyfile(
        keyfileBytes: ByteArray,
        password: CharArray? = null,
        salt: ByteArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD
    ): ByteArray = withContext(Dispatchers.Default) {
        // First, hash the keyfile bytes with SHA-256
        val keyfileHash = MessageDigest.getInstance("SHA-256").run {
            update(keyfileBytes)
            digest()
        }
        
        // If password is provided, combine it with the keyfile hash
        return@withContext if (password != null) {
            // Combine password with keyfile hash and derive using Argon2
            val combinedInput = String(password).toByteArray(Charsets.UTF_8) + keyfileHash
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = combinedInput,
                salt = salt,
                tCostInIterations = method.tCostInIterations,
                mCostInKibibyte = method.mCostInKibibyte,
                parallelism = method.parallelism,
                hashLengthInBytes = 32
            )
            Arrays.fill(combinedInput, 0.toByte())
            keyfileHash.fill(0)
            result.rawHashAsByteArray()
        } else {
            // Use keyfile hash directly (already 32 bytes)
            keyfileHash
        }
    }

    /**
     * Derive a unique per-chunk nonce by XOR-ing the base nonce with the chunk index.
     * chunk_index fits in 8 bytes; the remaining 4 bytes stay zero-padded (safe for up to
     * 2^64 chunks which is astronomically large).
     *
     * Visible for testing.
     */
    internal fun chunkNonce(baseNonce: ByteArray, chunkIndex: Long): ByteArray {
        val nonce = baseNonce.copyOf()
        // XOR the last 8 bytes with the chunk index (little-endian)
        val indexBytes = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        for (i in 0 until 8) {
            nonce[NONCE_LENGTH - 8 + i] = (nonce[NONCE_LENGTH - 8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return nonce
    }
    
    /**
     * Compute SHA-256 checksum of data.
     */
    suspend fun computeChecksum(data: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        MessageDigest.getInstance("SHA-256").run {
            update(data)
            digest()
        }
    }
    
    /**
     * Compute SHA-256 checksum of an input stream.
     */
    suspend fun computeChecksum(inputStream: InputStream): ByteArray = withContext(Dispatchers.Default) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        digest.digest()
    }
    
    /**
     * Compute HMAC-SHA256 for data integrity verification.
     */
    suspend fun computeHmac(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.doFinal(data)
    }
    
    /**
     * Verify HMAC-SHA256.
     */
    suspend fun verifyHmac(data: ByteArray, key: ByteArray, expectedHmac: ByteArray): Boolean = 
        withContext(Dispatchers.Default) {
            val computedHmac = computeHmac(data, key)
            java.security.MessageDigest.isEqual(computedHmac, expectedHmac)
        }
    
    /**
     * Read a keyfile and return its contents.
     * Validates file size constraints.
     */
    suspend fun readKeyfile(file: File): ByteArray = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("Keyfile does not exist or cannot be read")
        }
        if (file.length() < KEYFILE_MIN_SIZE) {
            throw IllegalArgumentException("Keyfile too small (minimum $KEYFILE_MIN_SIZE bytes)")
        }
        if (file.length() > KEYFILE_MAX_SIZE) {
            throw IllegalArgumentException("Keyfile too large (maximum $KEYFILE_MAX_SIZE bytes)")
        }
        file.readBytes()
    }
    
    /**
     * Read a keyfile from a URI and return its contents.
     */
    suspend fun readKeyfile(contentResolver: android.content.ContentResolver, uri: Uri): ByteArray = 
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size < KEYFILE_MIN_SIZE) {
                    throw IllegalArgumentException("Keyfile too small (minimum $KEYFILE_MIN_SIZE bytes)")
                }
                if (bytes.size > KEYFILE_MAX_SIZE) {
                    throw IllegalArgumentException("Keyfile too large (maximum $KEYFILE_MAX_SIZE bytes)")
                }
                bytes
            } ?: throw IllegalArgumentException("Cannot read keyfile from URI")
        }
    
    /**
     * Generate a random keyfile.
     */
    fun generateKeyfile(size: Int = 256): ByteArray {
        require(size in KEYFILE_MIN_SIZE..KEYFILE_MAX_SIZE) {
            "Keyfile size must be between $KEYFILE_MIN_SIZE and $KEYFILE_MAX_SIZE bytes"
        }
        return ByteArray(size).apply { SecureRandom().nextBytes(this) }
    }

    suspend fun encrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        progressCallback: suspend (Long, Long, Long) -> Unit,
        totalSize: Long = 0L,
        keyfileBytes: ByteArray? = null,
        enableIntegrityCheck: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
            val baseNonce = ByteArray(NONCE_LENGTH).apply { SecureRandom().nextBytes(this) }

            // Derive key (Argon2 — slow by design, or from keyfile)
            val key = if (keyfileBytes != null) {
                deriveKeyFromKeyfile(keyfileBytes, password, salt, method)
            } else {
                deriveKey(password, salt, method)
            }
            val secretKey = SecretKeySpec(key, "AES")

            // Build header data for HMAC computation
            val headerData = ByteArrayOutputStream()
            headerData.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8)) // 6 bytes
            headerData.write(salt)                                       // 16 bytes
            headerData.write(baseNonce)                                  // 12 bytes
            headerData.write(method.ordinal)                             // 1 byte
            headerData.write(if (enableIntegrityCheck) 1 else 0)        // 1 byte
            headerData.write(1) // Header HMAC enabled flag (always 1 for v4)

            // Compute HMAC of header data
            val headerHmac = computeHmac(headerData.toByteArray(), key)

            // Write header + HMAC
            outputStream.write(headerData.toByteArray())
            outputStream.write(headerHmac) // 32 bytes

            val fileSize = if (totalSize > 0) totalSize else null
            val startTime = System.currentTimeMillis()
            var totalBytesRead = 0L
            var chunkIndex = 0L
            var lastUpdateTime = startTime

            // For integrity check, collect all plaintext to compute hash after encryption
            val integrityData = if (enableIntegrityCheck) {
                java.io.ByteArrayOutputStream()
            } else null

            progressCallback(0, fileSize ?: 1L, startTime)

            // Read and encrypt chunk by chunk
            val plainBuf = ByteArray(CHUNK_SIZE)
            var bytesRead: Int

            while (inputStream.read(plainBuf).also { bytesRead = it } != -1) {
                val nonce = chunkNonce(baseNonce, chunkIndex)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

                // doFinal on ≤1MB — safe, bounded memory
                val cipherChunk = cipher.doFinal(plainBuf, 0, bytesRead)

                // Write 4-byte big-endian length prefix then ciphertext+tag
                val lenBytes = ByteBuffer.allocate(4).putInt(cipherChunk.size).array()
                outputStream.write(lenBytes)
                outputStream.write(cipherChunk)

                // Store plaintext for integrity check
                integrityData?.write(plainBuf, 0, bytesRead)

                totalBytesRead += bytesRead
                chunkIndex++

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) {
                    val reportTotal = fileSize ?: (totalBytesRead * 1.2).toLong().coerceAtLeast(1)
                    progressCallback(totalBytesRead, reportTotal, startTime)
                    lastUpdateTime = currentTime
                }
            }

            // Write integrity check if enabled
            if (enableIntegrityCheck && integrityData != null) {
                val plaintextData = integrityData.toByteArray()
                val checksum = computeChecksum(plaintextData)
                val hmac = computeHmac(checksum, key)

                outputStream.write(checksum)
                outputStream.write(hmac)

                // Clear sensitive data
                plaintextData.fill(0)
                integrityData.reset()
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())
            headerData.close()

            val finalSize = fileSize ?: totalBytesRead
            progressCallback(finalSize, finalSize, startTime)

        } catch (e: Exception) {
            Log.e("EncryptionHelper", "Encryption Failed", e)
            throw e
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    suspend fun decrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        progressCallback: suspend (Long, Long, Long) -> Unit,
        totalSize: Long = 0L,
        keyfileBytes: ByteArray? = null,
        verifyIntegrity: Boolean = false
    ): DecryptionResult = withContext(Dispatchers.IO) {
        try {
            // Read and verify magic header
            val magicBuffer = ByteArray(MAGIC_HEADER.length)
            inputStream.read(magicBuffer)
            val magic = String(magicBuffer, Charsets.UTF_8)

            val isV4Format = magic == MAGIC_HEADER
            val isV3Format = magic == MAGIC_HEADER_V3
            val isV2Format = magic == MAGIC_HEADER_V2
            
            if (!isV4Format && !isV3Format && !isV2Format) {
                throw IllegalArgumentException("Invalid file format. Expected OBFSv3/v4 header but got: $magic")
            }

            val salt = ByteArray(SALT_LENGTH)
            readFully(inputStream, salt)

            val baseNonce = ByteArray(NONCE_LENGTH)
            readFully(inputStream, baseNonce)

            // Read encryption method from header (v3/v4 format only)
            val storedMethod = if (isV2Format) {
                EncryptionMethod.STANDARD
            } else {
                val methodByte = inputStream.read()
                if (methodByte == -1 || methodByte >= EncryptionMethod.entries.size) {
                    throw IllegalArgumentException("Corrupted file: invalid encryption method byte")
                }
                EncryptionMethod.entries[methodByte]
            }

            // Read integrity check flag (v3/v4 format only)
            val hasIntegrityCheck = if (isV2Format) {
                false
            } else {
                val flag = inputStream.read()
                flag == 1
            }

            // Read and verify header HMAC (v4 format only)
            var storedHmac: ByteArray? = null
            val hasHeaderHmac = if (isV4Format) {
                val headerHmacFlag = inputStream.read()
                if (headerHmacFlag != 1) {
                    Log.w("EncryptionHelper", "V4 file without header HMAC flag set")
                }
                
                // Read stored HMAC
                storedHmac = ByteArray(HEADER_HMAC_LENGTH)
                readFully(inputStream, storedHmac!!)
                
                true // Header HMAC is present and will be verified after key derivation
            } else {
                false
            }

            // Derive key (Argon2 — slow by design, or from keyfile)
            val key = if (keyfileBytes != null) {
                deriveKeyFromKeyfile(keyfileBytes, password, salt, storedMethod)
            } else {
                deriveKey(password, salt, storedMethod)
            }
            val secretKey = SecretKeySpec(key, "AES")

            // Verify header HMAC for v4 format
            if (isV4Format && hasHeaderHmac && storedHmac != null) {
                val headerData = ByteArrayOutputStream()
                headerData.write(magicBuffer)
                headerData.write(salt)
                headerData.write(baseNonce)
                headerData.write(storedMethod.ordinal)
                headerData.write(if (hasIntegrityCheck) 1 else 0)
                headerData.write(1)
                
                val computedHmac = computeHmac(headerData.toByteArray(), key)
                headerData.close()
                
                if (!java.security.MessageDigest.isEqual(computedHmac, storedHmac)) {
                    throw SecurityException("Header HMAC verification failed - file may be tampered or incorrect password")
                }
            }

            val fileSize = if (totalSize > 0) totalSize else null
            val startTime = System.currentTimeMillis()
            var totalBytesDecrypted = 0L
            var chunkIndex = 0L
            var lastUpdateTime = startTime

            // For integrity verification
            val outputBuffer = if (hasIntegrityCheck || verifyIntegrity) {
                java.io.ByteArrayOutputStream()
            } else null

            progressCallback(0, fileSize ?: 1L, startTime)

            val lenBuf = ByteArray(4)

            while (true) {
                // Read 4-byte length prefix
                val lenRead = inputStream.read(lenBuf)
                if (lenRead == -1) break // EOF — done
                if (lenRead < 4) {
                    // Partial read at end — could be integrity data
                    break
                }

                val chunkLen = ByteBuffer.wrap(lenBuf).int

                // Check if this looks like integrity data at the end
                if (chunkLen <= 0 || chunkLen > CHUNK_SIZE + 256) {
                    // Could be start of integrity data, push back and break
                    break
                }

                val cipherChunk = ByteArray(chunkLen)
                readFully(inputStream, cipherChunk)

                val nonce = chunkNonce(baseNonce, chunkIndex)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

                // This will throw AEADBadTagException if password is wrong or data is corrupted
                val plainChunk = try {
                    cipher.doFinal(cipherChunk)
                } catch (e: javax.crypto.AEADBadTagException) {
                    throw javax.crypto.AEADBadTagException("Incorrect password or corrupted file. Chunk index: $chunkIndex")
                }

                outputStream.write(plainChunk)
                outputBuffer?.write(plainChunk)

                // Progress is based on bytes read from the encrypted file (totalSize is file size)
                totalBytesDecrypted += chunkLen + 4
                chunkIndex++

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) {
                    val reportTotal = fileSize ?: (totalBytesDecrypted * 1.2).toLong().coerceAtLeast(1)
                    progressCallback(totalBytesDecrypted, reportTotal, startTime)
                    lastUpdateTime = currentTime
                }
            }

            // Verify integrity if enabled
            var integrityResult: IntegrityResult? = null
            if (hasIntegrityCheck) {
                try {
                    val expectedChecksum = ByteArray(SHA256_CHECKSUM_LENGTH)
                    readFully(inputStream, expectedChecksum)

                    val expectedHmac = ByteArray(HMAC_SHA256_LENGTH)
                    readFully(inputStream, expectedHmac)

                    // Verify HMAC
                    val hmacValid = verifyHmac(expectedChecksum, key, expectedHmac)

                    if (hmacValid && outputBuffer != null) {
                        val computedChecksum = computeChecksum(outputBuffer.toByteArray())
                        val checksumValid = java.security.MessageDigest.isEqual(computedChecksum, expectedChecksum)
                        integrityResult = IntegrityResult(
                            hmacValid = true,
                            checksumValid = checksumValid,
                            message = if (checksumValid) "Integrity verified" else "Checksum mismatch - file may be corrupted"
                        )
                    } else {
                        integrityResult = IntegrityResult(
                            hmacValid = false,
                            checksumValid = false,
                            message = "HMAC verification failed - file may be tampered"
                        )
                    }
                } catch (e: Exception) {
                    integrityResult = IntegrityResult(
                        hmacValid = false,
                        checksumValid = false,
                        message = "Integrity check error: ${e.message}"
                    )
                }
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())

            val finalSize = fileSize ?: totalBytesDecrypted
            progressCallback(finalSize, finalSize, startTime)

            return@withContext DecryptionResult(
                success = true,
                integrityResult = integrityResult
            )

        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e("EncryptionHelper", "Password verification failed - AEAD tag mismatch", e)
            throw SecurityException("Incorrect password or corrupted file. The authentication tag verification failed.")
        } catch (e: java.security.GeneralSecurityException) {
            Log.e("EncryptionHelper", "Security error during decryption", e)
            throw SecurityException("Decryption failed due to a security error: ${e.message}")
        } catch (e: Exception) {
            Log.e("EncryptionHelper", "Decryption Failed", e)
            throw e
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    /** Reads exactly buf.size bytes from the stream, blocking until all are available. */
    private fun readFully(stream: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val read = stream.read(buf, offset, buf.size - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream reading ${buf.size} bytes at offset $offset")
            offset += read
        }
    }
}

/**
 * Result of a decryption operation including integrity verification status.
 */
data class DecryptionResult(
    val success: Boolean,
    val integrityResult: IntegrityResult? = null
)

/**
 * Integrity verification result.
 */
data class IntegrityResult(
    val hmacValid: Boolean,
    val checksumValid: Boolean,
    val message: String
) {
    val isValid: Boolean get() = hmacValid && checksumValid
}
