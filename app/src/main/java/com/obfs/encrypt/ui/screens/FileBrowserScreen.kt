package com.obfs.encrypt.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.obfs.encrypt.R
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.ui.components.FileFilter
import com.obfs.encrypt.ui.components.FileFilterChips
import com.obfs.encrypt.ui.components.FilePathBreadcrumb
import com.obfs.encrypt.ui.components.FilePreviewSheet
import com.obfs.encrypt.ui.components.FileSearchBar
import com.obfs.encrypt.ui.components.BatchOperationsMenu
import com.obfs.encrypt.ui.components.FolderPickerDialog
import com.obfs.encrypt.ui.components.DeleteConfirmationDialog
import com.obfs.encrypt.ui.components.QuickAccessSection
import com.obfs.encrypt.ui.components.SortOptionsDropdown
import com.obfs.encrypt.ui.components.optimized.OptimizedFileList
import com.obfs.encrypt.ui.components.shouldIncludeFile
import com.obfs.encrypt.ui.utils.rememberHapticFeedback
import com.obfs.encrypt.viewmodel.FileItem
import com.obfs.encrypt.viewmodel.FileManagerViewModel
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit,
    viewModel: MainViewModel,
    fileManagerViewModel: FileManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(FileFilter.ALL) }
    val quickAccessExpanded by viewModel.quickAccessExpanded.collectAsState()
    val favoritePaths by fileManagerViewModel.favoritePaths.collectAsState()
    var searchSubfolders by rememberSaveable { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<FileItem>?>(null) }
    val scope = rememberCoroutineScope()

    val selectedItems by fileManagerViewModel.selectedItems.collectAsState()
    val currentDirectory by fileManagerViewModel.currentDirectory.collectAsState()
    val filesAndFolders by fileManagerViewModel.filesAndFolders.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()
    val sortOrder by fileManagerViewModel.sortOrder.collectAsState()
    val sortAscending by fileManagerViewModel.sortAscending.collectAsState()

    val isDecryptionMode by remember {
        derivedStateOf {
            selectedItems.isNotEmpty() && selectedItems.all { it.name.endsWith(".obfs", ignoreCase = true) }
        }
    }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }

    // Use a timestamped trigger to guarantee recomposition on every click
    var showOutputDialogTrigger by remember { mutableStateOf<Long?>(null) }

    var selectedMethod by remember { mutableStateOf(EncryptionMethod.STANDARD) }
    var pendingShowDialog by remember { mutableStateOf<String?>(null) }
    var pendingShowPasswordFromMethod by remember { mutableStateOf(false) }
    var selectedOutputUri by remember { mutableStateOf<Uri?>(null) }

    // Batch operation states
    var showCopyMoveDialog by remember { mutableStateOf<String?>(null) } // "copy" or "move"
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val batchOperationResult by fileManagerViewModel.batchOperationResult.collectAsState()

    // File preview state
    var previewFileItem by remember { mutableStateOf<FileItem?>(null) }

    // Handle dialog sequencing to avoid race conditions
    LaunchedEffect(pendingShowDialog) {
        pendingShowDialog?.let { dialog ->
            delay(100) // Delay to allow OutputLocationDialog to fully dismiss
            when (dialog) {
                "password" -> showPasswordDialog = true
                "method" -> showMethodDialog = true
            }
            pendingShowDialog = null
        }
    }

    // Handle transition from MethodDialog to PasswordDialog
    LaunchedEffect(pendingShowPasswordFromMethod) {
        if (pendingShowPasswordFromMethod) {
            delay(100) // Delay to allow EncryptionMethodDialog to fully dismiss
            showPasswordDialog = true
            pendingShowPasswordFromMethod = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val granted = map.values.all { it }
        hasPermission = granted
        if (granted) fileManagerViewModel.loadCurrentDirectory()
    }
    
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
            if (hasPermission) fileManagerViewModel.loadCurrentDirectory()
        }
    }
    
    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            fileManagerViewModel.loadCurrentDirectory()
        }
    }
    
    if (!hasPermission) {
        FileBrowserPermissionRequest(onRequest = ::requestPermissions)
        return
    }
    
    val filteredFiles = remember(filesAndFolders, searchQuery, selectedFilter) {
        filesAndFolders.filter { item ->
            val matchesFilter = shouldIncludeFile(item.name, item.isDirectory, selectedFilter)
            val matchesSearch = searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedOutputUri = it
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Share completed
    }


    
    // Dialogs moved inside Scaffold for reliability
    
    val hasSelection = selectedItems.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!showSearch) {
                        Text(
                            text = stringResource(R.string.browse_files),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                showSearch = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptic.click()
                                onNavigateBack()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                showSearch = true
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        SortOptionsDropdown(
                            currentSortOrder = sortOrder,
                            currentSortAscending = sortAscending,
                            onSortOrderChange = { fileManagerViewModel.setSortOrder(it) },
                            onSortAscendingToggle = { fileManagerViewModel.toggleSortAscending() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = hasSelection,
                enter = androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.slideOutVertically { it }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.items_selected, selectedItems.size),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (isDecryptionMode) stringResource(R.string.ready_to_decrypt) else stringResource(R.string.ready_to_encrypt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = {
                                        haptic.click()
                                        fileManagerViewModel.clearSelection()
                                    }
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                // Batch operations menu
                                BatchOperationsMenu(
                                    onCopy = {
                                        haptic.click()
                                        showCopyMoveDialog = "copy"
                                    },
                                    onMove = {
                                        haptic.click()
                                        showCopyMoveDialog = "move"
                                    },
                                    onDelete = {
                                        haptic.click()
                                        showDeleteConfirm = true
                                    },
                                    onShare = {
                                        haptic.click()
                                        val shareIntent = fileManagerViewModel.batchShare(
                                            selectedItems.toList(),
                                            context
                                        )
                                        shareIntent?.let {
                                            shareLauncher.launch(Intent.createChooser(it, "Share files"))
                                        }
                                    }
                                )
                                Button(
                                    onClick = {
                                        haptic.heavyClick()
                                        val trigger = System.currentTimeMillis()
                                        Log.d("FileBrowserScreen", "Button clicked! Generating new trigger: $trigger")
                                        showOutputDialogTrigger = trigger
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        if (isDecryptionMode) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isDecryptionMode) stringResource(R.string.decrypt) else stringResource(R.string.encrypt), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showSearch) {
                FileSearchBar(
                    query = searchQuery,
                    onQueryChange = { 
                        searchQuery = it
                        searchResults = null
                    },
                    onSearch = { 
                        if (it.isNotBlank()) {
                            fileManagerViewModel.searchFiles(
                                query = it,
                                searchSubfolders = searchSubfolders
                            ) { results ->
                                searchResults = results
                            }
                        }
                    },
                    searchSubfolders = searchSubfolders,
                    onSearchSubfoldersChange = { 
                        searchSubfolders = it
                        if (searchQuery.isNotBlank()) {
                            fileManagerViewModel.searchFiles(
                                query = searchQuery,
                                searchSubfolders = it
                            ) { results ->
                                searchResults = results
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column {
                        // Quick Access Section
                        QuickAccessSection(
                            favoritePaths = favoritePaths,
                            onFolderClick = { path ->
                                fileManagerViewModel.navigateToPath(path)
                            },
                            onFavoriteClick = { path ->
                                fileManagerViewModel.toggleFavorite(path)
                            },
                            isExpanded = quickAccessExpanded,
                            onToggleExpand = { viewModel.setQuickAccessExpanded(!quickAccessExpanded) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            if (!showSearch) {
                FileFilterChips(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                )
            }
            
            if (!showSearch && currentDirectory.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                FilePathBreadcrumb(
                    currentPath = currentDirectory.absolutePath,
                    onPathClick = { path ->
                        fileManagerViewModel.navigateToPath(path)
                    },
                    onHomeClick = {
                        fileManagerViewModel.navigateToPath(Environment.getExternalStorageDirectory().absolutePath)
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                )
            }
            
            val displayFiles = if (showSearch && searchResults != null) {
                // Show search results
                val results = searchResults!!
                if (selectedFilter != FileFilter.ALL) {
                    results.filter { item ->
                        shouldIncludeFile(item.name, item.isDirectory, selectedFilter)
                    }
                } else {
                    results
                }
            } else {
                // Show filtered files from current directory
                filteredFiles
            }

            OptimizedFileList(
                filesAndFolders = displayFiles,
                selectedItems = selectedItems,
                isLoading = isLoading,
                favoritePaths = favoritePaths,
                onFileClick = { item ->
                    haptic.click()
                    if (item.isDirectory) {
                        fileManagerViewModel.navigateTo(item.file)
                    } else {
                        // Click on name/row selects the file
                        fileManagerViewModel.toggleSelection(item.file)
                    }
                },
                onFileLongClick = { item ->
                    haptic.heavyClick()
                    if (!item.isDirectory) {
                        fileManagerViewModel.toggleSelection(item.file)
                    }
                },
                onFilePreview = { item ->
                    // Click on image thumbnail opens preview
                    haptic.click()
                    previewFileItem = item
                },
                onToggleSelect = { file ->
                    haptic.click()
                    fileManagerViewModel.toggleSelection(file)
                },
                onSelectAll = { fileManagerViewModel.selectAll() },
                onClearSelection = { fileManagerViewModel.clearSelection() },
                onRefresh = { fileManagerViewModel.refreshCurrentDirectory() },
                onToggleFavorite = { path ->
                    fileManagerViewModel.toggleFavorite(path)
                },
                modifier = Modifier.weight(1f)
            )

        }
    }

    // Overlays rendered last to ensure they are on top of everything including bottom bar
    if (showOutputDialogTrigger != null) {
        val currentOutputUri by viewModel.currentOutputUri.collectAsState()
        OutputLocationDialog(
            currentOutputUri = currentOutputUri,
            selectedUri = selectedOutputUri,
            onSelectCustomFolder = { folderPickerLauncher.launch(null) },
            onClearCustomFolder = { selectedOutputUri = null },
            onDismiss = { showOutputDialogTrigger = null },
            onConfirm = {
                viewModel.setCurrentOutputDirectory(selectedOutputUri)
                showOutputDialogTrigger = null
                pendingShowDialog = if (isDecryptionMode) "password" else "method"
            }
        )
    }

    if (showMethodDialog) {
        EncryptionMethodDialog(
            onDismiss = { showMethodDialog = false },
            onConfirm = { method ->
                selectedMethod = method
                showMethodDialog = false
                pendingShowPasswordFromMethod = true
            }
        )
    }

    if (showPasswordDialog) {
        PasswordDialog(
            uris = emptyList(),
            isFolder = selectedItems.size == 1 && selectedItems.first().isDirectory,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password, deleteOriginal ->
                showPasswordDialog = false
                if (isDecryptionMode) {
                    viewModel.decryptDirectFiles(selectedItems.toList(), password, deleteOriginal)
                } else {
                    viewModel.encryptDirectFiles(selectedItems.toList(), password, selectedMethod, deleteOriginal)
                }
                fileManagerViewModel.clearSelection()
                onNavigateToProgress(if (isDecryptionMode) "decrypt" else "device_folders")
            },
            showDeleteOption = true,
            isDecryption = isDecryptionMode,
            viewModel = viewModel
        )
    }

    // Copy/Move destination picker dialog
    if (showCopyMoveDialog != null) {
        FolderPickerDialog(
            title = if (showCopyMoveDialog == "copy") "Copy to..." else "Move to...",
            currentDirectory = currentDirectory,
            onDismiss = { showCopyMoveDialog = null },
            onConfirm = { destinationDir ->
                showCopyMoveDialog = null
                val selectedFiles = selectedItems.toList()
                if (showCopyMoveDialog == "copy") {
                    fileManagerViewModel.batchCopy(selectedFiles, destinationDir)
                } else {
                    fileManagerViewModel.batchMove(selectedFiles, destinationDir)
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            fileCount = selectedItems.size,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                fileManagerViewModel.batchDelete(selectedItems.toList())
            }
        )
    }

    // Batch operation result snackbar
    LaunchedEffect(batchOperationResult) {
        batchOperationResult?.let { result ->
            // Show result (could be enhanced with Snackbar)
            Log.d("FileBrowserScreen", "Batch operation result: ${result.message}")
            fileManagerViewModel.clearBatchOperationResult()
        }
    }

    // File preview sheet
    previewFileItem?.let { file ->
        FilePreviewSheet(
            fileItem = file,
            onDismiss = { previewFileItem = null }
        )
    }
}

private fun isTextFile(fileName: String): Boolean {
    val textExtensions = listOf("txt", "md", "json", "xml", "html", "htm", "csv", "log", "java", "kt", "js", "ts", "py", "cpp", "c", "h", "css", "scss", "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bat", "ps1", "sql", "php", "rb", "go", "rs", "swift")
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in textExtensions
}


@Composable
private fun FileBrowserPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.storage_access_required),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.grant_permission_browse),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}
