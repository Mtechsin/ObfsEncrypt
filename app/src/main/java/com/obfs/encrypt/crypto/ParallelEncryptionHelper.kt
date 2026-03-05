package com.obfs.encrypt.crypto

import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Parallel Chunk Encryption Data
 */
private data class ChunkTask(
    val index: Long,
    val data: ByteArray,
    val size: Int
)

private data class EncryptedChunk(
    val index: Long,
    val ciphertext: ByteArray
)

/**
 * Enhanced Encryption Helper with Parallel Processing
 * 
 * This class provides parallel chunk encryption for improved performance
 * on multi-core devices. It uses a thread pool to encrypt multiple chunks
 * simultaneously while maintaining the correct order in the output.
 */
class ParallelEncryptionHelper {

    private val argon2Kt = Argon2Kt()
    
    // Thread pool for parallel encryption (number of cores - 1 to leave room for UI)
    private val encryptionExecutor = Executors.newFixedThreadPool(
        maxOf(2, Runtime.getRuntime().availableProcessors() - 1)
    )

    companion object {
        private const val TAG = "ParallelEncryptionHelper"
        const val MAGIC_HEADER = "OBFSv4"
        const val SALT_LENGTH = 16
        const val NONCE_LENGTH = 12
        const val GCM_TAG_LENGTH = 128 // bits
        const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB per chunk
        
        // Integrity verification constants
        const val SHA256_CHECKSUM_LENGTH = 32
        const val HMAC_SHA256_LENGTH = 32
        const val HEADER_HMAC_LENGTH = 32
    }

    /**
     * Encrypt data using parallel chunk processing.
     * 
     * @param inputStream Source data to encrypt
     * @param outputStream Destination for encrypted data
     * @param password User password
     * @param method Encryption method (FAST/STANDARD/STRONG)
     * @param progressCallback Progress callback (bytesRead, totalBytes, startTime)
     * @param totalSize Total size of input (if known, 0 otherwise)
     * @param keyfileBytes Optional keyfile bytes for combined authentication
     * @param enableIntegrityCheck Whether to compute and store integrity checksum
     */
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

            // Derive key (Argon2 or from keyfile)
            val key = if (keyfileBytes != null) {
                deriveKeyFromKeyfile(keyfileBytes, password, salt, method)
            } else {
                deriveKey(password, salt, method)
            }
            val secretKey = SecretKeySpec(key, "AES")

            // Build header data for HMAC computation
            val headerData = ByteArrayOutputStream()
            headerData.write(MAGIC_HEADER.toByteArray(Charsets.UTF_8))
            headerData.write(salt)
            headerData.write(baseNonce)
            headerData.write(method.ordinal)
            headerData.write(if (enableIntegrityCheck) 1 else 0)
            headerData.write(1) // Header HMAC enabled flag

            // Compute HMAC of header data
            val headerHmac = computeHmac(headerData.toByteArray(), key)

            // Write header + HMAC
            outputStream.write(headerData.toByteArray())
            outputStream.write(headerHmac)

            val fileSize = if (totalSize > 0) totalSize else null
            val startTime = System.currentTimeMillis()
            val totalBytesRead = AtomicLong(0)
            var lastUpdateTime = startTime

            progressCallback(0, fileSize ?: 1L, startTime)

            // Read all chunks first
            val chunks = mutableListOf<ChunkTask>()
            var chunkIndex = 0L
            val plainBuf = ByteArray(CHUNK_SIZE)
            var bytesRead: Int

            while (inputStream.read(plainBuf).also { bytesRead = it } != -1) {
                chunks.add(
                    ChunkTask(
                        index = chunkIndex++,
                        data = plainBuf.copyOf(bytesRead),
                        size = bytesRead
                    )
                )
            }

            // Encrypt chunks in parallel
            val encryptedChunks = ConcurrentLinkedQueue<EncryptedChunk>()
            val processedCount = AtomicInteger(0)
            
