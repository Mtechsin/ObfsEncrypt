package com.obfs.encrypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.obfs.encrypt.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create DataStore instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository to manage app settings using DataStore.
 * Persists user preferences across app restarts.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val SECURE_DELETE_KEY = booleanPreferencesKey("secure_delete_originals")
        val OUTPUT_DIRECTORY_URI_KEY = stringPreferencesKey("output_directory_uri")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    /**
     * Flow of secure delete originals setting.
     * Default: true (secure delete is enabled)
     */
    val secureDeleteOriginals: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SECURE_DELETE_KEY] ?: true
    }

    /**
     * Flow of output directory URI.
     * Default: null (use source directory)
     */
    val outputDirectoryUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[OUTPUT_DIRECTORY_URI_KEY]
    }

    /**
     * Flow of theme mode.
     * Default: SYSTEM
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val themeStr = preferences[THEME_MODE_KEY]
        when (themeStr) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /**
     * Update secure delete originals setting.
     */
    suspend fun setSecureDeleteOriginals(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SECURE_DELETE_KEY] = enabled
        }
    }

    /**
     * Update output directory URI.
     * Pass null to clear and use source directory.
     */
    suspend fun setOutputDirectoryUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[OUTPUT_DIRECTORY_URI_KEY] = uri
            } else {
                preferences.remove(OUTPUT_DIRECTORY_URI_KEY)
            }
        }
    }

    /**
     * Update theme mode.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
}
