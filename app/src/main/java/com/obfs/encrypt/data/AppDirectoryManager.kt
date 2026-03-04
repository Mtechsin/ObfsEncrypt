package com.obfs.encrypt.data

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the app's output directory for storing encrypted/decrypted files.
 *
 * Output is always stored in:
 *   /storage/emulated/0/Documents/ObfsEncrypt/
 *
 * Why Documents and not Android/data:
 * On Android 11+ (API 30+), the Android/data folder is hidden from all file managers.
 * Using MANAGE_EXTERNAL_STORAGE we can write to the public Documents directory so that
 * users can actually find their encrypted files.
 */
@Singleton
class AppDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val APP_FOLDER_NAME = "ObfsEncrypt"
        const val OUTPUT_FOLDER_NAME = "Output"
    }

    /**
     * Returns the main app directory: /sdcard/Documents/ObfsEncrypt/
     * Creates it if it doesn't exist.
     */
    fun getAppDirectory(): File? {
        // Always use the public Documents directory so it's visible in any file manager.
        // Requires MANAGE_EXTERNAL_STORAGE (Android 11+) or WRITE_EXTERNAL_STORAGE (≤10).
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = File(documentsDir, APP_FOLDER_NAME)
        return if (appDir.exists() || appDir.mkdirs()) appDir else null
    }

    /**
     * Returns the output directory: /sdcard/Documents/ObfsEncrypt/Output/
     * Creates it if it doesn't exist.
     */
    fun getOutputDirectory(): File? {
        val appDir = getAppDirectory() ?: return null
        val outputDir = File(appDir, OUTPUT_FOLDER_NAME)
        return if (outputDir.exists() || outputDir.mkdirs()) outputDir else null
    }

    fun getOutputDirectoryPath(): String =
        getOutputDirectory()?.absolutePath ?: "Not available"

    fun hasWriteAccess(): Boolean {
        val outputDir = getOutputDirectory()
        return outputDir?.exists() == true && outputDir.canWrite()
    }

    fun verifyWriteAccess(): Boolean {
        return try {
            val outputDir = getOutputDirectory() ?: return false
            val testFile = File(outputDir, ".write_test")
            testFile.createNewFile()
            testFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAppFolderIcon(): String = if (hasWriteAccess()) "✓" else "✗"
}
