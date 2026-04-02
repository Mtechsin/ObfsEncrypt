package com.obfs.encrypt.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obfs.encrypt.R
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.formatTimestamp
import com.obfs.encrypt.viewmodel.HistoryFilter
import com.obfs.encrypt.viewmodel.HistoryViewModel

/**
 * Encryption History Screen.
 * Displays all encryption/decryption operations with search, filter, and bulk operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val allItems by viewModel.allHistoryItems.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showClearConfirmDialog by viewModel.showClearConfirmDialog.collectAsState()
    val groupedHistory by viewModel.groupedHistory.collectAsState()

    var showFilterDropdown by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // File saver for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportHistory(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = stringResource(R.string.items_selected, selectedItems.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column {
                            Text(
                                text = stringResource(R.string.encryption_history),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.total_operations, allItems.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.done),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.deleteSelectedItems() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // Search icon
                        IconButton(onClick = { /* Focus search field */ }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }

                        // Filter dropdown
                        Box {
                            IconButton(onClick = { showFilterDropdown = !showFilterDropdown }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.filter)
                                )
                            }
                            FilterDropdown(
                                currentFilter = selectedFilter,
                                onFilterSelected = {
                                    viewModel.setFilter(it)
                                    showFilterDropdown = false
                                },
                                onDismiss = { showFilterDropdown = false },
                                expanded = showFilterDropdown
                            )
                        }

                        // Overflow menu
                        Box {
                            IconButton(onClick = { showOverflowMenu = !showOverflowMenu }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more)
                                )
                            }
                            if (showOverflowMenu) {
                                OverflowMenu(
                                    onExport = {
                                        showOverflowMenu = false
                                        exportLauncher.launch("encryption_history.json")
                                    },
                                    onClearAll = {
                                        showOverflowMenu = false
                                        viewModel.showClearHistoryDialog()
                                    },
                                    onDismiss = { showOverflowMenu = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_files)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    // Filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == HistoryFilter.ALL,
                            onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                            label = { Text(stringResource(R.string.all)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFilter == HistoryFilter.ENCRYPT,
                            onClick = { viewModel.setFilter(HistoryFilter.ENCRYPT) },
                            label = { Text(stringResource(R.string.encrypt)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFilter == HistoryFilter.DECRYPT,
                            onClick = { viewModel.setFilter(HistoryFilter.DECRYPT) },
                            label = { Text(stringResource(R.string.decrypt)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFilter == HistoryFilter.FAILED,
                            onClick = { viewModel.setFilter(HistoryFilter.FAILED) },
                            label = { Text(stringResource(R.string.failed_filter)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }

            // History list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredItems.isEmpty()) {
                EmptyHistoryState(
                    hasAnyHistory = allItems.isEmpty(),
                    isSearching = searchQuery.isNotEmpty(),
                    onClearSearch = { viewModel.setSearchQuery("") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedHistory.forEach { (section, items) ->
                        item(key = "header_$section") {
                            SectionHeader(title = section)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(items, key = { it.id }) { historyItem ->
                            HistoryItemCard(
                                item = historyItem,
                                isSelected = selectedItems.contains(historyItem.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(historyItem.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.enterSelectionMode()
                                    viewModel.toggleItemSelection(historyItem.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Clear all confirmation dialog
    if (showClearConfirmDialog) {
        ClearHistoryConfirmationDialog(
            onDismiss = { viewModel.hideClearHistoryDialog() },
            onConfirm = { viewModel.clearAllHistory() }
        )
    }
}

@Composable
private fun FilterDropdown(
    currentFilter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
    expanded: Boolean
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        HistoryFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = { 
                    val label = when(filter) {
                        HistoryFilter.ALL -> stringResource(R.string.all)
                        HistoryFilter.ENCRYPT -> stringResource(R.string.encrypt)
                        HistoryFilter.DECRYPT -> stringResource(R.string.decrypt)
                        HistoryFilter.SUCCESS -> stringResource(R.string.success_filter)
                        HistoryFilter.FAILED -> stringResource(R.string.failed_filter)
                    }
                    Text(label) 
                },
                onClick = { onFilterSelected(filter) },
                leadingIcon = {
                    if (currentFilter == filter) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun OverflowMenu(
    onExport: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_json)) },
            onClick = onExport,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.clear_all_history)) },
            onClick = onClearAll,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun HistoryItemCard(
    item: EncryptionHistoryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isEncrypt = item.operationType == EncryptionHistoryItem.OperationType.ENCRYPT
    val isSuccess = item.success

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(
                if (isSelected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (isSelected) 0.7f else 1f
            )
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox or icon
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            } else {
                Icon(
                    imageVector = getOperationIcon(item),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = getOperationColor(item, MaterialTheme.colorScheme)
                )
            }

            // File info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isEncrypt) {
                            item.encryptionMethod?.name ?: stringResource(R.string.system) // Default to something if name is missing
                        } else {
                            stringResource(R.string.decrypt)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (!isSuccess && item.errorMessage != null) {
                    Text(
                        text = "${stringResource(R.string.failed)}: ${item.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Success/Failure indicator
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (isSuccess) stringResource(R.string.success) else stringResource(R.string.failed),
                modifier = Modifier.size(24.dp),
                tint = if (isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun getOperationIcon(item: EncryptionHistoryItem): ImageVector {
    return when {
        item.operationType == EncryptionHistoryItem.OperationType.ENCRYPT ->
            Icons.AutoMirrored.Outlined.InsertDriveFile
        item.operationType == EncryptionHistoryItem.OperationType.DECRYPT ->
            Icons.Outlined.History
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

@Composable
private fun getOperationColor(
    item: EncryptionHistoryItem,
    colorScheme: androidx.compose.material3.ColorScheme
): androidx.compose.ui.graphics.Color {
    return when {
        !item.success -> colorScheme.error
        item.operationType == EncryptionHistoryItem.OperationType.ENCRYPT ->
            colorScheme.primary
        item.operationType == EncryptionHistoryItem.OperationType.DECRYPT ->
            colorScheme.secondary
        else -> colorScheme.onSurfaceVariant
    }
}

@Composable
private fun EmptyHistoryState(
    hasAnyHistory: Boolean,
    isSearching: Boolean,
    onClearSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasAnyHistory) Icons.Default.Search else Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSearching) {
                stringResource(R.string.no_results)
            } else if (hasAnyHistory) {
                stringResource(R.string.no_results) // Could be more specific
            } else {
                stringResource(R.string.no_recent_activity)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) {
                stringResource(R.string.try_different_search)
            } else if (hasAnyHistory) {
                stringResource(R.string.try_different_search) // Could be more specific
            } else {
                stringResource(R.string.history_empty_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isSearching) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClearSearch
            ) {
                Text(stringResource(R.string.clear_search))
            }
        }
    }
}

@Composable
private fun ClearHistoryConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.clear_history_prompt)) },
        text = {
            Text(
                stringResource(R.string.clear_history_description),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.clear_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
