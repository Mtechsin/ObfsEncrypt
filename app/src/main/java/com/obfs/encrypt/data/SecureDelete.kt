package com.obfs.encrypt.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handle Secure Deletion of unencrypted files to prevent data recovery.
 *
 * Why this approach was chosen:
 * Standard file deletion simply unlinks the file metadata, leaving traces on the flash memory.
 * By using a 3-pass overwrite (0x00, 0xFF, random), we make recovery extremely difficult,
 * even for forensics on traditional SSDs/eMMCs (subject to wear leveling constraints).
 * 
 * Performance implications:
 * A 3-pass overwrite is slow. We perform this on the IO dispatcher.
 * 
 * Android 7 / SAF compatibility tricks used:
 * If working on modern Android via DocumentFile, direct file access isn't always available 
 * or relies on FUSE which complicates deep overwriting. We grab the content resolver and 
 * perform the overwrite streamingly before calling DocumentFile.delete().
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
