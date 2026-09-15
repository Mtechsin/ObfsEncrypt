package com.obfs.encrypt.crypto

import android.net.Uri
import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

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
class EncryptionHelper(private val argon2Kt: Argon2Kt = Argon2Kt()) {

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
     * The keyfile bytes are hashed with SHA-256, then run through Argon2id
     * with the per-file salt — ALWAYS, even when no password is supplied.
     *
     * Fixed HIGH-01: the old path returned the raw SHA-256(keyfile) when
     * password == null, skipping salt and Argon2id, so every file encrypted
     * with the same keyfile shared one identical AES key. Now the salt
     * diversifies each file's key.
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
        
        // Always run Argon2id with the per-file salt so each file gets a unique key.
        val argonInput: ByteArray = if (password != null) {
            String(password).toByteArray(Charsets.UTF_8) + keyfileHash
        } else {
            // Keyfile-only mode: keyfile hash is the Argon2 password input.
            keyfileHash.copyOf()
        }
        try {
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = argonInput,
                salt = salt,
                tCostInIterations = method.tCostInIterations,
                mCostInKibibyte = method.mCostInKibibyte,
                parallelism = method.parallelism,
                hashLengthInBytes = 32
            )
            return@withContext result.rawHashAsByteArray()
        } finally {
            Arrays.fill(argonInput, 0.toByte())
            keyfileHash.fill(0)
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
        enableIntegrityCheck: Boolean = false,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false)
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

            // For integrity check, use streaming digest to avoid OOM
            val checksumDigest = if (enableIntegrityCheck) {
                MessageDigest.getInstance("SHA-256")
            } else null

            progressCallback(0, fileSize ?: 1L, startTime)

            // Read and encrypt chunk by chunk
            val plainBuf = ByteArray(CHUNK_SIZE)
            var bytesRead: Int

            while (inputStream.read(plainBuf).also { bytesRead = it } != -1) {
                // Check for pause
                while (isPaused.value) {
                    delay(200)
                    if (!kotlin.coroutines.coroutineContext.isActive) return@withContext
                }
                
                // Allow cancellation
                yield()

                // Update checksum digest before encryption to ensure original data is hashed
                checksumDigest?.update(plainBuf, 0, bytesRead)

                val nonce = chunkNonce(baseNonce, chunkIndex)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

                // doFinal on ≤1MB — safe, bounded memory
                val cipherChunk = cipher.doFinal(plainBuf, 0, bytesRead)

                // Write 4-byte big-endian length prefix then ciphertext+tag
                val lenBytes = ByteBuffer.allocate(4).putInt(cipherChunk.size).array()
                outputStream.write(lenBytes)
                outputStream.write(cipherChunk)

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
            if (enableIntegrityCheck && checksumDigest != null) {
                val checksum = checksumDigest.digest()
                val hmac = computeHmac(checksum, key)

                outputStream.write(checksum)
                outputStream.write(hmac)
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())
            headerData.close()

            val finalSize = fileSize ?: totalBytesRead
            progressCallback(finalSize, finalSize, startTime)

        } catch (e: Exception) {
            com.obfs.encrypt.diagnostics.AppLogger.e("EncryptionHelper", "Encryption failed", e)
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
        verifyIntegrity: Boolean = false,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false)
    ): DecryptionResult = withContext(Dispatchers.IO) {
        // Use PushbackInputStream to handle chunk/checksum boundary without losing bytes
        val pushbackStream = PushbackInputStream(inputStream, 4)
        try {
            // Read and verify magic header
            val magicBuffer = ByteArray(MAGIC_HEADER.length)
            if (readFully(pushbackStream, magicBuffer) < MAGIC_HEADER.length) {
                throw IllegalArgumentException("Corrupted file: could not read magic header")
            }
            val magic = String(magicBuffer, Charsets.UTF_8)

            val isV4Format = magic == MAGIC_HEADER
            val isV3Format = magic == MAGIC_HEADER_V3
            val isV2Format = magic == MAGIC_HEADER_V2
            
            if (!isV4Format && !isV3Format && !isV2Format) {
                throw IllegalArgumentException("Invalid file format. Expected OBFSv3/v4 header but got: $magic")
            }

            val salt = ByteArray(SALT_LENGTH)
            if (readFully(pushbackStream, salt) < SALT_LENGTH) {
                throw IllegalArgumentException("Corrupted file: could not read salt")
            }

            val baseNonce = ByteArray(NONCE_LENGTH)
            if (readFully(pushbackStream, baseNonce) < NONCE_LENGTH) {
                throw IllegalArgumentException("Corrupted file: could not read nonce")
            }

            // Read encryption method from header (v3/v4 format only)
            val storedMethod = if (isV2Format) {
                EncryptionMethod.STANDARD
            } else {
                val methodByte = pushbackStream.read()
                if (methodByte == -1 || methodByte >= EncryptionMethod.entries.size) {
                    throw IllegalArgumentException("Corrupted file: invalid encryption method byte")
                }
                EncryptionMethod.entries[methodByte]
            }

            // Read integrity check flag (v3/v4 format only)
            val hasIntegrityCheck = if (isV2Format) {
                false
            } else {
                val flag = pushbackStream.read()
                flag == 1
            }

            // Read and verify header HMAC (v4 format only)
            var storedHmac: ByteArray? = null
            val hasHeaderHmac = if (isV4Format) {
                val headerHmacFlag = pushbackStream.read()
                if (headerHmacFlag != 1) {
                    Log.w("EncryptionHelper", "V4 file without header HMAC flag set")
                }

                // Read stored HMAC
                storedHmac = ByteArray(HEADER_HMAC_LENGTH)
                if (readFully(pushbackStream, storedHmac!!) < HEADER_HMAC_LENGTH) {
                    throw IllegalArgumentException("Corrupted file: could not read header HMAC")
                }

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
            // Exact header bytes consumed, for deterministic trailer detection.
            val headerLen: Long = when {
                isV4Format -> (MAGIC_HEADER.length + SALT_LENGTH + NONCE_LENGTH + 1 + 1 + 1 + HEADER_HMAC_LENGTH).toLong()
                isV3Format -> (MAGIC_HEADER_V3.length + SALT_LENGTH + NONCE_LENGTH + 1 + 1).toLong()
                else -> (MAGIC_HEADER_V2.length + SALT_LENGTH + NONCE_LENGTH).toLong()
            }

            // For integrity verification - use a streaming digest instead of buffering all data
            val checksumDigest = if (hasIntegrityCheck || verifyIntegrity) {
                MessageDigest.getInstance("SHA-256")
            } else null

            progressCallback(0, fileSize ?: 1L, startTime)

            val lenBuf = ByteArray(4)

            while (true) {
                // Check for pause
                while (isPaused.value) {
                    delay(200)
                    if (!kotlin.coroutines.coroutineContext.isActive) {
                        return@withContext DecryptionResult(success = false)
                    }
                }

                // Allow cancellation
                yield()

                // Read 4-byte length prefix
                val lenRead = readFully(pushbackStream, lenBuf)
                if (lenRead < 4) {
                    // EOF or partial read at end — break and handle integrity data if any
                    break
                }

                val chunkLen = ByteBuffer.wrap(lenBuf).int

                // Check if this looks like integrity data at the end
                // Valid chunk lengths are between 1 and CHUNK_SIZE + GCM_TAG_LENGTH/8
                val maxChunkLen = CHUNK_SIZE + (GCM_TAG_LENGTH / 8)
                if (chunkLen <= 0 || chunkLen > maxChunkLen) {
                    // Not a valid chunk length, must be the start of integrity data
                    pushbackStream.unread(lenBuf)
                    break
                }

                // Fixed CRIT-02: removed the old `chunkLen == 32 -> trailer`
                // heuristic. A final 16-byte plaintext chunk encrypts to exactly
                // 32 bytes (16 + GCM tag), so the heuristic silently dropped the
                // last 16 bytes of every such file when integrity was on.
                // Trailer is detected deterministically instead: when the total
                // encrypted size is known, the 64-byte trailer
                // (32 checksum + 32 HMAC) is exactly the remaining bytes.
                // Otherwise every syntactically valid length is treated as a
                // chunk; a checksum's random prefix bytes almost never parse as
                // a satisfiable chunk length, and a short read still breaks to
                // the trailer path below.
                if (hasIntegrityCheck && totalSize > 0) {
                    val remainingBefore = totalSize - headerLen - totalBytesDecrypted
                    if (remainingBefore == (SHA256_CHECKSUM_LENGTH + HMAC_SHA256_LENGTH).toLong()) {
                        pushbackStream.unread(lenBuf)
                        break
                    }
                }

                val cipherChunk = ByteArray(chunkLen)
                val cipherRead = readFully(pushbackStream, cipherChunk)
                if (cipherRead < cipherChunk.size) {
                    // Unexpected EOF, unread what we got and break
                    pushbackStream.unread(cipherChunk, 0, cipherRead)
                    pushbackStream.unread(lenBuf)
                    break
                }

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
                // Update checksum digest incrementally - no buffering needed
                checksumDigest?.update(plainChunk)

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
                    val checksumRead = readFully(pushbackStream, expectedChecksum)
                    if (checksumRead < SHA256_CHECKSUM_LENGTH) {
                        throw IllegalArgumentException("Corrupted file: could not read integrity checksum")
                    }

                    val expectedHmac = ByteArray(HMAC_SHA256_LENGTH)
                    val hmacRead = readFully(pushbackStream, expectedHmac)
                    if (hmacRead < HMAC_SHA256_LENGTH) {
                        throw IllegalArgumentException("Corrupted file: could not read integrity HMAC")
                    }

                    // Verify HMAC first (no secret material in logs — MED-01).
                    val hmacValid = verifyHmac(expectedChecksum, key, expectedHmac)

                    if (hmacValid && checksumDigest != null) {
                        val computedChecksum = checksumDigest.digest()
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
            } else if (verifyIntegrity && checksumDigest != null) {
                // If header says no integrity check but caller requested it (e.g. for already decrypted data)
                integrityResult = IntegrityResult(
                    hmacValid = true, // No HMAC to verify
                    checksumValid = true, // We have nothing to compare against, but we computed it
                    message = "Integrity computation complete (no verification possible)"
                )
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())

            val finalSize = fileSize ?: totalBytesDecrypted
            progressCallback(finalSize, finalSize, startTime)

            // Fixed CRIT-03 (part 1): success must reflect integrity. The old code
            // always returned success=true even when HMAC/checksum failed,
            // so callers deleted originals after tampered decrypts.
            val integrityOk = integrityResult?.isValid ?: true
            return@withContext DecryptionResult(
                success = integrityOk,
                integrityResult = integrityResult
            )

        } catch (e: javax.crypto.AEADBadTagException) {
            com.obfs.encrypt.diagnostics.AppLogger.e("EncryptionHelper", "Password verification failed - AEAD tag mismatch", e)
            throw SecurityException("Incorrect password or corrupted file. The authentication tag verification failed.")
        } catch (e: java.security.GeneralSecurityException) {
            com.obfs.encrypt.diagnostics.AppLogger.e("EncryptionHelper", "Security error during decryption", e)
            throw SecurityException("Decryption failed due to a security error: ${e.message}")
        } catch (e: Exception) {
            com.obfs.encrypt.diagnostics.AppLogger.e("EncryptionHelper", "Decryption failed", e)
            throw e
        } finally {
            pushbackStream.close()
        }
    }

    /** Reads exactly buf.size bytes from the stream, blocking until all are available.
     *  Returns the total number of bytes read, or less than buf.size if EOF is reached.
     */
    private fun readFully(stream: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val read = stream.read(buf, offset, buf.size - offset)
            if (read == -1) return offset  // Return what we've read so far
            offset += read
        }
        return offset
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
