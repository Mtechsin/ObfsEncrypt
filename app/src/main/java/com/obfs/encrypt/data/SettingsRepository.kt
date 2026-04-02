package com.obfs.encrypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.obfs.encrypt.ui.theme.AppTheme
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
        val APP_THEME_KEY = stringPreferencesKey("app_theme")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        val QUICK_ACCESS_EXPANDED_KEY = booleanPreferencesKey("quick_access_expanded")
        val APP_LOCK_ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT_KEY = longPreferencesKey("app_lock_timeout")
        val APP_LOCK_REQUIRE_PASSWORD_KEY = booleanPreferencesKey("app_lock_require_password")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val FILE_SORT_ORDER_KEY = stringPreferencesKey("file_sort_order")
        val FILE_SORT_ASCENDING_KEY = booleanPreferencesKey("file_sort_ascending")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
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
     * Flow of dynamic color setting (Material You).
     * Default: false (use default app colors)
     */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: false
    }

    /**
     * Flow of app theme color.
     * Default: DEFAULT (Blue/Lemon)
     */
    val appTheme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeStr = preferences[APP_THEME_KEY]
        when (themeStr) {
            "RED" -> AppTheme.RED
            "GREEN" -> AppTheme.GREEN
            "ORANGE" -> AppTheme.ORANGE
            "NAVY" -> AppTheme.NAVY
            "AMOLED" -> AppTheme.DEFAULT  // Backward compat: AMOLED is now a toggle, not a theme
            else -> AppTheme.DEFAULT
        }
    }

    /**
     * Flow of AMOLED mode setting.
     * When enabled + dark mode, backgrounds become pure black for OLED power savings.
     * Default: false (or true if user had AMOLED theme selected before migration)
     */
    val amoledMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        // If user explicitly set amoled_mode, use that
        // Otherwise, check if they had the old AMOLED theme selected (migration)
        preferences[AMOLED_MODE_KEY] ?: (preferences[APP_THEME_KEY] == "AMOLED")
    }

    /**
     * Flow of quick access expanded state.
     * Default: true (expanded)
     */
    val quickAccessExpanded: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[QUICK_ACCESS_EXPANDED_KEY] ?: true
    }

    /**
     * Flow of app lock enabled setting.
     * Default: false (app lock disabled)
     */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED_KEY] ?: false
    }

    /**
     * Flow of app lock timeout (milliseconds).
     * Default: 0 (lock immediately when app is backgrounded)
     */
    val appLockTimeout: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_TIMEOUT_KEY] ?: 0L
    }

    /**
     * Flow of app lock requiring password.
     * Default: true (password required for unlock)
     */
    val appLockRequirePassword: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_REQUIRE_PASSWORD_KEY] ?: true
    }

    /**
     * Flow of language setting.
     * Default: "system" (follow system language)
     */
    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "system"
    }

    /**
     * Flow of file sort order setting.
     * Default: NAME
     */
    val fileSortOrder: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FILE_SORT_ORDER_KEY] ?: "NAME"
    }

    /**
     * Flow of file sort ascending setting.
     * Default: true
     */
    val fileSortAscending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FILE_SORT_ASCENDING_KEY] ?: true
    }

    /**
     * Flow of onboarding completed status.
     * Default: false (onboarding not completed)
     */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    /**
     * Update file sort order setting.
     */
    suspend fun setFileSortOrder(sortOrder: String) {
        context.dataStore.edit { preferences ->
            preferences[FILE_SORT_ORDER_KEY] = sortOrder
        }
    }

    /**
     * Update file sort ascending setting.
     */
    suspend fun setFileSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FILE_SORT_ASCENDING_KEY] = ascending
        }
    }

    /**
     * Mark onboarding as completed.
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
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

    /**
     * Update dynamic color setting.
     */
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    /**
     * Update AMOLED mode setting.
     */
    suspend fun setAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AMOLED_MODE_KEY] = enabled
        }
    }

    /**
     * Update app theme color.
     */
    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME_KEY] = theme.name
        }
    }

    /**
     * Update quick access expanded state.
     */
    suspend fun setQuickAccessExpanded(expanded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_ACCESS_EXPANDED_KEY] = expanded
        }
    }

    /**
     * Update app lock enabled setting.
     */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED_KEY] = enabled
        }
    }

    /**
     * Update app lock timeout (milliseconds).
     */
    suspend fun setAppLockTimeout(timeout: Long) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_TIMEOUT_KEY] = timeout
        }
    }

    /**
     * Update app lock requiring password.
     */
    suspend fun setAppLockRequirePassword(required: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_REQUIRE_PASSWORD_KEY] = required
        }
    }

    /**
     * Update language setting.
     * @param language Language code: "en", "ar", or "system"
     */
    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }
}