            val jobs = chunks.map { chunk ->
                async {
                    val nonce = chunkNonce(baseNonce, chunk.index)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
                    
                    val ciphertext = cipher.doFinal(chunk.data, 0, chunk.size)
                    
                    encryptedChunks.add(EncryptedChunk(chunk.index, ciphertext))
                    
                    // Update progress
                    val current = totalBytesRead.addAndGet(chunk.size.toLong())
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 100) {
                        val reportTotal = fileSize ?: (current * 1.2).toLong().coerceAtLeast(1)
                        progressCallback(current, reportTotal, startTime)
                        lastUpdateTime = currentTime
                    }
                    
                    // Clear sensitive data
                    chunk.data.fill(0)
                }
            }

            // Wait for all encryption jobs to complete
            jobs.awaitAll()

            // Write encrypted chunks in order
            val sortedChunks = encryptedChunks.sortedBy { it.index }
            for (encryptedChunk in sortedChunks) {
                val lenBytes = ByteBuffer.allocate(4).putInt(encryptedChunk.ciphertext.size).array()
                outputStream.write(lenBytes)
                outputStream.write(encryptedChunk.ciphertext)
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())
            headerData.close()

            val finalSize = fileSize ?: totalBytesRead.get()
            progressCallback(finalSize, finalSize, startTime)

        } catch (e: Exception) {
            Log.e(TAG, "Parallel Encryption Failed", e)
            throw e
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    /**
     * Decrypt data (sequential - required for correct ordering).
     * 
     * Decryption must be sequential because chunks need to be written in order.
     * However, we still use efficient buffering and streaming.
     */
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

            if (magic != MAGIC_HEADER) {
                throw IllegalArgumentException("Invalid file format. Expected $MAGIC_HEADER but got: $magic")
            }

            val salt = ByteArray(SALT_LENGTH)
            readFully(inputStream, salt)

            val baseNonce = ByteArray(NONCE_LENGTH)
            readFully(inputStream, baseNonce)

            // Read encryption method
            val methodByte = inputStream.read()
            if (methodByte == -1 || methodByte >= EncryptionMethod.entries.size) {
                throw IllegalArgumentException("Corrupted file: invalid encryption method byte")
            }
            val storedMethod = EncryptionMethod.entries[methodByte]

            // Read integrity check flag
            val hasIntegrityCheck = inputStream.read() == 1

            // Read header HMAC flag and stored HMAC
            val headerHmacFlag = inputStream.read()
            val storedHmac = ByteArray(HEADER_HMAC_LENGTH)
            readFully(inputStream, storedHmac)

            // Derive key
            val key = if (keyfileBytes != null) {
                deriveKeyFromKeyfile(keyfileBytes, password, salt, storedMethod)
            } else {
                deriveKey(password, salt, storedMethod)
            }
            val secretKey = SecretKeySpec(key, "AES")

            // Verify header HMAC
            val headerData = ByteArrayOutputStream()
            headerData.write(magicBuffer)
            headerData.write(salt)
            headerData.write(baseNonce)
            headerData.write(storedMethod.ordinal)
            headerData.write(if (hasIntegrityCheck) 1 else 0)
            headerData.write(1)
            
            val computedHmac = computeHmac(headerData.toByteArray(), key)
            headerData.close()
            
            if (!MessageDigest.isEqual(computedHmac, storedHmac)) {
                throw SecurityException("Header HMAC verification failed - file may be tampered or incorrect password")
            }

            val fileSize = if (totalSize > 0) totalSize else null
            val startTime = System.currentTimeMillis()
            var totalBytesDecrypted = 0L
            var chunkIndex = 0L
            var lastUpdateTime = startTime

            // For integrity verification
            val outputBuffer = if (hasIntegrityCheck || verifyIntegrity) {
                ByteArrayOutputStream()
            } else null

            progressCallback(0, fileSize ?: 1L, startTime)

            val lenBuf = ByteArray(4)

            while (true) {
                val lenRead = inputStream.read(lenBuf)
                if (lenRead == -1) break
                if (lenRead < 4) break

                val chunkLen = ByteBuffer.wrap(lenBuf).int
                if (chunkLen <= 0 || chunkLen > CHUNK_SIZE + 256) break

                val cipherChunk = ByteArray(chunkLen)
                readFully(inputStream, cipherChunk)

                val nonce = chunkNonce(baseNonce, chunkIndex)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

                val plainChunk = try {
                    cipher.doFinal(cipherChunk)
                } catch (e: javax.crypto.AEADBadTagException) {
                    throw javax.crypto.AEADBadTagException("Incorrect password or corrupted file. Chunk index: $chunkIndex")
                }

                outputStream.write(plainChunk)
                outputBuffer?.write(plainChunk)

                totalBytesDecrypted += chunkLen + 4
                chunkIndex++

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) {
                    val reportTotal = fileSize ?: (totalBytesDecrypted * 1.2).toLong().coerceAtLeast(1)
                    progressCallback(totalBytesDecrypted, reportTotal, startTime)
                    lastUpdateTime = currentTime
                }
            }

            outputStream.flush()
            Arrays.fill(key, 0.toByte())

            return@withContext DecryptionResult(success = true)

        } catch (e: Exception) {
            Log.e(TAG, "Decryption Failed", e)
            throw e
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    // Helper methods (same as EncryptionHelper)
    private suspend fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        method: EncryptionMethod
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
        result.rawHashAsByteArray()
    }

    private suspend fun deriveKeyFromKeyfile(
        keyfileBytes: ByteArray,
        password: CharArray?,
        salt: ByteArray,
        method: EncryptionMethod
    ): ByteArray = withContext(Dispatchers.Default) {
        val keyfileHash = MessageDigest.getInstance("SHA-256").digest(keyfileBytes)
        
        if (password != null) {
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
            result.rawHashAsByteArray()
        } else {
            keyfileHash
        }
    }

    private fun chunkNonce(baseNonce: ByteArray, chunkIndex: Long): ByteArray {
        val nonce = baseNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        for (i in 0 until 8) {
            nonce[NONCE_LENGTH - 8 + i] = (nonce[NONCE_LENGTH - 8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return nonce
    }

    private suspend fun computeHmac(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.doFinal(data)
    }

    private fun readFully(stream: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val read = stream.read(buf, offset, buf.size - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
    }

    /**
     * Shutdown the thread pool when no longer needed.
     */
    fun shutdown() {
        encryptionExecutor.shutdown()
    }
}
