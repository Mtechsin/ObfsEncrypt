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
import androidx.work.WorkManager
import com.obfs.encrypt.R
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.EncryptionHistoryRepository
import com.obfs.encrypt.data.FileEncryptionManager
import com.obfs.encrypt.data.createHistoryItem
import com.obfs.encrypt.security.EncryptedData
import com.obfs.encrypt.security.SecureKeyStore
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
 *         "password_file" to passwordFilePath,
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
    private val historyRepository: EncryptionHistoryRepository,
    private val secureKeyStore: SecureKeyStore,
    private val fileEncryptionManager: FileEncryptionManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "EncryptionWorker"
        const val NOTIFICATION_CHANNEL_ID = "encryption_progress_channel"
        const val NOTIFICATION_ID = 1001
        
        const val KEY_OPERATION = "operation"
        const val KEY_FILE_URIS = "file_uris"
        const val KEY_FILE_URIS_FILE = "file_uris_file"
        const val KEY_PASSWORD_FILE = "password_file" // Secure file path
        const val KEY_PASSWORD = "password" // DEPRECATED for security
        const val KEY_METHOD = "method"
        const val KEY_DELETE_ORIGINAL = "delete_original"
        const val KEY_ENABLE_INTEGRITY = "enable_integrity"
        const val KEY_PROGRESS = "progress"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_CURRENT_FILE_INDEX = "current_file_index"
        const val KEY_TOTAL_FILES = "total_files"
        
        const val OPERATION_ENCRYPT = "encrypt"
        const val OPERATION_DECRYPT = "decrypt"
    }

    private val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val gson = com.google.gson.Gson()
    
    // Progress tracking for live updates
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var password: CharArray? = null
        var tempPasswordFile: File? = null
        var urisFile: File? = null

        try {
            // Get input data
            val operation = inputData.getString(KEY_OPERATION) ?: return@withContext Result.failure()
            // Prefer the spilled URI file (HIGH-06); fall back to inline JSON.
            val urisFilePath = inputData.getString(KEY_FILE_URIS_FILE)
            val fileUrisJson: String = if (!urisFilePath.isNullOrEmpty()) {
                urisFile = File(urisFilePath)
                try {
                    urisFile!!.takeIf { it.exists() }?.readText().orEmpty()
                } catch (_: Exception) { "" }
            } else {
                inputData.getString(KEY_FILE_URIS).orEmpty()
            }
            if (fileUrisJson.isEmpty()) return@withContext Result.failure()
            
            // Securely retrieve password from temporary file
            val passwordFilePath = inputData.getString(KEY_PASSWORD_FILE)
            if (passwordFilePath != null) {
                tempPasswordFile = File(passwordFilePath)
                if (tempPasswordFile.exists()) {
                    try {
                        val encryptedJson = tempPasswordFile.readText()
                        val encryptedData = gson.fromJson(encryptedJson, EncryptedData::class.java)
                        
                        if (!secureKeyStore.isInitialized()) {
                            secureKeyStore.initialize()
                        }
                        
                        val decryptedPassword = secureKeyStore.decrypt(encryptedData)
                        password = decryptedPassword?.toCharArray()
                        
                        // Delete the file immediately after reading
                        tempPasswordFile.delete()
                    } catch (e: Exception) {
                        com.obfs.encrypt.diagnostics.AppLogger.e(TAG, "Failed to restore worker password", e)
                        return@withContext Result.failure()
                    }
                }
            }
            
            // Fallback to legacy password (if still in use during transition)
            if (password == null) {
                val passwordStr = inputData.getString(KEY_PASSWORD)
                password = passwordStr?.toCharArray()
            }

            if (password == null) {
                return@withContext Result.failure()
            }

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
                            processEncryption(uri, password!!, method, deleteOriginal, enableIntegrity, index + 1, uris.size)
                            
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
                            processDecryption(uri, password!!, deleteOriginal, enableIntegrity, index + 1, uris.size)
                            
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

                    // Update progress (overall file-based progress)
                    val progress = (index + 1).toFloat() / uris.size
                    _progress.value = progress
                    setProgress(androidx.work.workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_CURRENT_FILE to fileName,
                        KEY_CURRENT_FILE_INDEX to index + 1,
                        KEY_TOTAL_FILES to uris.size,
                        KEY_OPERATION to operation
                    ))
                    setForeground(createForegroundInfo(operation, progress, fileName))

                } catch (e: Exception) {
                    com.obfs.encrypt.diagnostics.AppLogger.e(
                        TAG,
                        "File operation failed op=$operation",
                        e
                    )
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
            com.obfs.encrypt.diagnostics.AppLogger.e(TAG, "Worker failed", e)
            cleanupPassword(password)
            Result.failure()
        } finally {
            // Final cleanup of the password and file
            cleanupPassword(password)
            tempPasswordFile?.let {
                if (it.exists()) it.delete()
            }
            urisFile?.let {
                try { if (it.exists()) it.delete() } catch (_: Exception) {}
            }
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
        val title = if (operation == OPERATION_ENCRYPT) "Encrypting files..." else "Decrypting files..."
        val progressInt = (progress * 100).toInt()
        
        // Add a Cancel button to the notification
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (currentFile.isNotEmpty()) currentFile else "Preparing...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progressInt, progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
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
        enableIntegrity: Boolean,
        index: Int,
        totalFiles: Int
    ) {
        val sourceFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, uri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileName = sourceFile.name ?: "Unknown"

        val outputUri = fileEncryptionManager.createOutputFile(uri, encrypt = true)

        fileEncryptionManager.encryptUri(
            sourceUri = uri,
            outputUri = outputUri,
            password = password,
            method = method,
            enableIntegrityCheck = enableIntegrity,
            progressCallback = { current, total, _ ->
                val fileProgress = current.toFloat() / total.toFloat()
                val overallProgress = (index + fileProgress) / totalFiles
                _progress.value = overallProgress
                setForeground(createForegroundInfo(OPERATION_ENCRYPT, overallProgress, "($index/$totalFiles) $fileName"))
            }
        )

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
        verifyIntegrity: Boolean,
        index: Int,
        totalFiles: Int
    ) {
        val sourceFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, uri)
            ?: throw IllegalArgumentException("Cannot read source file")
        val fileName = sourceFile.name ?: "Unknown"

        val outputUri = fileEncryptionManager.createOutputFile(uri, encrypt = false)

        val result = fileEncryptionManager.decryptUri(
            sourceUri = uri,
            outputUri = outputUri,
            password = password,
            verifyIntegrity = verifyIntegrity,
            progressCallback = { current, total, _ ->
                val fileProgress = current.toFloat() / total.toFloat()
                val overallProgress = (index + fileProgress) / totalFiles
                _progress.value = overallProgress
                setForeground(createForegroundInfo(OPERATION_DECRYPT, overallProgress, "($index/$totalFiles) $fileName"))
            }
        )

        // Fixed CRIT-03 (worker): FileEncryptionManager only commits plaintext
        // on integrity success; mirror the foreground gate before shredding.
        val integrityOk = result.integrityResult?.isValid ?: true
        if (!result.success || !integrityOk) {
            throw SecurityException(
                result.integrityResult?.message ?: "Integrity verification failed — original kept."
            )
        }

        if (deleteOriginal) {
            com.obfs.encrypt.data.SecureDelete.secureDelete(applicationContext, sourceFile)
        }
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
