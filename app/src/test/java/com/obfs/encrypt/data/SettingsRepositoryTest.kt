package com.obfs.encrypt.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SettingsRepository.
 *
 * Tests verify:
 * - Default values for all settings
 * - Setting updates
 * - DataStore integration
 */
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        
        // Mock context.dataStore property
        every { context.dataStore } returns dataStore
    }

    // region: Secure Delete Tests

    @Test
    fun secureDeleteOriginals_defaultValue_isTrue() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        repository = SettingsRepository(context)

        val result = repository.secureDeleteOriginals.first()
        assertTrue(result)
    }

    @Test
    fun secureDeleteOriginals_whenSetToFalse_returnsFalse() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.SECURE_DELETE_KEY] } returns false
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.secureDeleteOriginals.first()
        assertFalse(result)
    }

    // endregion

    // region: Output Directory Tests

    @Test
    fun outputDirectoryUri_defaultValue_isNull() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        repository = SettingsRepository(context)

        val result = repository.outputDirectoryUri.first()
        assertNull(result)
    }

    @Test
    fun outputDirectoryUri_whenSet_returnsUri() = runTest {
        val testUri = "content://test/uri"
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.OUTPUT_DIRECTORY_URI_KEY] } returns testUri
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.outputDirectoryUri.first()
        assertEquals(testUri, result)
    }

    // endregion

    // region: Theme Mode Tests

    @Test
    fun themeMode_defaultValue_isSystem() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        repository = SettingsRepository(context)

        val result = repository.themeMode.first()
        assertEquals(com.obfs.encrypt.ui.theme.ThemeMode.SYSTEM, result)
    }

    @Test
    fun themeMode_whenSetToLight_returnsLight() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.THEME_MODE_KEY] } returns "LIGHT"
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.themeMode.first()
        assertEquals(com.obfs.encrypt.ui.theme.ThemeMode.LIGHT, result)
    }

    @Test
    fun themeMode_whenSetToDark_returnsDark() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.THEME_MODE_KEY] } returns "DARK"
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.themeMode.first()
        assertEquals(com.obfs.encrypt.ui.theme.ThemeMode.DARK, result)
    }

    @Test
    fun themeMode_whenSetToInvalidValue_returnsSystem() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.THEME_MODE_KEY] } returns "INVALID"
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.themeMode.first()
        assertEquals(com.obfs.encrypt.ui.theme.ThemeMode.SYSTEM, result)
    }

    // endregion

    // region: Dynamic Color Tests

    @Test
    fun dynamicColor_defaultValue_isFalse() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        repository = SettingsRepository(context)

        val result = repository.dynamicColor.first()
        assertFalse(result)
    }

    @Test
    fun dynamicColor_whenSetToTrue_returnsTrue() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.DYNAMIC_COLOR_KEY] } returns true
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.dynamicColor.first()
        assertTrue(result)
    }

    // endregion

    // region: Quick Access Expanded Tests

    @Test
    fun quickAccessExpanded_defaultValue_isTrue() = runTest {
        every { dataStore.data } returns flowOf(emptyPreferences())

        repository = SettingsRepository(context)

        val result = repository.quickAccessExpanded.first()
        assertTrue(result)
    }

    @Test
    fun quickAccessExpanded_whenSetToFalse_returnsFalse() = runTest {
        val prefs = mockk<Preferences>()
        every { prefs[SettingsRepository.QUICK_ACCESS_EXPANDED_KEY] } returns false
        every { dataStore.data } returns flowOf(prefs)

        repository = SettingsRepository(context)

        val result = repository.quickAccessExpanded.first()
        assertFalse(result)
    }

    // endregion

    // region: Preference Keys Tests

    @Test
    fun preferenceKeys_areCorrectlyDefined() {
        assertEquals(
            "secure_delete_originals",
            SettingsRepository.SECURE_DELETE_KEY.name
        )
        assertEquals(
            "output_directory_uri",
            SettingsRepository.OUTPUT_DIRECTORY_URI_KEY.name
        )
        assertEquals(
            "theme_mode",
            SettingsRepository.THEME_MODE_KEY.name
        )
        assertEquals(
            "dynamic_color",
            SettingsRepository.DYNAMIC_COLOR_KEY.name
        )
        assertEquals(
            "quick_access_expanded",
            SettingsRepository.QUICK_ACCESS_EXPANDED_KEY.name
        )
    }

    // endregion

    // Helper function to create empty preferences
    private fun emptyPreferences(): Preferences {
        return emptyPreferences()
    }
}
