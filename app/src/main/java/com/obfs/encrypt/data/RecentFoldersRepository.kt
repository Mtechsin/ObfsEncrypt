package com.obfs.encrypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.recentFoldersDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_folders")

/**
 * Repository to manage recently visited folders.
 * Tracks the last N folders visited by the user for quick access.
 */
@Singleton
class RecentFoldersRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val RECENT_FOLDERS_KEY = stringSetPreferencesKey("recent_folders")
        const val MAX_RECENT_FOLDERS = 15
    }

    /**
     * Flow of recent folder paths (ordered by most recent first).
     * Returns a list of folder paths.
     */
    val recentFolders: Flow<List<String>> = context.recentFoldersDataStore.data.map { preferences ->
        val folderSet = preferences[RECENT_FOLDERS_KEY] ?: emptySet()
        // Convert to list and maintain order (most recent first)
        folderSet.toList()
    }

    /**
     * Add a folder to recent folders.
     * If the folder already exists, it's moved to the top.
     * The list is limited to MAX_RECENT_FOLDERS entries.
     */
    suspend fun addRecentFolder(path: String) {
        context.recentFoldersDataStore.edit { preferences ->
            val currentFavorites = preferences[RECENT_FOLDERS_KEY] ?: emptySet()
            // Remove if already exists (to re-add at the end as most recent)
            val updatedList = (currentFavorites - path).toMutableList()
            // Add to end (most recent)
            updatedList.add(path)
            // Keep only the most recent MAX_RECENT_FOLDERS
            val trimmedList = updatedList.takeLast(MAX_RECENT_FOLDERS)
            preferences[RECENT_FOLDERS_KEY] = trimmedList.toSet()
        }
    }

    /**
     * Remove a specific folder from recent folders.
     */
    suspend fun removeRecentFolder(path: String) {
        context.recentFoldersDataStore.edit { preferences ->
            val currentFavorites = preferences[RECENT_FOLDERS_KEY] ?: emptySet()
            preferences[RECENT_FOLDERS_KEY] = currentFavorites - path
        }
    }

    /**
     * Clear all recent folders.
     */
    suspend fun clearRecentFolders() {
        context.recentFoldersDataStore.edit { preferences ->
            preferences.remove(RECENT_FOLDERS_KEY)
        }
    }

    /**
     * Check if a folder is in recent folders.
     */
    suspend fun isRecentFolder(path: String): Boolean {
        var result = false
        context.recentFoldersDataStore.data.collect { preferences ->
            val recentFolders = preferences[RECENT_FOLDERS_KEY] ?: emptySet()
            result = path in recentFolders
        }
        return result
    }
}
