package com.obfs.encrypt.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obfs.encrypt.performance.TraceSection
import com.obfs.encrypt.performance.TraceSections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val rootDirectory = Environment.getExternalStorageDirectory()

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

    // Performance: Cache for directory listings to avoid redundant disk reads
    private val directoryCache = mutableMapOf<String, List<FileItem>>()
    private val maxCacheSize = 50 // Limit cache to prevent memory issues

    // Performance: Debounce rapid navigation
    private var loadJob: Job? = null

    init {
        loadDirectory(rootDirectory)
    }

    fun navigateTo(directory: File) {
        if (directory.isDirectory && directory.canRead()) {
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

    fun navigateUp(): Boolean {
        val current = _currentDirectory.value
        val parent = current.parentFile
        if (parent != null && parent.absolutePath.startsWith(Environment.getExternalStorageDirectory().parent ?: "")) {
             loadDirectory(parent)
             return true
        }
        return false // Can't go higher or reached the top
    }

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

    fun invertSelection() {
        val currentSelected = _selectedItems.value.toMutableSet()
        val allFiles = _filesAndFolders.value.filter { !it.isDirectory }.map { it.file }.toSet()
        _selectedItems.value = allFiles - currentSelected
    }

    fun selectNone() {
        clearSelection()
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
        // Cancel previous load job to prevent redundant operations
        loadJob?.cancel()
        
        loadJob = viewModelScope.launch {
            TraceSection.begin(TraceSections.FILE_LOAD)
            _isLoading.value = true
            _error.value = null
            
            try {
                val cacheKey = directory.absolutePath
                
                // Check cache first for instant navigation back
                if (!forceRefresh) {
                    val cachedItems = directoryCache[cacheKey]
                    if (cachedItems != null) {
                        _currentDirectory.value = directory
                        _filesAndFolders.value = cachedItems
                        _isLoading.value = false
                        TraceSection.end()
                        return@launch
                    }
                }
                
                // Load from disk with IO dispatcher
                _currentDirectory.value = directory
                val items = withContext(Dispatchers.IO) {
                    TraceSection.begin(TraceSections.FILE_SORT)
                    try {
                        val listFiles = directory.listFiles() ?: emptyArray()
                        val mappedItems = listFiles.map { file ->
                            FileItem(
                                file = file,
                                isDirectory = file.isDirectory,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        }
                        // Sort: Folders first, then files, both alphabetically
                        mappedItems.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
                
                _filesAndFolders.value = items
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
}
