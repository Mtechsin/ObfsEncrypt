package com.obfs.encrypt.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.obfs.encrypt.R
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.EncryptionHistoryRepository
import com.obfs.encrypt.data.createHistoryItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Worker for performing encryption/decryption operations in the background.
 * Provides progress notifications and handles app termination gracefully.
 *
 * Usage:
 * ```kotlin
 * val workRequest = OneTimeWorkRequestBuilder<EncryptionWorker>()
 *     .setInputData(workDataOf(
 *         "operation" to "encrypt",
 *         "file_uris" to urisJson,
 *         "password" to password,
 *         "method" to method.name,
 *         "delete_original" to deleteOriginal
 *     ))
 *     .build()
 * WorkManager.getInstance(context).enqueue(workRequest)
 * ```
 */
@HiltWorker
class EncryptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val encryptionHelper: EncryptionHelper,
    private val historyRepository: EncryptionHistoryRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "encryption_progress_channel"
        const val NOTIFICATION_ID = 1001
        
        const val KEY_OPERATION = "operation"
        const val KEY_FILE_URIS = "file_uris"
        const val KEY_PASSWORD = "password"
        const val KEY_METHOD = "method"
        const val KEY_DELETE_ORIGINAL = "delete_original"
        const val KEY_ENABLE_INTEGRITY = "enable_integrity"
        
        const val OPERATION_ENCRYPT = "encrypt"
        const val OPERATION_DECRYPT = "decrypt"
    }

    private val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // Progress tracking for live updates
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get input data
            val operation = inputData.getString(KEY_OPERATION) ?: return@withContext Result.failure()
            val fileUrisJson = inputData.getString(KEY_FILE_URIS) ?: return@withContext Result.failure()
            val passwordStr = inputData.getString(KEY_PASSWORD) ?: return@withContext Result.failure()
            val password = passwordStr.toCharArray()
            val method = try {
                EncryptionMethod.valueOf(inputData.getString(KEY_METHOD) ?: "STANDARD")
            } catch (e: Exception) {
                EncryptionMethod.STANDARD
            }
            val deleteOriginal = inputData.getBoolean(KEY_DELETE_ORIGINAL, false)
            val enableIntegrity = inputData.getBoolean(KEY_ENABLE_INTEGRITY, false)

            // Parse URIs from JSON
            val uris = parseUrisFromJson(fileUrisJson)
            if (uris.isEmpty()) {
                return@withContext Result.failure()
            }

            // Show initial notification
            setForeground(createForegroundInfo(operation, 0f))

            // Process each file
            uris.forEachIndexed { index, uri ->
                if (isStopped) {
                    // Work was cancelled
                    cleanupPassword(password)
                    return@withContext Result.failure()
                }

                val fileName = getFileNameFromUri(uri)
                val fileSize = getFileSizeFromUri(uri)

                try {
                    when (operation) {
                        OPERATION_ENCRYPT -> {
                            processEncryption(uri, password, method, deleteOriginal, enableIntegrity)
                            
                            // Add to history on success
                            historyRepository.addHistoryItem(
                                createHistoryItem(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    operationType = EncryptionHistoryItem.OperationType.ENCRYPT,
                                    encryptionMethod = method,
                                    success = true,
                                    secureDelete = deleteOriginal
                                )
                            )
                        }
                        OPERATION_DECRYPT -> {
                            processDecryption(uri, password, deleteOriginal, enableIntegrity)
                            
                            // Add to history on success
                            historyRepository.addHistoryItem(
                                createHistoryItem(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    operationType = EncryptionHistoryItem.OperationType.DECRYPT,
                                    success = true
                                )
                            )
                        }
                    }

                    // Update progress
                    val progress = (index + 1).toFloat() / uris.size
                    _progress.value = progress
                    setForeground(createForegroundInfo(operation, progress, fileName))

                } catch (e: Exception) {
                    // Log failure to history
                    historyRepository.addHistoryItem(
                        createHistoryItem(
                            fileName = fileName,
                            fileSize = fileSize,
                            operationType = if (operation == OPERATION_ENCRYPT) 
                                EncryptionHistoryItem.OperationType.ENCRYPT 
                            else 
                                EncryptionHistoryItem.OperationType.DECRYPT,
                            success = false,
                            errorMessage = e.localizedMessage
                        )
                    )
                    
                    // Continue with next file instead of failing all
                    // Or fail completely based on preference
                    throw e
                }
            }

            cleanupPassword(password)

            // Show completion notification
            showCompletionNotification(operation, uris.size)

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            cleanupPassword(inputData.getString(KEY_PASSWORD)?.toCharArray())
            Result.failure()
        }
    }

    /**
     * Create foreground info with notification for progress tracking.
     */
    private fun createForegroundInfo(
        operation: String,
        progress: Float,
        currentFile: String = ""
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(if (operation == OPERATION_ENCRYPT) "Encrypting files..." else "Decrypting files...")
            .setContentText(if (currentFile.isNotEmpty()) currentFile else "Preparing...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /**
     * Show completion notification when work is done.
     */
    private fun showCompletionNotification(operation: String, fileCount: Int) {
        val title = if (operation == OPERATION_ENCRYPT) {
            "Encryption Complete"
        } else {
            "Decryption Complete"
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$fileCount file(s) processed successfully")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    /**
     * Create notification channel for Oreo+.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Encryption Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows encryption/decryption progress"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Process single file encryption.
     */
    private suspend fun processEncryption(
        uri: Uri,
        password: CharArray,
        method: EncryptionMethod,
        deleteOriginal: Boolean,
        enableIntegrity: Boolean
    ) {
        val cr = applicationContext.contentResolver
        val sourceFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, uri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileSize = sourceFile.length()

        val outputUri = createOutputFile(uri, encrypt = true)

        cr.openInputStream(uri)?.use { inputStream ->
            cr.openOutputStream(outputUri)?.use { outputStream ->
                encryptionHelper.encrypt(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    password = password,
                    method = method,
                    progressCallback = { current, total, _ -> },
                    totalSize = fileSize,
                    keyfileBytes = null,
                    enableIntegrityCheck = enableIntegrity
                )
            }
        }

        if (deleteOriginal) {
            com.obfs.encrypt.data.SecureDelete.secureDelete(applicationContext, sourceFile)
        }
    }

    /**
     * Process single file decryption.
     */
    private suspend fun processDecryption(
        uri: Uri,
        password: CharArray,
        deleteOriginal: Boolean,
        verifyIntegrity: Boolean
    ) {
        val cr = applicationContext.contentResolver
        val sourceFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, uri)
            ?: throw IllegalArgumentException("Cannot read source file")
        val fileSize = sourceFile.length()

        val outputUri = createOutputFile(uri, encrypt = false)

        cr.openInputStream(uri)?.use { inputStream ->
            cr.openOutputStream(outputUri)?.use { outputStream ->
                encryptionHelper.decrypt(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { current, total, _ -> },
                    totalSize = fileSize,
                    keyfileBytes = null,
                    verifyIntegrity = verifyIntegrity
                )
            }
        }

        if (deleteOriginal) {
            com.obfs.encrypt.data.SecureDelete.secureDelete(applicationContext, sourceFile)
        }
    }

    /**
     * Create output file for encryption/decryption result.
     * Ensures no conflicts by using unique filenames.
     */
    private fun createOutputFile(inputUri: Uri, encrypt: Boolean): Uri {
        val cr = applicationContext.contentResolver
        val sourceFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, inputUri)
            ?: throw IllegalArgumentException("Could not access source file")
        
        val parent = sourceFile.parentFile ?: throw IllegalStateException("No parent folder")
        
        val outputName = if (encrypt) {
            "${sourceFile.name}.obfs"
        } else {
            sourceFile.name?.removeSuffix(".obfs") ?: "decrypted_${System.currentTimeMillis()}"
        }

        // Handle conflict resolution
        var finalName = outputName
        var counter = 1
        while (parent.findFile(finalName) != null) {
            val base = if (outputName.contains(".")) outputName.substringBeforeLast(".") else outputName
            val ext = if (outputName.contains(".")) ".${outputName.substringAfterLast(".")}" else ""
            finalName = "$base ($counter)$ext"
            counter++
        }

        val mimeType = if (encrypt) "application/octet-stream" else "*/*"
        val newFile = parent.createFile(mimeType, finalName) 
            ?: throw IllegalStateException("Failed to create output file")
            
        return newFile.uri
    }

    /**
     * Parse URIs from JSON string.
     */
    private fun parseUrisFromJson(json: String): List<Uri> {
        return try {
            val gson = com.google.gson.Gson()
            val uriStrings = gson.fromJson(json, Array<String>::class.java)
            uriStrings.map { Uri.parse(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get file name from URI.
     */
    private fun getFileNameFromUri(uri: Uri): String {
        return uri.lastPathSegment ?: "Unknown"
    }

    /**
     * Get file size from URI.
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            val file = File(uri.path ?: return 0L)
            file.length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Securely clear password from memory.
     */
    private fun cleanupPassword(password: CharArray?) {
        password?.fill('0')
    }
}
