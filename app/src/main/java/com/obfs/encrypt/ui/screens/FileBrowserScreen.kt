package com.obfs.encrypt.ui.screens

import android.Manifest
import android.content.Intent
import java.io.File
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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

    // Track folder expansion/collapse animation
    var expandingFolderPath by remember { mutableStateOf<String?>(null) }
    var collapsingFolderPath by remember { mutableStateOf<String?>(null) }
    
    // Track the bounds of the folder item being expanded (for shared element animation)
    var expandingFolderBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Animation state for folder expansion
    val expansionProgress by animateFloatAsState(
        targetValue = if (expandingFolderPath != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "folder_expansion"
    )
    
    // Track previous directory for collapse animation
    var previousDirectory by remember { mutableStateOf<File?>(null) }

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

    // Track directory changes for collapse animation
    LaunchedEffect(currentDirectory) {
        if (collapsingFolderPath != null) {
            // We're collapsing, wait for animation then reset
            delay(600)
            collapsingFolderPath = null
        }
        // When navigating to a new folder during expansion, trigger collapse animation
        if (expandingFolderPath != null && currentDirectory?.absolutePath != expandingFolderPath) {
            // Expansion completed, new directory is loaded
        }
        previousDirectory = currentDirectory
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
                                // Trigger collapse animation if we have a previous directory
                                if (previousDirectory != null && currentDirectory != previousDirectory) {
                                    collapsingFolderPath = currentDirectory.absolutePath
                                    scope.launch {
                                        delay(300)
                                        onNavigateBack()
                                    }
                                } else {
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
                    if (!item.isDirectory) {
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
                onFolderClick = { item, bounds ->
                    haptic.click()
                    // Capture folder bounds for animation
                    expandingFolderPath = item.file.absolutePath
                    expandingFolderBounds = bounds
                    // Navigate immediately so content appears during morph transition
                    scope.launch {
                        fileManagerViewModel.navigateTo(item.file)
                        // Reset expansion state after animation completes
                        delay(600)
                        expandingFolderPath = null
                        expandingFolderBounds = null
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

    // Folder expansion animation overlay - morph transition from folder position
    if (expandingFolderPath != null) {
        FolderExpansionOverlay(
            folderPath = expandingFolderPath!!,
            folderBounds = expandingFolderBounds,
            expansionProgress = expansionProgress,
            isExpanding = true
        )
    }

    // Folder collapse animation overlay - morph transition back
    if (collapsingFolderPath != null) {
        FolderExpansionOverlay(
            folderPath = collapsingFolderPath!!,
            folderBounds = null, // Collapse from center
            expansionProgress = 1f - expansionProgress,
            isExpanding = false
        )
    }
}

/**
 * Folder expansion/collapse morph transition.
 * Creates a smooth shared-element-like transition where the folder item
 * expands from its position to reveal the new screen content.
 */
@Composable
private fun FolderExpansionOverlay(
    folderPath: String,
    folderBounds: Rect?,
    expansionProgress: Float,
    isExpanding: Boolean
) {
    val folderName = folderPath.substringAfterLast('/').ifEmpty { folderPath }
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // Calculate start position from bounds or default to center
    val startX = folderBounds?.let { (it.left + it.right) / 2 } ?: screenWidth / 2
    val startY = folderBounds?.let { (it.top + it.bottom) / 2 } ?: screenHeight / 3
    val startWidth = folderBounds?.width ?: 100f
    val startHeight = folderBounds?.height ?: 72f

    // Calculate scale from the folder item size to fill most of screen (not full)
    val targetScaleX = 0.95f  // Don't scale to full screen width
    val targetScaleY = 0.90f  // Don't scale to full screen height
    val scaleX = lerp(startWidth / screenWidth, targetScaleX, expansionProgress.coerceIn(0f, 1f))
    val scaleY = lerp(startHeight / screenHeight, targetScaleY, expansionProgress.coerceIn(0f, 1f))

    // Corner radius animation: starts rounded (like folder icon) to less rounded
    val cornerRadius by animateFloatAsState(
        targetValue = if (isExpanding) {
            lerp(16f, 8f, expansionProgress.coerceIn(0f, 1f))
        } else {
            lerp(8f, 16f, expansionProgress.coerceIn(0f, 1f))
        }.coerceAtLeast(0f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expansion_corner"
    )

    // Border width that shrinks as we expand
    val borderWidth by animateFloatAsState(
        targetValue = if (isExpanding) {
            lerp(2f, 0f, expansionProgress.coerceIn(0f, 1f))
        } else {
            lerp(0f, 2f, expansionProgress.coerceIn(0f, 1f))
        },
        animationSpec = tween(250),
        label = "expansion_border"
    )

    // Icon scale with bounce - shrinks as container expands
    val iconScale by animateFloatAsState(
        targetValue = if (isExpanding) {
            lerp(1f, 0.6f, expansionProgress.coerceIn(0f, 1f))
        } else {
            lerp(0.6f, 1f, expansionProgress.coerceIn(0f, 1f))
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expansion_icon_scale"
    )

    // Icon alpha - fades out as we transition
    val iconAlpha by animateFloatAsState(
        targetValue = if (isExpanding) {
            if (expansionProgress > 0.7f) 0f else 1f
        } else {
            if (expansionProgress > 0.3f) 1f else 0f
        },
        animationSpec = tween(200),
        label = "expansion_icon_alpha"
    )

    // Text alpha - fades out during transition
    val textAlpha by animateFloatAsState(
        targetValue = if (isExpanding) {
            if (expansionProgress > 0.6f) 0f else 1f
        } else {
            if (expansionProgress > 0.4f) 1f else 0f
        },
        animationSpec = tween(200),
        label = "expansion_text_alpha"
    )

    // Overall overlay alpha - subtle background dim
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isExpanding) {
            expansionProgress * 0.5f  // Max 50% opacity for subtle dim
        } else {
            (1f - expansionProgress) * 0.5f
        },
        animationSpec = tween(300),
        label = "expansion_overlay_alpha"
    )

    // Content alpha for the morphing container
    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanding) {
            if (expansionProgress > 0.8f) 0f else 1f
        } else {
            if (expansionProgress > 0.2f) 1f else 0f
        },
        animationSpec = tween(250),
        label = "expansion_content_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = overlayAlpha
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        // Morphing container that expands from folder position
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Scale from the folder position
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (startX / screenWidth).coerceIn(0f, 1f),
                        pivotFractionY = (startY / screenHeight).coerceIn(0f, 1f)
                    )
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.alpha = contentAlpha
                    shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0f).dp)
                    clip = true
                }
                .border(
                    width = borderWidth.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0f).dp)
                )
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
        ) {
            // Folder icon and name that fade out during transition
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Folder icon with scale animation
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            this.scaleX = iconScale
                            this.scaleY = iconScale
                            this.alpha = iconAlpha
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Folder name with fade animation
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        alpha = textAlpha
                    }
                )
            }
        }
    }
}

/**
 * Linear interpolation helper for smooth animations.
 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
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
