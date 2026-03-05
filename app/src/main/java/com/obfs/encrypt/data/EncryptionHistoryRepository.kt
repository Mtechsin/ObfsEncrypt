package com.obfs.encrypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.obfs.encrypt.crypto.EncryptionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create DataStore instance
val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "encryption_history")

/**
 * Repository to manage encryption/decryption history.
 * Persists operation history across app restarts using DataStore.
 */
@Singleton
class EncryptionHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HISTORY_KEY = stringPreferencesKey("encryption_history_json")
        const val MAX_HISTORY_ITEMS = 50
    }

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Flow of encryption history items.
     */
    val historyItems: Flow<List<EncryptionHistoryItem>> = context.historyDataStore.data.map { preferences ->
        val json = preferences[HISTORY_KEY] ?: return@map emptyList()
        try {
            val items = gson.fromJson(json, Array<EncryptionHistoryItem>::class.java)
            items.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a new encryption/decryption operation to history.
     */
    suspend fun addHistoryItem(item: EncryptionHistoryItem) {
        context.historyDataStore.edit { preferences ->
            val currentJson = preferences[HISTORY_KEY]
            val currentList = if (currentJson != null) {
                try {
                    gson.fromJson(currentJson, Array<EncryptionHistoryItem>::class.java).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            // Add new item at the beginning
            currentList.add(0, item)

            // Limit history size
            while (currentList.size > MAX_HISTORY_ITEMS) {
                currentList.removeAt(currentList.size - 1)
            }

            preferences[HISTORY_KEY] = gson.toJson(currentList)
        }
    }

    /**
     * Clear all history.
     */
    suspend fun clearHistory() {
        context.historyDataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }
    }

    /**
     * Remove a specific item from history.
     */
    suspend fun removeItem(itemId: String) {
        context.historyDataStore.edit { preferences ->
            val currentJson = preferences[HISTORY_KEY] ?: return@edit
            try {
                val currentList = gson.fromJson(currentJson, Array<EncryptionHistoryItem>::class.java).toMutableList()
                currentList.removeAll { it.id == itemId }
                preferences[HISTORY_KEY] = gson.toJson(currentList)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Get recent items (limited count).
     */
    suspend fun getRecentItems(count: Int = 10): List<EncryptionHistoryItem> {
        return historyItems.first().take(count)
    }

    private suspend fun Flow<List<EncryptionHistoryItem>>.first(): List<EncryptionHistoryItem> {
        var result: List<EncryptionHistoryItem>? = null
        this.collect { result = it }
        return result ?: emptyList()
    }
}

/**
 * Represents a single encryption/decryption operation in history.
 */
data class EncryptionHistoryItem(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("fileName") val fileName: String,
    @SerializedName("fileSize") val fileSize: Long,
    @SerializedName("formattedSize") val formattedSize: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("formattedDate") val formattedDate: String,
    @SerializedName("operationType") val operationType: OperationType,
    @SerializedName("encryptionMethod") val encryptionMethod: EncryptionMethod? = null,
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("outputPath") val outputPath: String? = null,
    @SerializedName("secureDelete") val secureDelete: Boolean = false
) {
    enum class OperationType {
        @SerializedName("encrypt") ENCRYPT,
        @SerializedName("decrypt") DECRYPT
    }
}

/**
 * Helper function to format file size in human-readable format.
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * Helper function to format timestamp to readable date string.
 */
fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Create an EncryptionHistoryItem from file operation details.
 */
fun createHistoryItem(
    fileName: String,
    fileSize: Long,
    operationType: EncryptionHistoryItem.OperationType,
    encryptionMethod: EncryptionMethod? = null,
    success: Boolean = true,
    errorMessage: String? = null,
    outputPath: String? = null,
    secureDelete: Boolean = false
): EncryptionHistoryItem {
    return EncryptionHistoryItem(
        fileName = fileName,
        fileSize = fileSize,
        formattedSize = formatFileSize(fileSize),
        formattedDate = formatTimestamp(System.currentTimeMillis()),
        operationType = operationType,
        encryptionMethod = encryptionMethod,
        success = success,
        errorMessage = errorMessage,
        outputPath = outputPath,
        secureDelete = secureDelete
    )
}
