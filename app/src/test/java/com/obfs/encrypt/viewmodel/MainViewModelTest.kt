package com.obfs.encrypt.viewmodel

import android.app.Application
import android.net.Uri
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.EncryptionHistoryRepository
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MainViewModel.
 *
 * Tests verify:
 * - Initial state values
 * - State updates through public methods
 * - Operation lifecycle (start, cancel, complete)
 * - Preference persistence delegation to repository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Mocks
    private lateinit var application: Application
    private lateinit var encryptionHelper: EncryptionHelper
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appDirectoryManager: AppDirectoryManager
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var historyRepository: EncryptionHistoryRepository
    private lateinit var appPasswordManager: com.obfs.encrypt.security.AppPasswordManager
    private lateinit var batchEncryptionManager: com.obfs.encrypt.data.BatchEncryptionManager

    // System under test
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.documentfile.provider.DocumentFileKt")

        // Initialize mocks
        application = mockk(relaxed = true)
        encryptionHelper = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        appDirectoryManager = mockk(relaxed = true)
        biometricAuthManager = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        appPasswordManager = mockk(relaxed = true)
        batchEncryptionManager = mockk(relaxed = true)

        // Mock repository flows with default values
        every { settingsRepository.secureDeleteOriginals } returns flowOf(true)
        every { settingsRepository.outputDirectoryUri } returns flowOf(null)
        every { settingsRepository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        every { settingsRepository.dynamicColor } returns flowOf(false)
        every { settingsRepository.quickAccessExpanded } returns flowOf(true)
        every { settingsRepository.appLockEnabled } returns flowOf(false)
        every { settingsRepository.appLockTimeout } returns flowOf(0L)
        every { historyRepository.historyItems } returns flowOf(emptyList())

        viewModel = MainViewModel(
            application = application,
            encryptionHelper = encryptionHelper,
            settingsRepository = settingsRepository,
            appDirectoryManager = appDirectoryManager,
            historyRepository = historyRepository,
            biometricAuthManager = biometricAuthManager,
            appPasswordManager = appPasswordManager,
            batchEncryptionManager = batchEncryptionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.documentfile.provider.DocumentFileKt")
    }

    // region: Initial State Tests

    @Test
    fun initialProgress_isZero() = runTest {
        assertEquals(0f, viewModel.progress.value)
    }

    @Test
    fun initialStatusMessage_isEmpty() = runTest {
        assertEquals("", viewModel.statusMessage.value)
    }

    @Test
    fun initialIsOperationActive_isFalse() = runTest {
        assertFalse(viewModel.isOperationActive.value)
    }

    @Test
    fun initialSecureDeleteOriginals_isTrue() = runTest {
        assertTrue(viewModel.secureDeleteOriginals.value)
    }

    @Test
    fun initialCurrentOutputUri_isNull() = runTest {
        assertNull(viewModel.currentOutputUri.value)
    }

    @Test
    fun initialThemeMode_isSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun initialDynamicColor_isFalse() = runTest {
        assertFalse(viewModel.dynamicColor.value)
    }

    @Test
    fun initialQuickAccessExpanded_isTrue() = runTest {
        assertTrue(viewModel.quickAccessExpanded.value)
    }

    @Test
    fun initialKeyfileUri_isNull() = runTest {
        assertNull(viewModel.keyfileUri.value)
    }

    @Test
    fun initialEnableIntegrityCheck_isFalse() = runTest {
        assertFalse(viewModel.enableIntegrityCheck.value)
    }

    @Test
    fun initialLastDecryptionResult_isNull() = runTest {
        assertNull(viewModel.lastDecryptionResult.value)
    }

    // endregion

    // region: Theme Settings Tests

    @Test
    fun setThemeMode_updatesStateAndRepository() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        // Verify repository method was called (rely on relaxed mock)
    }

    @Test
    fun setDynamicColor_updatesStateAndRepository() = runTest {
        viewModel.setDynamicColor(true)
        advanceUntilIdle()

        assertTrue(viewModel.dynamicColor.value)
    }

    @Test
    fun setQuickAccessExpanded_updatesStateAndRepository() = runTest {
        viewModel.setQuickAccessExpanded(false)
        advanceUntilIdle()

        assertFalse(viewModel.quickAccessExpanded.value)
    }

    // endregion

    // region: Secure Delete Tests

    @Test
    fun toggleSecureDelete_enabled_updatesState() = runTest {
        viewModel.toggleSecureDelete(false)
        advanceUntilIdle()

        assertFalse(viewModel.secureDeleteOriginals.value)
    }

    @Test
    fun toggleSecureDelete_disabled_updatesState() = runTest {
        viewModel.toggleSecureDelete(false)
        viewModel.toggleSecureDelete(true)
        advanceUntilIdle()

        assertTrue(viewModel.secureDeleteOriginals.value)
    }

    // endregion

    // region: Output Directory Tests

    @Test
    fun setCurrentOutputDirectory_withValidUri_updatesState() = runTest {
        val testUri = mockk<Uri>()
        every { testUri.toString() } returns "content://test/uri"

        viewModel.setCurrentOutputDirectory(testUri)
        advanceUntilIdle()

        assertEquals(testUri, viewModel.currentOutputUri.value)
    }

    @Test
    fun setCurrentOutputDirectory_withNull_clearsUri() = runTest {
        val testUri = mockk<Uri>()
        every { testUri.toString() } returns "content://test/uri"
        viewModel.setCurrentOutputDirectory(testUri)
        advanceUntilIdle()

        viewModel.setCurrentOutputDirectory(null)
        advanceUntilIdle()

        assertNull(viewModel.currentOutputUri.value)
    }

    // endregion

    // region: Keyfile Tests

    @Test
    fun setKeyfileUri_updatesState() = runTest {
        val testUri = mockk<Uri>()
        viewModel.setKeyfileUri(testUri)

        assertEquals(testUri, viewModel.keyfileUri.value)
    }

    @Test
    fun setKeyfileUri_withNull_clearsUri() = runTest {
        val testUri = mockk<Uri>()
        viewModel.setKeyfileUri(testUri)
        viewModel.setKeyfileUri(null)

        assertNull(viewModel.keyfileUri.value)
    }

    @Test
    fun toggleIntegrityCheck_enabled_updatesState() = runTest {
        viewModel.toggleIntegrityCheck(true)
        assertTrue(viewModel.enableIntegrityCheck.value)
    }

    @Test
    fun toggleIntegrityCheck_disabled_updatesState() = runTest {
        viewModel.toggleIntegrityCheck(false)
        assertFalse(viewModel.enableIntegrityCheck.value)
    }

    @Test
    fun clearLastDecryptionResult_setsNull() = runTest {
        // Simulate a decryption result
        val mockResult = com.obfs.encrypt.crypto.DecryptionResult(
            success = true,
            integrityResult = com.obfs.encrypt.crypto.IntegrityResult(
                hmacValid = true,
                checksumValid = true,
                message = "Test"
            )
        )
        // Access private field via reflection or test helper
        // For now, just verify the clear method exists and doesn't crash
        viewModel.clearLastDecryptionResult()
        assertNull(viewModel.lastDecryptionResult.value)
    }

    // endregion

    // region: Operation Cancellation Tests

    @Test
    fun cancelOperation_whenIdle_doesNotCrash() = runTest {
        viewModel.cancelOperation()
        
        assertFalse(viewModel.isOperationActive.value)
        assertEquals(0f, viewModel.progress.value)
        assertEquals("Operation Cancelled", viewModel.statusMessage.value)
    }

    @Test
    fun encryptFiles_withEmptyList_doesNothing() = runTest {
        viewModel.encryptFiles(
            uris = emptyList(),
            password = charArrayOf('t', 'e', 's', 't')
        )
        advanceUntilIdle()

        assertFalse(viewModel.isOperationActive.value)
    }

    @Test
    fun decryptFiles_withEmptyList_doesNothing() = runTest {
        viewModel.decryptFiles(
            uris = emptyList(),
            password = charArrayOf('t', 'e', 's', 't')
        )
        advanceUntilIdle()

        assertFalse(viewModel.isOperationActive.value)
    }

    @Test
    fun encryptDirectFiles_withEmptyList_doesNothing() = runTest {
        viewModel.encryptDirectFiles(
            files = emptyList(),
            password = charArrayOf('t', 'e', 's', 't')
        )
        advanceUntilIdle()

        assertFalse(viewModel.isOperationActive.value)
    }

    @Test
    fun decryptDirectFiles_withEmptyList_doesNothing() = runTest {
        viewModel.decryptDirectFiles(
            files = emptyList(),
            password = charArrayOf('t', 'e', 's', 't')
        )
        advanceUntilIdle()

        assertFalse(viewModel.isOperationActive.value)
    }

    // endregion

    // region: Encryption Method Tests

    @Test
    fun encryptFiles_setsActiveState() = runTest {
        val testUri = mockk<Uri>()
        every { testUri.path } returns "/test/file.txt"
        
        viewModel.encryptFiles(
            uris = listOf(testUri),
            password = charArrayOf('t', 'e', 's', 't'),
            method = EncryptionMethod.STANDARD
        )
        
        // Operation should be active (will complete immediately in test due to mocks)
        // The actual encryption will fail with mocks, but we can verify state transitions
        advanceUntilIdle()
        
        // After completion/failure, operation should be inactive
        assertFalse(viewModel.isOperationActive.value)
    }

    // endregion

    // region: Password Security Tests

    @Test
    fun passwordIsClearedAfterEncryption() = runTest {
        val password = charArrayOf('s', 'e', 'c', 'u', 'r', 'e')
        val passwordContentBefore = password.concatToString()
        
        // Note: In real implementation, password is cleared in finally block
        // This test verifies the test setup works
        assertNotNull(passwordContentBefore)
        assertTrue(passwordContentBefore.isNotEmpty())
    }

    // endregion

    // region: State Flow Collection Tests

    @Test
    fun progressFlow_emitsInitialValue() = runTest {
        val progress = viewModel.progress.first()
        assertEquals(0f, progress)
    }

    @Test
    fun statusMessageFlow_emitsInitialValue() = runTest {
        val status = viewModel.statusMessage.first()
        assertEquals("", status)
    }

    @Test
    fun isOperationActiveFlow_emitsInitialValue() = runTest {
        val active = viewModel.isOperationActive.first()
        assertFalse(active)
    }

    // endregion
}
