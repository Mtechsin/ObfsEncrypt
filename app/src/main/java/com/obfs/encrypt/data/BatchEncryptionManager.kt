package com.obfs.encrypt.data

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.google.gson.Gson
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.security.SecureKeyStore
import com.obfs.encrypt.services.EncryptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BatchEncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore
) {
    private val workManager = WorkManager.getInstance(context)
    private val gson = Gson()

    fun enqueueBatch(
        operation: String,
        uris: List<Uri>,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        deleteOriginal: Boolean = false,
        enableIntegrity: Boolean = false
    ): String {
        // Initialize KeyStore if not already
        if (!secureKeyStore.isInitialized()) {
            secureKeyStore.initialize()
        }

        // Encrypt password securely
        val encryptedPassword = secureKeyStore.encryptPassword(password)
            ?: throw SecurityException("Failed to encrypt password for batch operation")

        // Save encrypted password to a temporary file
        val tempPasswordFile = File(context.cacheDir, "obfs_batch_${System.currentTimeMillis()}.bin")
        tempPasswordFile.writeText(gson.toJson(encryptedPassword))

        // Fixed HIGH-06: WorkManager Data is capped at ~10KB. A JSON list of
        // content URIs easily exceeds that for large batches / long URIs and
        // throws IllegalStateException at enqueue time. Spill the URI list to
        // a cache file and pass only the path (same pattern as the password).
        val urisFile = File(context.cacheDir, "obfs_uris_${System.currentTimeMillis()}.json")
        urisFile.writeText(gson.toJson(uris.map { it.toString() }))

        val inputData = workDataOf(
            EncryptionWorker.KEY_OPERATION to operation,
            // Keep inline copy for small batches (back-compat); worker prefers file.
            EncryptionWorker.KEY_FILE_URIS to "",
            EncryptionWorker.KEY_FILE_URIS_FILE to urisFile.absolutePath,
            EncryptionWorker.KEY_PASSWORD_FILE to tempPasswordFile.absolutePath,
            EncryptionWorker.KEY_METHOD to method.name,
            EncryptionWorker.KEY_DELETE_ORIGINAL to deleteOriginal,
            EncryptionWorker.KEY_ENABLE_INTEGRITY to enableIntegrity
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<EncryptionWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("batch_operation")
            .build()

        workManager.enqueueUniqueWork(
            "batch_${System.currentTimeMillis()}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
        
        return workRequest.id.toString()
    }

    fun getWorkInfoById(id: String): Flow<WorkInfo?> {
        return workManager.getWorkInfoByIdFlow(java.util.UUID.fromString(id))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag("batch_operation")
    }

    fun pruneWork() {
        workManager.pruneWork()
    }
}
