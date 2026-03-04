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

val Context.quickAccessDataStore: DataStore<Preferences> by preferencesDataStore(name = "quick_access")

@Singleton
class QuickAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val FAVORITE_PATHS_KEY = stringSetPreferencesKey("favorite_paths")
    }

    val favoritePaths: Flow<Set<String>> = context.quickAccessDataStore.data.map { preferences ->
        preferences[FAVORITE_PATHS_KEY] ?: emptySet()
    }

    suspend fun addFavorite(path: String) {
        context.quickAccessDataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITE_PATHS_KEY] ?: emptySet()
            preferences[FAVORITE_PATHS_KEY] = currentFavorites + path
        }
    }

    suspend fun removeFavorite(path: String) {
        context.quickAccessDataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITE_PATHS_KEY] ?: emptySet()
            preferences[FAVORITE_PATHS_KEY] = currentFavorites - path
        }
    }

    suspend fun toggleFavorite(path: String) {
        context.quickAccessDataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITE_PATHS_KEY] ?: emptySet()
            preferences[FAVORITE_PATHS_KEY] = if (path in currentFavorites) {
                currentFavorites - path
            } else {
                currentFavorites + path
            }
        }
    }

    suspend fun isFavorite(path: String): Boolean {
        var result = false
        context.quickAccessDataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITE_PATHS_KEY] ?: emptySet()
            result = path in currentFavorites
        }
        return result
    }
}
