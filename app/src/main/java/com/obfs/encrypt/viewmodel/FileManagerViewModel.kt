package com.obfs.encrypt.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.QuickAccessRepository
import com.obfs.encrypt.data.RecentFoldersRepository
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.performance.TraceSection
import com.obfs.encrypt.performance.TraceSections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val size: Long,
    val lastModified: Long
)

enum class SortOption {
    NAME,
    DATE,
    SIZE,
    TYPE
}

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val quickAccessRepository: QuickAccessRepository,
    private val recentFoldersRepository: RecentFoldersRepository,
    private val appDirectoryManager: AppDirectoryManager
) : AndroidViewModel(application) {

    private val rootDirectory = Environment.getExternalStorageDirectory()
    
    private val rootDirectories: List<File> by lazy {
        val roots = mutableListOf<File>()
        
        roots.add(rootDirectory)
        
        appDirectoryManager.getAppDirectory()?.let { roots.add(it) }
        
        try {
            val storageManager = application.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes?.forEach { volume ->
                volume.directory?.let { dir ->
                    if (!roots.any { it.absolutePath.startsWith(dir.absolutePath) }) {
                        roots.add(dir)
                    }
                }
            }
        } catch (_: Exception) {}
        
        roots
    }

    private val _currentDirectory = MutableStateFlow(rootDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _filesAndFolders = MutableStateFlow<List<FileItem>>(emptyList())
    val filesAndFolders: StateFlow<List<FileItem>> = _filesAndFolders.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<File>>(emptySet())
    val selectedItems: StateFlow<Set<File>> = _selectedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOption.NAME)
    val sortOrder: StateFlow<SortOption> = _sortOrder.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _favoritePaths = MutableStateFlow<Set<String>>(emptySet())
    val favoritePaths: StateFlow<Set<String>> = _favoritePaths.asStateFlow()

    private val _recentFolders = MutableStateFlow<List<String>>(emptyList())
    val recentFolders: StateFlow<List<String>> = _recentFolders.asStateFlow()

    private val _batchOperationResult = MutableStateFlow<BatchOperationResult?>(null)
    val batchOperationResult: StateFlow<BatchOperationResult?> = _batchOperationResult.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileItem>?>(null)
    val searchResults: StateFlow<List<FileItem>?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val searchResultCount: StateFlow<Int> = _searchResults.map { it?.size ?: 0 }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    private val _searchSubfolders = MutableStateFlow(false)
    val searchSubfolders: StateFlow<Boolean> = _searchSubfolders.asStateFlow()

    private val searchCache = mutableMapOf<String, List<FileItem>>()
    private val maxSearchCacheSize = 20

    private var searchJob: Job? = null

    // Performance: Cache for directory listings to avoid redundant disk reads
    private val directoryCache = mutableMapOf<String, List<FileItem>>()
    private val maxCacheSize = 50 // Limit cache to prevent memory issues

    // Scroll position persistence across navigation
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    // Performance: Debounce rapid navigation
    private var loadJob: Job? = null

    init {
        // Load sort preferences from settings
        viewModelScope.launch {
            _sortOrder.value = SortOption.valueOf(settingsRepository.fileSortOrder.first())
        }
        viewModelScope.launch {
            _sortAscending.value = settingsRepository.fileSortAscending.first()
        }
        // Load favorite paths
        viewModelScope.launch {
            quickAccessRepository.favoritePaths.collect { favorites ->
                _favoritePaths.value = favorites
            }
        }
        // Load recent folders
        viewModelScope.launch {
            recentFoldersRepository.recentFolders.collect { folders ->
                _recentFolders.value = folders
            }
        }
        loadDirectory(rootDirectory)
        setupSearchFlow()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun setupSearchFlow() {
        viewModelScope.launch {
            combine(_searchQuery, _searchSubfolders) { query, subfolders -> query to subfolders }
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { (query, subfolders) ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        flowOf(performSearch(query, subfolders))
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                    _isSearching.value = false
                }
        }
    }

    private suspend fun performSearch(query: String, searchSubfolders: Boolean): List<FileItem> {
        val cacheKey = "${_currentDirectory.value.absolutePath}|$query|$searchSubfolders"
        searchCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            if (searchSubfolders) {
                searchRecursive(_currentDirectory.value, query, results)
            } else {
                _currentDirectory.value.listFiles()?.forEach { file ->
                    if (file.name.contains(query, ignoreCase = true)) {
                        val isDir = file.isDirectory
                        results.add(
                            FileItem(
                                file = file,
                                isDirectory = isDir,
                                name = file.name,
                                size = if (isDir) 0L else file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
            val sorted = results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            if (searchCache.size >= maxSearchCacheSize) {
                searchCache.remove(searchCache.keys.first())
            }
            searchCache[cacheKey] = sorted
            sorted
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = null
            _isSearching.value = false
        } else {
            _isSearching.value = true
        }
    }

    fun toggleSearchSubfolders() {
        _searchSubfolders.value = !_searchSubfolders.value
        if (_searchQuery.value.isNotBlank()) {
            _isSearching.value = true
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = null
        _isSearching.value = false
        _searchSubfolders.value = false
        searchCache.clear()
    }

    fun saveScrollPosition(path: String, index: Int, offset: Int) {
        scrollPositions[path] = index to offset
    }

    fun getScrollPosition(path: String): Pair<Int, Int>? {
        return scrollPositions[path]
    }

    fun getCachedFiles(path: String): List<FileItem>? {
        return directoryCache[path]
    }

    fun navigateTo(directory: File) {
        if (directory.isDirectory && directory.canRead()) {
            // Track as recent folder
            viewModelScope.launch {
                recentFoldersRepository.addRecentFolder(directory.absolutePath)
            }
            loadDirectory(directory)
        } else {
            _error.value = "Cannot read directory: ${directory.name}"
        }
    }

    fun navigateToPath(path: String) {
        val directory = File(path)
        if (directory.exists() && directory.isDirectory && directory.canRead()) {
            loadDirectory(directory)
        } else {
            _error.value = "Cannot access directory: $path"
        }
    }

    fun canNavigateUp(): Boolean {
        val current = _currentDirectory.value
        return rootDirectories.any { root -> 
            current.absolutePath != root.absolutePath && 
            current.absolutePath.startsWith(root.absolutePath)
        }
    }

    fun navigateUp(): Boolean {
        if (!canNavigateUp()) return false
        
        val current = _currentDirectory.value
        val parent = current.parentFile
        if (parent != null) {
            loadDirectory(parent)
            return true
        }
        return false
    }

    fun isAtRoot(): Boolean = !canNavigateUp()

    fun toggleSelection(file: File) {
        val currentSelected = _selectedItems.value.toMutableSet()
        if (currentSelected.contains(file)) {
            currentSelected.remove(file)
        } else {
            currentSelected.add(file)
        }
        _selectedItems.value = currentSelected
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun selectAll() {
        val allFiles = _filesAndFolders.value.filter { !it.isDirectory }.map { it.file }.toSet()
        _selectedItems.value = allFiles
    }

    fun selectFiles(files: Collection<File>) {
        _selectedItems.value = files.filter { it.isFile }.toSet()
    }

    fun invertSelection() {
        val currentSelected = _selectedItems.value.toMutableSet()
        val allFiles = _filesAndFolders.value.filter { !it.isDirectory }.map { it.file }.toSet()
        _selectedItems.value = allFiles - currentSelected
    }

    fun selectNone() {
        clearSelection()
    }

    fun setSortOrder(sortOrder: SortOption) {
        _sortOrder.value = sortOrder
        viewModelScope.launch {
            settingsRepository.setFileSortOrder(sortOrder.name)
        }
        // Reload current directory with new sort order
        loadDirectory(_currentDirectory.value, forceRefresh = true)
    }

    fun toggleSortAscending() {
        _sortAscending.value = !_sortAscending.value
        viewModelScope.launch {
            settingsRepository.setFileSortAscending(_sortAscending.value)
        }
        // Reload current directory with new sort order
        loadDirectory(_currentDirectory.value, forceRefresh = true)
    }

    fun toggleFavorite(path: String) {
        viewModelScope.launch {
            quickAccessRepository.toggleFavorite(path)
        }
    }

    fun isFavorite(path: String): Boolean {
        return path in _favoritePaths.value
    }

    fun clearRecentFolders() {
        viewModelScope.launch {
            recentFoldersRepository.clearRecentFolders()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Batch Operations
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Delete selected files permanently.
     */
    fun batchDelete(files: List<File>) {
        if (files.isEmpty()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0
            
            withContext(Dispatchers.IO) {
                files.forEach { file ->
                    try {
                        if (file.deleteRecursively()) {
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            }
            
            _batchOperationResult.value = BatchOperationResult(
                successCount = successCount,
                failCount = failCount,
                operation = "Delete"
            )
            _isLoading.value = false
            clearSelection()
            refreshCurrentDirectory()
        }
    }

    /**
     * Copy selected files to destination directory.
     */
    fun batchCopy(files: List<File>, destinationDir: File) {
        if (files.isEmpty() || !destinationDir.exists()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0
            
            withContext(Dispatchers.IO) {
                files.forEach { file ->
                    try {
                        val targetFile = File(destinationDir, file.name)
                        if (file.isDirectory) {
                            file.copyRecursively(targetFile, overwrite = true)
                        } else {
                            file.copyTo(targetFile, overwrite = true)
                        }
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            }
            
            _batchOperationResult.value = BatchOperationResult(
                successCount = successCount,
                failCount = failCount,
                operation = "Copy"
            )
            _isLoading.value = false
            clearSelection()
        }
    }

    /**
     * Move selected files to destination directory.
     */
    fun batchMove(files: List<File>, destinationDir: File) {
        if (files.isEmpty() || !destinationDir.exists()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0
            
            withContext(Dispatchers.IO) {
                files.forEach { file ->
                    try {
                        val targetFile = File(destinationDir, file.name)
                        if (file.isDirectory) {
                            file.copyRecursively(targetFile, overwrite = true)
                            file.deleteRecursively()
                        } else {
                            file.copyTo(targetFile, overwrite = true)
                            file.delete()
                        }
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            }
            
            _batchOperationResult.value = BatchOperationResult(
                successCount = successCount,
                failCount = failCount,
                operation = "Move"
            )
            _isLoading.value = false
            clearSelection()
            refreshCurrentDirectory()
        }
    }

    /**
     * Share selected files via system share intent.
     * Returns a list of URIs to share.
     */
    fun batchShare(files: List<File>, context: android.content.Context): Intent? {
        if (files.isEmpty()) return null
        
        val uris = files.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                null
            }
        }
        
        if (uris.isEmpty()) return null
        
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return intent
    }

    fun clearBatchOperationResult() {
        _batchOperationResult.value = null
    }

    /**
     * Search files recursively in the current directory and subdirectories.
     */
    private fun searchRecursive(directory: File, query: String, results: MutableList<FileItem>) {
        try {
            val files = directory.listFiles() ?: return
            files.forEach { file ->
                if (file.name.contains(query, ignoreCase = true)) {
                    val isDir = file.isDirectory
                    results.add(
                        FileItem(
                            file = file,
                            isDirectory = isDir,
                            name = file.name,
                            size = if (isDir) 0L else file.length(),
                            lastModified = file.lastModified()
                        )
                    )
                }
                if (file.isDirectory && file.canRead()) {
                    searchRecursive(file, query, results)
                }
            }
        } catch (e: Exception) {
            // Skip inaccessible directories
        }
    }

    fun loadCurrentDirectory() {
        loadDirectory(_currentDirectory.value)
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_currentDirectory.value, forceRefresh = true)
    }

    /**
     * Load directory with caching and debouncing for performance.
     */
    private fun loadDirectory(directory: File, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        
        loadJob = viewModelScope.launch {
            delay(100)
            
            TraceSection.begin(TraceSections.FILE_LOAD)
            _isLoading.value = true
            _error.value = null
            
            try {
                val cacheKey = directory.absolutePath
                
                // Check cache first for instant navigation back
                if (!forceRefresh) {
                    val cachedItems = directoryCache[cacheKey]
                    if (cachedItems != null) {
                        // For cached items, we can update both path and files atomically for UI
                        _currentDirectory.value = directory
                        _filesAndFolders.value = cachedItems
                        _isLoading.value = false
                        TraceSection.end()
                        return@launch
                    }
                }
                
                // If not in cache or forced refresh, we update the directory first
                // to trigger the transition in UI, but keep old files until new ones are ready
                // OR clear them if we want to show loading.
                // To avoid "weird artifacts", we'll keep old files but marked as loading.
                _currentDirectory.value = directory
                
                // Load from disk with IO dispatcher
                val items = withContext(Dispatchers.IO) {
                    TraceSection.begin(TraceSections.FILE_SORT)
                    try {
                        val listFiles = directory.listFiles() ?: emptyArray()
                        val mappedItems = listFiles.map { file ->
                            val isDir = file.isDirectory
                            FileItem(
                                file = file,
                                isDirectory = isDir,
                                name = file.name,
                                size = if (isDir) 0L else file.length(),
                                lastModified = file.lastModified()
                            )
                        }
                        // Apply sorting based on current sort order and ascending preference
                        val sortOrder = _sortOrder.value
                        val ascending = _sortAscending.value
                        mappedItems.sortedWith(getComparator(sortOrder, ascending))
                    } finally {
                        TraceSection.end()
                    }
                }
                
                // Update cache with LRU eviction
                if (directoryCache.size >= maxCacheSize) {
                    // Remove oldest entry
                    directoryCache.remove(directoryCache.keys.first())
                }
                directoryCache[cacheKey] = items
                
                // Update files only if we are still on the same directory (handling rapid navigation)
                if (_currentDirectory.value.absolutePath == directory.absolutePath) {
                    _filesAndFolders.value = items
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Unknown error"
            } finally {
                _isLoading.value = false
                TraceSection.end()
            }
        }
    }
    
    /**
     * Clear directory cache to free memory.
     * Call this when leaving the file browser or on low memory.
     */
    fun clearCache() {
        directoryCache.clear()
    }
    
    /**
     * Trim cache to specified size.
     */
    fun trimCache(maxSize: Int = 20) {
        val keysToRemove = directoryCache.keys.take(directoryCache.size - maxSize)
        keysToRemove.forEach { directoryCache.remove(it) }
    }

    /**
     * Get comparator for sorting files based on sort option and order.
     */
    private fun getComparator(sortOrder: SortOption, ascending: Boolean): Comparator<FileItem> {
        val baseComparator = when (sortOrder) {
            SortOption.NAME -> compareBy<FileItem> { !it.isDirectory }
                .then(compareBy { it.name.lowercase() })
            SortOption.DATE -> compareBy<FileItem> { !it.isDirectory }
                .then(compareByDescending { it.lastModified })
            SortOption.SIZE -> compareBy<FileItem> { !it.isDirectory }
                .then(compareByDescending<FileItem> { if (it.isDirectory) 0L else it.size })
            SortOption.TYPE -> compareBy<FileItem> { !it.isDirectory }
                .then(compareBy { it.name.substringAfterLast('.', "").lowercase() })
                .then(compareBy { it.name.lowercase() })
        }
        return if (ascending) baseComparator else baseComparator.reversed()
    }
}

data class BatchOperationResult(
    val successCount: Int,
    val failCount: Int,
    val operation: String
) {
    val isSuccess: Boolean get() = failCount == 0
    val totalCount: Int get() = successCount + failCount
    val message: String get() = "$operation: $successCount/$totalCount succeeded"
}
