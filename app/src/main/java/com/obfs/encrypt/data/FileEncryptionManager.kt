package com.obfs.encrypt.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.obfs.encrypt.crypto.DecryptionResult
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager for file encryption/decryption operations.
 * Consolidates boilerplate for URI handling, stream management, and output file creation.
 */
@Singleton
class FileEncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val encryptionHelper: EncryptionHelper,
    private val appDirectoryManager: AppDirectoryManager
) {

    /**
     * Encrypt a file from a Uri to an output Uri.
     *
     * Staged via a cache-dir temp file so a crash/cancel never leaves a
     * truncated ciphertext at the final URI.
     */
    suspend fun encryptUri(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        enableIntegrityCheck: Boolean = false,
        keyfileBytes: ByteArray? = null,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false),
        progressCallback: suspend (current: Long, total: Long, startTime: Long) -> Unit
    ) {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileSize = sourceFile.length()

        val tmp = File.createTempFile("obfs_enc_", ".tmp", context.cacheDir)
        try {
            cr.openInputStream(sourceUri)?.use { inputStream ->
                tmp.outputStream().use { tmpOut ->
                    encryptionHelper.encrypt(
                        inputStream = inputStream,
                        outputStream = tmpOut,
                        password = password,
                        method = method,
                        progressCallback = progressCallback,
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        enableIntegrityCheck = enableIntegrityCheck,
                        isPaused = isPaused
                    )
                }
            } ?: throw IllegalStateException("Could not open input stream")
            // Commit staged ciphertext to the final URI only after success.
            cr.openOutputStream(outputUri, "w")?.use { finalOut ->
                tmp.inputStream().use { tmpIn -> tmpIn.copyTo(finalOut) }
                finalOut.flush()
            } ?: throw IllegalStateException("Could not open output stream")
        } catch (e: kotlinx.coroutines.CancellationException) {
            try { tmp.delete() } catch (_: Exception) {}
            deleteUriQuietly(outputUri)
            throw e
        } catch (e: Exception) {
            try { tmp.delete() } catch (_: Exception) {}
            deleteUriQuietly(outputUri)
            throw e
        } finally {
            try { tmp.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Decrypt a file from a Uri to an output Uri.
     *
     * Fixed CRIT-04: previously streamed plaintext straight into the final
     * URI with no staging and no cleanup, leaving partial plaintext behind
     * on failure/cancel. Now decrypts into a cache-dir temp file first and
     * commits to the final URI only when decryption (and integrity, when
     * present) succeeds. Temp + partial outputs are deleted on any failure
     * or coroutine cancellation.
     */
    suspend fun decryptUri(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
        verifyIntegrity: Boolean = true,
        keyfileBytes: ByteArray? = null,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false),
        progressCallback: suspend (current: Long, total: Long, startTime: Long) -> Unit
    ): DecryptionResult {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileSize = sourceFile.length()

        val tmp = File.createTempFile("obfs_dec_", ".tmp", context.cacheDir)
        try {
            val result = cr.openInputStream(sourceUri)?.use { inputStream ->
                tmp.outputStream().use { tmpOut ->
                    encryptionHelper.decrypt(
                        inputStream = inputStream,
                        outputStream = tmpOut,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = progressCallback,
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        verifyIntegrity = verifyIntegrity,
                        isPaused = isPaused
                    )
                }
            } ?: throw IllegalStateException("Could not open input stream")

            // Only commit plaintext when integrity (if any) verifies.
            val integrityOk = result.integrityResult?.isValid ?: true
            if (result.success && integrityOk) {
                cr.openOutputStream(outputUri, "w")?.use { finalOut ->
                    tmp.inputStream().use { tmpIn -> tmpIn.copyTo(finalOut) }
                    finalOut.flush()
                } ?: throw IllegalStateException("Could not open output stream")
            } else {
                // Do not leave an empty/partial file at the destination.
                deleteUriQuietly(outputUri)
            }
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            deleteUriQuietly(outputUri)
            throw e
        } catch (e: Exception) {
            deleteUriQuietly(outputUri)
            throw e
        } finally {
            try { tmp.delete() } catch (_: Exception) {}
        }
    }

    private fun deleteUriQuietly(uri: Uri) {
        try {
            DocumentFile.fromSingleUri(context, uri)?.delete()
        } catch (_: Exception) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
    }

    /**
     * Create an appropriate output file for encryption/decryption.
     */
    fun createOutputFile(
        inputUri: Uri, 
        encrypt: Boolean, 
        customOutputDirUri: Uri? = null
    ): Uri {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, inputUri)
            ?: throw IllegalArgumentException("Could not access source file")
        
        val outputName = if (encrypt) {
            "${sourceFile.name}.obfs"
        } else {
            sourceFile.name?.removeSuffix(".obfs") ?: "decrypted_${System.currentTimeMillis()}"
        }

        val mimeType = if (encrypt) "application/octet-stream" else "*/*"

        // Priority 1: Custom Output Directory (SAF)
        if (customOutputDirUri != null) {
            val dir = DocumentFile.fromTreeUri(context, customOutputDirUri)
                ?: throw IllegalStateException("Output directory not found or not writable")
            val target = createUniqueFile(dir, outputName, mimeType)
            return target.uri
        }

        // Priority 2: Same directory as source (if writable SAF)
        val parent = sourceFile.parentFile
        if (parent != null && parent.canWrite()) {
            val target = createUniqueFile(parent, outputName, mimeType)
            return target.uri
        }

        // Priority 3: Default App Directory
        val fallbackDir = appDirectoryManager.getOutputDirectory()
            ?: throw IllegalStateException("Could not access default output directory")
        val fallbackDocFile = DocumentFile.fromFile(fallbackDir)
        // Note: DocumentFile.fromFile might not be fully functional for creation in some contexts,
        // but for public Documents/ObfsEncrypt it should work or we use raw File API.
        
        val uniqueFile = uniqueFileRaw(fallbackDir, outputName)
        return Uri.fromFile(uniqueFile)
    }

    private fun createUniqueFile(parent: DocumentFile, name: String, mimeType: String): DocumentFile {
        var finalName = sanitizeFileName(name)
        var counter = 1
        while (parent.findFile(finalName) != null) {
            val base = if (finalName.contains(".")) finalName.substringBeforeLast(".") else finalName
            val ext = if (finalName.contains(".")) ".${finalName.substringAfterLast(".")}" else ""
            finalName = "$base ($counter)$ext"
            counter++
        }
        return parent.createFile(mimeType, finalName) 
            ?: throw IllegalStateException("Failed to create output file")
    }

    /**
     * Strip path separators and parent refs so a crafted DocumentFile.name
     * like "../../evil" can never escape the destination directory.
     */
    internal fun sanitizeFileName(name: String): String {
        var clean = name.substringAfterLast('/').substringAfterLast('\\')
        clean = clean.replace("..", "_")
        clean = clean.filter { it.code >= 0x20 && it != '/' && it != '\\' && it.code != 0 }
        if (clean.isBlank()) clean = "file_${System.currentTimeMillis()}"
        // Cap length to avoid filesystem limits (255 bytes).
        if (clean.length > 200) {
            val ext = clean.substringAfterLast('.', "")
            val base = clean.substringBeforeLast('.', clean)
            clean = if (ext.isNotEmpty() && ext.length < 20) {
                base.take(200 - ext.length - 1) + "." + ext
            } else {
                clean.take(200)
            }
        }
        return clean
    }

    private fun uniqueFileRaw(dir: File, name: String): File {
        val safeName = sanitizeFileName(name)
        var f = File(dir, safeName)
        if (!f.exists()) return f
        val base = safeName.substringBeforeLast(".")
        val ext = if (safeName.contains(".")) ".${safeName.substringAfterLast(".")}" else ""
        var counter = 1
        while (f.exists()) {
            f = File(dir, "$base ($counter)$ext")
            counter++
        }
        return f
    }
}
