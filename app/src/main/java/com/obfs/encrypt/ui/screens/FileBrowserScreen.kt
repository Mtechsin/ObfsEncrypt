package com.obfs.encrypt.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.ui.components.FileFilter
import com.obfs.encrypt.ui.components.FileFilterChips
import com.obfs.encrypt.ui.components.FilePathBreadcrumb
import com.obfs.encrypt.ui.components.FileSearchBar
import com.obfs.encrypt.ui.components.QuickAccessSection
import com.obfs.encrypt.ui.components.shouldIncludeFile
import com.obfs.encrypt.viewmodel.FileItem
import com.obfs.encrypt.viewmodel.FileManagerViewModel
import com.obfs.encrypt.viewmodel.MainViewModel
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
    val scope = rememberCoroutineScope()
    
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
    var quickAccessExpanded by rememberSaveable { mutableStateOf(true) }
    
    val selectedItems by fileManagerViewModel.selectedItems.collectAsState()
    val currentDirectory by fileManagerViewModel.currentDirectory.collectAsState()
    val filesAndFolders by fileManagerViewModel.filesAndFolders.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()
    
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

    // Handle dialog sequencing to avoid race conditions
    LaunchedEffect(pendingShowDialog) {
        pendingShowDialog?.let { dialog ->
            kotlinx.coroutines.delay(100) // Delay to allow OutputLocationDialog to fully dismiss
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
            kotlinx.coroutines.delay(100) // Delay to allow EncryptionMethodDialog to fully dismiss
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


    
    // Dialogs moved inside Scaffold for reliability
    
    val hasSelection = selectedItems.isNotEmpty()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!showSearch) {
                        Text(
                            text = "Browse Files",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = { 
                            showSearch = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                                    text = "${selectedItems.size} selected",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (isDecryptionMode) "Ready to decrypt" else "Ready to encrypt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = { fileManagerViewModel.clearSelection() }
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
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
                                    Text(if (isDecryptionMode) "Decrypt" else "Encrypt", fontWeight = FontWeight.Bold)
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
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    QuickAccessSection(
                        favoritePaths = emptySet(),
                        onFolderClick = { path ->
                            fileManagerViewModel.navigateToPath(path)
                        },
                        onFavoriteClick = { path ->
                            scope.launch {
                            }
                        },
                        isExpanded = quickAccessExpanded,
                        onToggleExpand = { quickAccessExpanded = !quickAccessExpanded },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No files found" else "No files in this folder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredFiles, key = { it.file.absolutePath }) { item ->
                        FileBrowserItem(
                            item = item,
                            isSelected = selectedItems.contains(item.file),
                            hasAnySelection = hasSelection,
                            onClick = {
                                if (item.isDirectory) {
                                    fileManagerViewModel.navigateTo(item.file)
                                } else {
                                    fileManagerViewModel.toggleSelection(item.file)
                                }
                            },
                            onToggleSelect = {
                                fileManagerViewModel.toggleSelection(item.file)
                            }
                        )
                    }
                }
            }

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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserItem(
    item: FileItem,
    isSelected: Boolean,
    hasAnySelection: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    
    val dateString = remember(item.lastModified) {
        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(item.lastModified))
    }
    
    val sizeString = remember(item.size) {
        if (!item.isDirectory) {
            android.text.format.Formatter.formatShortFileSize(context, item.size)
        } else ""
    }
    
    val fileType = remember(item.name, item.isDirectory) {
        getFileType(item.name, item.isDirectory)
    }
    
    val isImage = fileType == FileType.IMAGE
    val fileColor = getColorForFileType(fileType)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (!item.isDirectory) {
                            onToggleSelect()
                        }
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage && !item.isDirectory) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.file,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(android.R.drawable.ic_menu_gallery),
                        placeholder = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(fileColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForFileType(fileType),
                        contentDescription = null,
                        tint = fileColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (sizeString.isNotEmpty()) {
                    Text(
                        text = "$dateString • $sizeString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(visible = hasAnySelection) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
        }
    }
}

@Composable
private fun FileBrowserPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Storage Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Grant storage permission to browse files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant Permission")
        }
    }
}
