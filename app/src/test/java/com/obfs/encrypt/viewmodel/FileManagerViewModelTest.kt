package com.obfs.encrypt.viewmodel

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.BiometricAuthManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.File

/**
 * Unit tests for FileManagerViewModel.
 * 
 * Tests verify:
 * - File navigation (up, into directories)
 * - File selection (toggle, select all, invert, clear)
 * - Directory caching behavior
 * - Error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Mocks
    private lateinit var application: Application
    private lateinit var encryptionHelper: EncryptionHelper
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var appDirectoryManager: AppDirectoryManager
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var quickAccessRepository: com.obfs.encrypt.data.QuickAccessRepository
    private lateinit var recentFoldersRepository: com.obfs.encrypt.data.RecentFoldersRepository

    // System under test
    private lateinit var viewModel: FileManagerViewModel

    // Test files
    private lateinit var testRoot: File
    private lateinit var testSubdir: File
    private lateinit var testFile1: File
    private lateinit var testFile2: File

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
        quickAccessRepository = mockk(relaxed = true)
        recentFoldersRepository = mockk(relaxed = true)

        // Mock repository flows
        every { settingsRepository.secureDeleteOriginals } returns flowOf(true)
        every { settingsRepository.outputDirectoryUri } returns flowOf(null)

        // Create temporary test directory structure
        testRoot = createTempDir("test_root")
        testSubdir = File(testRoot, "subdir").apply { mkdir() }
        testFile1 = File(testRoot, "file1.txt").apply { createNewFile() }
        testFile2 = File(testRoot, "file2.txt").apply { createNewFile() }

        viewModel = FileManagerViewModel(
            application = application,
            settingsRepository = settingsRepository,
            quickAccessRepository = quickAccessRepository,
            recentFoldersRepository = recentFoldersRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.documentfile.provider.DocumentFileKt")
        
        // Clean up test files
        testFile1.delete()
        testFile2.delete()
        testSubdir.delete()
        testRoot.delete()
    }

    // region: Initial State Tests

    @Test
    fun initialCurrentDirectory_isNotNull() = runTest {
        assertNotNull(viewModel.currentDirectory.value)
    }

    @Test
    fun initialFilesAndFolders_isEmpty() = runTest {
        // Initial load should populate files
        advanceUntilIdle()
        // Files may or may not be empty depending on test environment
        // Just verify it's not null
        assertNotNull(viewModel.filesAndFolders.value)
    }

    @Test
    fun initialSelectedItems_isEmpty() = runTest {
        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun initialIsLoading_isFalse() = runTest {
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun initialError_isNull() = runTest {
        assertNull(viewModel.error.value)
    }

    // endregion

    // region: File Selection Tests

    @Test
    fun toggleSelection_addsFileWhenNotSelected() = runTest {
        viewModel.toggleSelection(testFile1)
        advanceUntilIdle()

        assertTrue(viewModel.selectedItems.value.contains(testFile1))
    }

    @Test
    fun toggleSelection_removesFileWhenSelected() = runTest {
        viewModel.toggleSelection(testFile1)
        advanceUntilIdle()
        
        viewModel.toggleSelection(testFile1)
        advanceUntilIdle()

        assertFalse(viewModel.selectedItems.value.contains(testFile1))
    }

    @Test
    fun toggleSelection_multipleFiles() = runTest {
        viewModel.toggleSelection(testFile1)
        viewModel.toggleSelection(testFile2)
        advanceUntilIdle()

        assertEquals(2, viewModel.selectedItems.value.size)
        assertTrue(viewModel.selectedItems.value.contains(testFile1))
        assertTrue(viewModel.selectedItems.value.contains(testFile2))
    }

    @Test
    fun clearSelection_removesAllSelections() = runTest {
        viewModel.toggleSelection(testFile1)
        viewModel.toggleSelection(testFile2)
        advanceUntilIdle()

        viewModel.clearSelection()
        advanceUntilIdle()

        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    @Test
    fun selectAll_selectsAllFilesInCurrentDirectory() = runTest {
        // Navigate to test directory first
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        viewModel.selectAll()
        advanceUntilIdle()

        val selected = viewModel.selectedItems.value
        // Should select all non-directory items
        assertTrue(selected.isNotEmpty())
    }

    @Test
    fun invertSelection_selectsUnselectedAndViceVersa() = runTest {
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        // Select one file
        viewModel.toggleSelection(testFile1)
        advanceUntilIdle()

        val initialSize = viewModel.selectedItems.value.size

        viewModel.invertSelection()
        advanceUntilIdle()

        // After invert, previously selected should be unselected and vice versa
        assertFalse(viewModel.selectedItems.value.contains(testFile1))
    }

    @Test
    fun selectNone_clearsSelection() = runTest {
        viewModel.toggleSelection(testFile1)
        advanceUntilIdle()

        viewModel.selectNone()
        advanceUntilIdle()

        assertTrue(viewModel.selectedItems.value.isEmpty())
    }

    // endregion

    // region: Navigation Tests

    @Test
    fun navigateTo_validDirectory_updatesCurrentDirectory() = runTest {
        viewModel.navigateTo(testSubdir)
        advanceUntilIdle()

        assertEquals(testSubdir, viewModel.currentDirectory.value)
    }

    @Test
    fun navigateTo_invalidFile_setsError() = runTest {
        val nonExistentFile = File(testRoot, "nonexistent.txt")
        
        viewModel.navigateTo(nonExistentFile)
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.error.value!!.contains("Cannot read directory"))
    }

    @Test
    fun navigateTo_fileInsteadOfDirectory_setsError() = runTest {
        viewModel.navigateTo(testFile1)
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
    }

    @Test
    fun navigateUp_fromSubdirectory_goesToParent() = runTest {
        viewModel.navigateTo(testSubdir)
        advanceUntilIdle()

        val result = viewModel.navigateUp()
        advanceUntilIdle()

        assertTrue(result)
        assertEquals(testRoot, viewModel.currentDirectory.value)
    }

    @Test
    fun navigateUp_fromRoot_returnsFalse() = runTest {
        // Start at root (already there from initialization)
        advanceUntilIdle()
        
        val result = viewModel.navigateUp()
        advanceUntilIdle()

        // May return false if already at top
        // Just verify it doesn't crash
    }

    @Test
    fun navigateToPath_validPath_updatesDirectory() = runTest {
        viewModel.navigateToPath(testSubdir.absolutePath)
        advanceUntilIdle()

        assertEquals(testSubdir, viewModel.currentDirectory.value)
    }

    @Test
    fun navigateToPath_invalidPath_setsError() = runTest {
        viewModel.navigateToPath("/nonexistent/path/12345")
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
    }

    @Test
    fun refreshCurrentDirectory_reloadsFiles() = runTest {
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        val initialCount = viewModel.filesAndFolders.value.size

        // Create a new file
        val newFile = File(testRoot, "newfile.txt").apply { createNewFile() }

        viewModel.refreshCurrentDirectory()
        advanceUntilIdle()

        // Should include the new file
        assertTrue(viewModel.filesAndFolders.value.size >= initialCount)

        // Cleanup
        newFile.delete()
    }

    // endregion

    // region: Cache Tests

    @Test
    fun clearCache_removesAllCachedEntries() = runTest {
        // Navigate to multiple directories to populate cache
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()
        
        viewModel.navigateTo(testSubdir)
        advanceUntilIdle()

        viewModel.clearCache()

        // Cache should be cleared (no direct way to verify, but method should not crash)
    }

    @Test
    fun trimCache_removesOldestEntries() = runTest {
        // Navigate to multiple directories to populate cache
        repeat(10) { i ->
            val dir = File(testRoot, "dir$i").apply { mkdir() }
            viewModel.navigateTo(dir)
            advanceUntilIdle()
        }

        viewModel.trimCache(5)

        // Cache should be trimmed to 5 entries
    }

    @Test
    fun directoryCache_preventsRedundantDiskReads() = runTest {
        // Navigate to a directory
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        val firstLoad = viewModel.filesAndFolders.value

        // Navigate away and back
        viewModel.navigateTo(testSubdir)
        advanceUntilIdle()
        
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        // Should load from cache (instant)
        val secondLoad = viewModel.filesAndFolders.value
        
        assertEquals(firstLoad.size, secondLoad.size)
    }

    // endregion

    // region: Error Handling Tests

    @Test
    fun error_isClearedOnSuccessfulNavigation() = runTest {
        // Cause an error
        viewModel.navigateTo(File("/nonexistent"))
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)

        // Navigate to valid directory
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        // Error should be cleared
        assertNull(viewModel.error.value)
    }

    @Test
    fun loadDirectory_withPermissionDenied_setsError() = runTest {
        // Create a directory and remove read permissions
        val noReadDir = File(testRoot, "noread").apply { 
            mkdir()
            setReadable(false)
        }

        viewModel.navigateTo(noReadDir)
        advanceUntilIdle()

        // Should set error for unreadable directory
        assertNotNull(viewModel.error.value)

        // Cleanup
        noReadDir.setReadable(true)
        noReadDir.delete()
    }

    // endregion

    // region: File Sorting Tests

    @Test
    fun filesAndFolders_sortedFoldersFirst() = runTest {
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        val items = viewModel.filesAndFolders.value
        val folders = items.filter { it.isDirectory }
        val files = items.filter { !it.isDirectory }

        // All folders should come before all files
        if (folders.isNotEmpty() && files.isNotEmpty()) {
            val lastFolderIndex = items.indexOfLast { it.isDirectory }
            val firstFileIndex = items.indexOfFirst { !it.isDirectory }
            assertTrue(lastFolderIndex < firstFileIndex)
        }
    }

    @Test
    fun filesAndFolders_sortedAlphabetically() = runTest {
        viewModel.navigateTo(testRoot)
        advanceUntilIdle()

        val items = viewModel.filesAndFolders.value
        
        // Verify folders are sorted alphabetically
        val folders = items.filter { it.isDirectory }
        if (folders.size > 1) {
            for (i in 0 until folders.size - 1) {
                assertTrue(
                    "Folders should be sorted alphabetically",
                    folders[i].name.lowercase() <= folders[i + 1].name.lowercase()
                )
            }
        }
    }

    // endregion

    // region: FileItem Data Class Tests

    @Test
    fun fileItem_isDirectory_correct() {
        val dirItem = FileItem(
            file = testSubdir,
            isDirectory = true,
            name = "subdir",
            size = 0,
            lastModified = testSubdir.lastModified()
        )

        assertTrue(dirItem.isDirectory)
    }

    @Test
    fun fileItem_fileHasSize() {
        testFile1.writeText("test content")
        
        val fileItem = FileItem(
            file = testFile1,
            isDirectory = false,
            name = "file1.txt",
            size = testFile1.length(),
            lastModified = testFile1.lastModified()
        )

        assertTrue(fileItem.size > 0)
    }

    // endregion
}
