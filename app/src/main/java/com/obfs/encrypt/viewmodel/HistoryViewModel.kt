package com.obfs.encrypt.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.EncryptionHistoryRepository
import com.obfs.encrypt.data.formatFileSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing encryption history UI.
 * Provides search, filter, and bulk operations on history items.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: EncryptionHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _allHistoryItems = MutableStateFlow<List<EncryptionHistoryItem>>(emptyList())
    val allHistoryItems: StateFlow<List<EncryptionHistoryItem>> = _allHistoryItems.asStateFlow()

    private val _filteredItems = MutableStateFlow<List<EncryptionHistoryItem>>(emptyList())
    val filteredItems: StateFlow<List<EncryptionHistoryItem>> = _filteredItems.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<HistoryFilter>(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showClearConfirmDialog = MutableStateFlow(false)
    val showClearConfirmDialog: StateFlow<Boolean> = _showClearConfirmDialog.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.historyItems.collect { items ->
                _allHistoryItems.value = items
                applyFiltersAndSearch()
            }
        }
    }

    /**
     * Update search query and filter items.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFiltersAndSearch()
    }

    /**
     * Set active filter and refresh filtered list.
     */
    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
        applyFiltersAndSearch()
    }

    /**
     * Toggle item selection for bulk operations.
     */
    fun toggleItemSelection(itemId: String) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItems.value = current

        // Exit selection mode if no items selected
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    /**
     * Select all currently visible items.
     */
    fun selectAll() {
        _selectedItems.value = _filteredItems.value.map { it.id }.toSet()
        _isSelectionMode.value = true
    }

    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * Enter selection mode.
     */
    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }

    /**
     * Delete selected items from history.
     */
    fun deleteSelectedItems() {
        viewModelScope.launch {
            _selectedItems.value.forEach { itemId ->
                historyRepository.removeItem(itemId)
            }
            clearSelection()
        }
    }

    /**
     * Show clear all history confirmation dialog.
     */
    fun showClearHistoryDialog() {
        _showClearConfirmDialog.value = true
    }

    /**
     * Hide clear all history confirmation dialog.
     */
    fun hideClearHistoryDialog() {
        _showClearConfirmDialog.value = false
    }

    /**
     * Clear all history.
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
            clearSelection()
        }
        _showClearConfirmDialog.value = false
    }

    /**
     * Show export dialog.
     */
    fun showExportDialog() {
        _showExportDialog.value = true
    }

    /**
     * Hide export dialog.
     */
    fun hideExportDialog() {
        _showExportDialog.value = false
    }

    /**
     * Export history to JSON file.
     */
    fun exportHistory(outputUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val gson = com.google.gson.Gson()
                val json = gson.toJson(_allHistoryItems.value)

                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
            } catch (e: Exception) {
                // Handle error (could emit error state)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _showExportDialog.value = false
            }
        }
    }

    /**
     * Get history grouped by date for display.
     */
    fun getGroupedHistory(): Map<String, List<EncryptionHistoryItem>> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val oneWeekMs = 7 * oneDayMs

        return _filteredItems.value.groupBy { item ->
            val age = now - item.timestamp
            when {
                age < oneDayMs -> "Today"
                age < 2 * oneDayMs -> "Yesterday"
                age < oneWeekMs -> "This Week"
                age < 2 * oneWeekMs -> "Last Week"
                age < 30 * oneDayMs -> "This Month"
                else -> "Older"
            }
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Get statistics about history.
     */
    fun getStatistics(): HistoryStatistics {
        val items = _allHistoryItems.value
        return HistoryStatistics(
            totalItems = items.size,
            encryptCount = items.count { it.operationType == EncryptionHistoryItem.OperationType.ENCRYPT },
            decryptCount = items.count { it.operationType == EncryptionHistoryItem.OperationType.DECRYPT },
            successCount = items.count { it.success },
            failedCount = items.count { !it.success },
            totalBytesProcessed = items.sumOf { it.fileSize }
        )
    }

    /**
     * Apply current search query and filter to items.
     */
    private fun applyFiltersAndSearch() {
        val query = _searchQuery.value.lowercase()
        val filter = _selectedFilter.value

        _filteredItems.value = _allHistoryItems.value.filter { item ->
            // Apply search filter
            val matchesSearch = query.isEmpty() ||
                    item.fileName.lowercase().contains(query) ||
                    item.operationType.name.lowercase().contains(query) ||
                    (item.encryptionMethod?.name?.lowercase()?.contains(query) == true)

            // Apply type filter
            val matchesType = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.ENCRYPT -> item.operationType == EncryptionHistoryItem.OperationType.ENCRYPT
                HistoryFilter.DECRYPT -> item.operationType == EncryptionHistoryItem.OperationType.DECRYPT
                HistoryFilter.SUCCESS -> item.success
                HistoryFilter.FAILED -> !item.success
            }

            matchesSearch && matchesType
        }
    }
}

/**
 * Filter options for history items.
 */
enum class HistoryFilter {
    ALL,
    ENCRYPT,
    DECRYPT,
    SUCCESS,
    FAILED
}

/**
 * Statistics about encryption history.
 */
data class HistoryStatistics(
    val totalItems: Int,
    val encryptCount: Int,
    val decryptCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalBytesProcessed: Long
)
