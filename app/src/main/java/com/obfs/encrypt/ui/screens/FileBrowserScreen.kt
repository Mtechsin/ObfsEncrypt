package com.obfs.encrypt.ui.screens

import android.Manifest
import android.content.Intent
import java.io.File
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Folder
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
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
import com.obfs.encrypt.performance.CoilImageLoader
import com.obfs.encrypt.performance.MemoryManager
import com.obfs.encrypt.ui.components.FileFilter
import com.obfs.encrypt.ui.components.FileFilterChips
import com.obfs.encrypt.ui.components.FilePathBreadcrumb
import com.obfs.encrypt.ui.components.FilePreviewSheet
import com.obfs.encrypt.ui.components.FileSearchBar
import com.obfs.encrypt.ui.components.SearchEmptyState
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
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
    var selectedFilter by rememberSaveable { mutableStateOf(FileFilter.ALL) }
    val quickAccessExpanded by viewModel.quickAccessExpanded.collectAsState()
    val favoritePaths by fileManagerViewModel.favoritePaths.collectAsState()
    val scope = rememberCoroutineScope()

    val selectedItems by fileManagerViewModel.selectedItems.collectAsState()
    val currentDirectory by fileManagerViewModel.currentDirectory.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()
    val sortOrder by fileManagerViewModel.sortOrder.collectAsState()
    val sortAscending by fileManagerViewModel.sortAscending.collectAsState()
    val filesAndFolders by fileManagerViewModel.filesAndFolders.collectAsState()
    
    val searchQuery by fileManagerViewModel.searchQuery.collectAsState()
    val searchResults by fileManagerViewModel.searchResults.collectAsState()
    val isSearching by fileManagerViewModel.isSearching.collectAsState()
    val searchResultCount by fileManagerViewModel.searchResultCount.collectAsState()
    val searchSubfolders by fileManagerViewModel.searchSubfolders.collectAsState()

    // Track navigation direction for spatial hints
    var previousPath by remember { mutableStateOf(currentDirectory.absolutePath) }
    val isForward = remember(currentDirectory.absolutePath) {
        val forward = currentDirectory.absolutePath.length > previousPath.length || 
                     currentDirectory.absolutePath.startsWith(previousPath) && currentDirectory.absolutePath != previousPath
        previousPath = currentDirectory.absolutePath
        forward
    }

    // Handle system back button - navigate to previous folder instead of closing app
    BackHandler {
        haptic.click()
        if (showSearch) {
            showSearch = false
            fileManagerViewModel.clearSearch()
        } else {
            val canNavigateUp = fileManagerViewModel.navigateUp()
            if (!canNavigateUp) {
                onNavigateBack()
            }
        }
    }

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
    
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            CoilImageLoader.clearMemoryCache()
        }
    }
    
    if (!hasPermission) {
        FileBrowserPermissionRequest(onRequest = ::requestPermissions)
        return
    }
    
    val filteredFiles by remember {
        derivedStateOf {
            filesAndFolders.filter { item ->
                shouldIncludeFile(item.name, item.isDirectory, selectedFilter)
            }
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

    val hasSelection = selectedItems.isNotEmpty()
    
    SharedTransitionLayout {
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
                                    fileManagerViewModel.clearSearch()
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    haptic.click()
                                    val canNavigateUp = fileManagerViewModel.navigateUp()
                                    if (!canNavigateUp) {
                                        // Already at root, go back to previous screen
                                        onNavigateBack()
                                    }
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
                                            showOutputDialogTrigger = System.currentTimeMillis()
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
                // Static elements that don't move with folder navigation
                if (showSearch) {
                    FileSearchBar(
                        query = searchQuery,
                        onQueryChange = { fileManagerViewModel.updateSearchQuery(it) },
                        isSearching = isSearching,
                        resultCount = searchResultCount,
                        searchSubfolders = searchSubfolders,
                        onSearchSubfoldersChange = { fileManagerViewModel.toggleSearchSubfolders() },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column {
                            // Quick Access Section - Fixed at top
                            QuickAccessSection(
                                favoritePaths = favoritePaths,
                                onFolderClick = { path ->
                                    fileManagerViewModel.navigateToPath(path)
                                },
                                onFavoriteClick = { path ->
                                    fileManagerViewModel.toggleFavorite(path)
                                },
                                currentPath = currentDirectory.absolutePath,
                                isExpanded = quickAccessExpanded,
                                onToggleExpand = { viewModel.setQuickAccessExpanded(!quickAccessExpanded) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            
                            // File Filter Chips - Fixed below Quick Access
                            FileFilterChips(
                                selectedFilter = selectedFilter,
                                onFilterSelected = { selectedFilter = it },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                // The dynamic content area that participates in folder transitions
                AnimatedContent(
                    targetState = currentDirectory.absolutePath,
                    transitionSpec = {
                        val duration = 450
                        val easing = androidx.compose.animation.core.FastOutSlowInEasing
                        
                        // For a forward move (deeper), fadeIn the new screen
                        // For a backward move (higher), the incoming screen is already "below" 
                        // so we focus more on letting the shared element collapse the current view.
                        if (isForward) {
                            (fadeIn(tween(duration, easing = easing)) + 
                             scaleIn(initialScale = 0.94f, animationSpec = tween(duration, easing = easing)))
                                .togetherWith(fadeOut(tween(duration / 2, easing = easing)))
                                .using(SizeTransform(clip = true))
                        } else {
                            // Backward: fade the current container as it shrinks, and fadeIn the parent list
                            (fadeIn(tween(duration, easing = easing)))
                                .togetherWith(fadeOut(tween(duration, easing = easing)) + 
                                            scaleOut(targetScale = 0.94f, animationSpec = tween(duration, easing = easing)))
                                .using(SizeTransform(clip = true))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "directory_transition"
                ) { targetPath ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .sharedElement(
                                rememberSharedContentState(key = "folder_$targetPath"),
                                animatedVisibilityScope = this@AnimatedContent,
                                boundsTransform = { _, _ ->
                                    tween(
                                        durationMillis = 450, // Matches transition duration for sync
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                }
                            )
                    ) {
                        if (!showSearch && targetPath != Environment.getExternalStorageDirectory().absolutePath) {
                            FilePathBreadcrumb(
                                currentPath = targetPath,
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
                        
                        // Path-aware file list for smooth transitions
                        val displayFiles = remember(targetPath, filesAndFolders, currentDirectory, showSearch, searchResults, selectedFilter) {
                            if (showSearch && searchResults != null && targetPath == currentDirectory.absolutePath) {
                                val results = searchResults!!
                                if (selectedFilter != FileFilter.ALL) {
                                    results.filter { item ->
                                        shouldIncludeFile(item.name, item.isDirectory, selectedFilter)
                                    }
                                } else {
                                    results
                                }
                            } else if (currentDirectory.absolutePath == targetPath) {
                                filteredFiles
                            } else {
                                val cached = fileManagerViewModel.getCachedFiles(targetPath) ?: emptyList()
                                cached.filter { item ->
                                    shouldIncludeFile(item.name, item.isDirectory, selectedFilter)
                                }
                            }
                        }

                        val showSearchEmpty = showSearch && searchQuery.isNotBlank() && searchResults != null && displayFiles.isEmpty()

                        if (showSearchEmpty) {
                            SearchEmptyState(
                                isSearching = isSearching,
                                query = searchQuery
                            )
                        } else {
                            OptimizedFileList(
                                filesAndFolders = displayFiles,
                                selectedItems = selectedItems,
                                isLoading = isLoading && !showSearch,
                                favoritePaths = favoritePaths,
                                onFileClick = { item ->
                                    haptic.click()
                                    if (!item.isDirectory) {
                                        if (showSearch) {
                                            fileManagerViewModel.navigateTo(item.file.parentFile ?: item.file)
                                            showSearch = false
                                            fileManagerViewModel.clearSearch()
                                        } else {
                                            fileManagerViewModel.toggleSelection(item.file)
                                        }
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
                                onFolderClick = { item, _ ->
                                    haptic.click()
                                    if (showSearch) {
                                        fileManagerViewModel.navigateTo(item.file)
                                        showSearch = false
                                        fileManagerViewModel.clearSearch()
                                    } else {
                                        fileManagerViewModel.navigateTo(item.file)
                                    }
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
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@AnimatedContent,
                                modifier = Modifier.weight(1f),
                                currentPath = targetPath,
                                onSaveScrollPosition = { path, index, offset ->
                                    fileManagerViewModel.saveScrollPosition(path, index, offset)
                                },
                                initialScrollPosition = fileManagerViewModel.getScrollPosition(targetPath),
                                searchQuery = if (showSearch) searchQuery else ""
                            )
                        }
                    }
                }
            }
        }
    }

    // Overlays rendered last
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
        batchOperationResult?.let {
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
