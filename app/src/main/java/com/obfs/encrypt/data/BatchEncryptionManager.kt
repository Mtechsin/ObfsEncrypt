package com.obfs.encrypt.data

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.google.gson.Gson
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.services.EncryptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BatchEncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
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
        val urisJson = gson.toJson(uris.map { it.toString() })
        
        val inputData = workDataOf(
            EncryptionWorker.KEY_OPERATION to operation,
            EncryptionWorker.KEY_FILE_URIS to urisJson,
            EncryptionWorker.KEY_PASSWORD to String(password),
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
