package com.obfs.encrypt.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handle Secure Deletion of unencrypted files to prevent casual data recovery.
 *
 * Honest limitation (MED-02): on flash storage with FTL/wear-leveling the OS
 * cannot guarantee the same physical blocks are overwritten, and SAF writes
 * via ContentResolver may go through FUSE. This 3-pass overwrite raises the
 * bar for software undelete tools but is NOT an anti-forensic guarantee.
 * Callers needing stronger assurance should also rely on file encryption
 * (ciphertext without the key is unrecoverable) rather than deletion alone.
 */
object SecureDelete {

    suspend fun secureDelete(context: Context, documentFile: DocumentFile, passes: Int = 3): Boolean = withContext(Dispatchers.IO) {
        if (!documentFile.exists() || !documentFile.canWrite()) return@withContext false
        
        try {
            val length = documentFile.length()
            val uri = documentFile.uri
            val contentResolver = context.contentResolver

            val bufferSize = 64 * 1024
            val buffer = ByteArray(bufferSize)

            for (pass in 0 until passes) {
                when (pass) {
                    0 -> buffer.fill(0x00.toByte())
                    1 -> buffer.fill(0xFF.toByte())
                    else -> SecureRandom().nextBytes(buffer)
                }

                contentResolver.openOutputStream(uri, "w")?.use { outStream ->
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(bufferSize.toLong(), length - written).toInt()
                        outStream.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    outStream.flush()
                }
                // Push bytes out of OS caches toward storage. Best-effort:
                // flash FTL may still remap blocks (see class KDoc).
                try {
                    contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        pfd.fileDescriptor.sync()
                    }
                } catch (_: Exception) {
                    // Sync unavailable via this provider; overwrite still attempted.
                }
            }
            // Finally, actually delete the file structure
            return@withContext documentFile.delete()
        } catch (e: IOException) {
            e.printStackTrace()
            // Fallback to normal delete if overwrite fails
            return@withContext documentFile.delete()
        }
    }
}
