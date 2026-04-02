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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.ui.components.optimized.EmptyDirectoryContent
import com.obfs.encrypt.ui.components.optimized.OptimizedFileList
import com.obfs.encrypt.ui.theme.Motion
import com.obfs.encrypt.viewmodel.FileItem
import com.obfs.encrypt.viewmodel.FileManagerViewModel
import com.obfs.encrypt.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceFoldersScreen(
    onNavigateToProgress: (String) -> Unit,
    viewModel: MainViewModel,
    fileManagerViewModel: FileManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            fileManagerViewModel.loadCurrentDirectory()
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

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf(EncryptionMethod.STANDARD) }
    var pendingShowDialog by remember { mutableStateOf<String?>(null) }
    var pendingShowPasswordFromMethod by remember { mutableStateOf(false) }
    var selectedOutputUri by remember { mutableStateOf<Uri?>(null) }

    val selectedItems by fileManagerViewModel.selectedItems.collectAsState()

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
    val currentDirectory by fileManagerViewModel.currentDirectory.collectAsState()
    val filesAndFolders by fileManagerViewModel.filesAndFolders.collectAsState()
    val isLoading by fileManagerViewModel.isLoading.collectAsState()

    val isDecryptionMode = remember(selectedItems) {
        selectedItems.isNotEmpty() && selectedItems.all { it.name.endsWith(".obfs", ignoreCase = true) }
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

    if (showOutputDialog) {
        android.util.Log.d("DeviceFoldersScreen", "Attempting to render OutputLocationDialog from DeviceFoldersScreen")
        val currentOutputUri by viewModel.currentOutputUri.collectAsState()

        OutputLocationDialog(
            currentOutputUri = currentOutputUri,
            selectedUri = selectedOutputUri,
            onSelectCustomFolder = { folderPickerLauncher.launch(null) },
            onClearCustomFolder = { selectedOutputUri = null },
            onDismiss = { showOutputDialog = false },
            onConfirm = {
                viewModel.setCurrentOutputDirectory(selectedOutputUri)
                showOutputDialog = false
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

    val hasSelection = selectedItems.isNotEmpty()

    Scaffold(
        // Floating action button removed to avoid duplication with bottomBar
        topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = currentDirectory.name.ifEmpty { "Internal Storage" },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                val parentPath = currentDirectory.parentFile?.name
                                if (parentPath != null && parentPath.isNotEmpty()) {
                                    Text(
                                        text = parentPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (fileManagerViewModel.canNavigateUp()) {
                                IconButton(onClick = { fileManagerViewModel.navigateUp() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        },
                        actions = {
                            val refreshRotation by animateFloatAsState(
                                targetValue = if (isLoading) 360f else 0f,
                                animationSpec = if (isLoading)
                                    infiniteRepeatable(tween(800, easing = LinearEasing))
                                else
                                    tween(300),
                                label = "refreshRotation"
                            )
                            AnimatedVisibility(
                                visible = hasSelection && filesAndFolders.isNotEmpty(),
                                enter = fadeIn(animationSpec = tween(150)),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                Row {
                                    IconButton(onClick = { fileManagerViewModel.selectAll() }) {
                                        Icon(
                                            Icons.Default.SelectAll,
                                            contentDescription = "Select All"
                                        )
                                    }
                                    IconButton(onClick = { fileManagerViewModel.clearSelection() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear Selection"
                                        )
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = !hasSelection,
                                enter = fadeIn(animationSpec = tween(150)),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                IconButton(
                                    onClick = { fileManagerViewModel.refreshCurrentDirectory() },
                                    enabled = !isLoading
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        modifier = Modifier.rotate(refreshRotation),
                                        tint = if (isLoading)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ),
                        windowInsets = WindowInsets(0.dp)
                    )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = hasSelection,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 16.dp,
                    shadowElevation = 16.dp
                ) {
                    Column {
                        // Selection count bar
                        LinearProgressIndicator(
                            progress = 1f,
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
                                    text = "${selectedItems.size} item${if (selectedItems.size > 1) "s" else ""} selected",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDecryptionMode) "Ready to decrypt" else "Ready to encrypt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = { fileManagerViewModel.clearSelection() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = { showOutputDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 4.dp)
        ) {
            if (!hasPermission) {
                PermissionRequestContent(onRequest = ::requestPermissions)
            } else {
                // Use optimized file list with lazy loading and caching
                // It internally handles PullToRefresh, loading, and empty states
                OptimizedFileList(
                    filesAndFolders = filesAndFolders,
                    selectedItems = selectedItems,
                    isLoading = isLoading,
                    onFileClick = { item ->
                        if (item.isDirectory) {
                            fileManagerViewModel.navigateTo(item.file)
                        } else {
                            fileManagerViewModel.toggleSelection(item.file)
                        }
                    },
                    onFileLongClick = { item ->
                        if (!item.isDirectory) {
                            fileManagerViewModel.toggleSelection(item.file)
                        }
                    },
                    onToggleSelect = { file ->
                        fileManagerViewModel.toggleSelection(file)
                    },
                    onSelectAll = { fileManagerViewModel.selectAll() },
                    onClearSelection = { fileManagerViewModel.clearSelection() },
                    onRefresh = { fileManagerViewModel.refreshCurrentDirectory() },
                    currentPath = currentDirectory.absolutePath,
                    onSaveScrollPosition = { path, index, offset ->
                        fileManagerViewModel.saveScrollPosition(path, index, offset)
                    },
                    initialScrollPosition = fileManagerViewModel.getScrollPosition(currentDirectory.absolutePath)
                )
            }
        }
    }
}


@Composable
fun PermissionRequestContent(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Storage Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Obfs Encrypt needs access to your device storage to browse and select files for encryption.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Grant Permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EmptyDirectoryContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No files here",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This folder doesn't contain any files or subfolders",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Determines the file type based on extension
 */
enum class FileType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    APK,
    ARCHIVE,
    UNKNOWN
}

/**
 * Gets the file type from a file name
 */
fun getFileType(fileName: String, isDirectory: Boolean): FileType {
    if (isDirectory) return FileType.FOLDER

    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())

    return when (extension) {
        in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "raw") -> FileType.IMAGE
        in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v") -> FileType.VIDEO
        in listOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a") -> FileType.AUDIO
        in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt") -> FileType.DOCUMENT
        "apk" -> FileType.APK
        in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> FileType.ARCHIVE
        else -> FileType.UNKNOWN
    }
}

/**
 * Returns the appropriate icon for the file type
 */
@Composable
fun getIconForFileType(fileType: FileType): ImageVector {
    return when (fileType) {
        FileType.FOLDER -> Icons.Outlined.Folder
        FileType.IMAGE -> Icons.Outlined.Image
        FileType.VIDEO -> Icons.Outlined.Videocam
        FileType.AUDIO -> Icons.Outlined.MusicNote
        FileType.DOCUMENT -> Icons.AutoMirrored.Outlined.InsertDriveFile
        FileType.APK -> Icons.Outlined.Android
        FileType.ARCHIVE -> Icons.Outlined.FolderZip
        FileType.UNKNOWN -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

/**
 * Returns the accent color for the file type
 */
@Composable
fun getColorForFileType(fileType: FileType): Color {
    return when (fileType) {
        FileType.FOLDER -> MaterialTheme.colorScheme.primary
        FileType.IMAGE -> MaterialTheme.colorScheme.secondary
        FileType.VIDEO -> MaterialTheme.colorScheme.tertiary
        FileType.AUDIO -> MaterialTheme.colorScheme.secondary
        FileType.DOCUMENT -> MaterialTheme.colorScheme.tertiary
        FileType.APK -> MaterialTheme.colorScheme.primary
        FileType.ARCHIVE -> MaterialTheme.colorScheme.secondary
        FileType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    isSelected: Boolean,
    hasAnySelection: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val dateString = remember(item.lastModified) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.lastModified))
    }
    val context = LocalContext.current
    val sizeString = remember(item.size) {
        if (item.isDirectory) "" else android.text.format.Formatter.formatShortFileSize(context, item.size)
    }

    val fileType = remember(item.name, item.isDirectory) {
        getFileType(item.name, item.isDirectory)
    }

    val isImage = fileType == FileType.IMAGE
    val fileColor = getColorForFileType(fileType)

    // Animation for selection state
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
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
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon or image preview
            if (isImage && !item.isDirectory) {
                // Show image preview for photos
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
                // Show file type icon with minimalist design
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) {
                                fileColor.copy(alpha = 0.2f)
                            } else {
                                fileColor.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForFileType(fileType),
                        contentDescription = null,
                        tint = if (isSelected) fileColor else fileColor.copy(alpha = 0.9f),
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
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (!item.isDirectory && sizeString.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = sizeString,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Checkbox - only visible when any items are selected globally
            AnimatedVisibility(
                visible = hasAnySelection,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = fileColor,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
