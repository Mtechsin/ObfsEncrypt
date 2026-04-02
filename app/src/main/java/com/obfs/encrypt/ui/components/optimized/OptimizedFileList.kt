package com.obfs.encrypt.ui.components.optimized

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.obfs.encrypt.ui.components.SearchHighlightText
import com.obfs.encrypt.viewmodel.FileItem
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Threshold in pixels before refresh triggers
private const val REFRESH_TRIGGER_PX = 200f
// Max drag distance in pixels (rubberband ceiling)
private const val MAX_DRAG_PX = 280f

/**
 * Custom spring-physics pull-to-refresh NestedScrollConnection.
 * Gives instant tactile response with progressive rubberband resistance.
 */
private class PullToRefreshNestedScroll(
    private val isRefreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
    private val dragOffset: androidx.compose.runtime.MutableState<Float>,
    private val isTriggered: androidx.compose.runtime.MutableState<Boolean>,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) : NestedScrollConnection {

    private var resetJob: kotlinx.coroutines.Job? = null
    // Pre-allocated Zero offset to avoid allocations on every frame
    private val zeroOffset = Offset.Zero
    private val zeroVelocity = Velocity.Zero

    // Cached spring spec to avoid recreation per fling
    private val resetSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return zeroOffset

        // Cancel any ongoing reset animation when user starts scrolling
        if (available.y != 0f) {
            resetJob?.cancel()
        }

        // Consume upward scroll to collapse the indicator
        val currentDrag = dragOffset.value
        if (currentDrag > 0f && available.y < 0f) {
            val consumed = available.y.coerceAtLeast(-currentDrag)
            val newDrag = (currentDrag + consumed).coerceAtLeast(0f)
            dragOffset.value = newDrag
            if (newDrag == 0f) isTriggered.value = false
            return Offset(0f, consumed)
        }
        return zeroOffset
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f || isRefreshing()) return zeroOffset

        val currentDrag = dragOffset.value
        val ratio = currentDrag / MAX_DRAG_PX
        val factor = 0.55f * (1f - ratio * 0.7f).coerceAtLeast(0.15f)
        val delta = available.y * factor
        val newDrag = (currentDrag + delta).coerceAtMost(MAX_DRAG_PX)
        dragOffset.value = newDrag
        if (newDrag >= REFRESH_TRIGGER_PX && !isTriggered.value) {
            isTriggered.value = true
        }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (isTriggered.value && !isRefreshing()) {
            onRefresh()
        }

        // Always animate back to zero when user releases finger
        val currentDrag = dragOffset.value
        if (currentDrag > 0f) {
            resetJob = coroutineScope.launch {
                animate(
                    initialValue = currentDrag,
                    targetValue = 0f,
                    animationSpec = resetSpring
                ) { value, _ ->
                    dragOffset.value = value
                }
                isTriggered.value = false
            }
        }

        return zeroVelocity
    }
}

/**
 * Browser-style animated refresh indicator.
 * Shows circular progress with rotating arrow that transforms into a spinner during refresh.
 */
@Composable
private fun BrowserRefreshIndicator(
    dragOffset: Float,
    isTriggered: Boolean,
    isRefreshing: Boolean
) {
    val density = LocalDensity.current
    val triggerFraction = (dragOffset / REFRESH_TRIGGER_PX).coerceIn(0f, 1f)
    
    // Show indicator when refreshing OR when there's drag progress
    val showIndicator = isRefreshing || dragOffset > 10f
    
    // Visibility based on drag OR refresh state
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (showIndicator) 1f else 0f,
        animationSpec = tween(150),
        label = "indicatorAlpha"
    )
    
    // Vertical offset - indicator follows drag or stays at position when refreshing
    val indicatorOffsetY by animateFloatAsState(
        targetValue = if (isRefreshing) {
            44f
        } else {
            (dragOffset * 0.4f).coerceAtMost(44f)
        },
        animationSpec = tween(100),
        label = "indicatorOffsetY"
    )
    
    // Infinite spin during refresh
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )
    
    // Arrow rotation as user pulls (0° to 180°)
    val arrowRotation = triggerFraction * 180f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = with(density) { indicatorOffsetY.toDp() - 12.dp }),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .alpha(indicatorAlpha)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress background ring
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
            } else {
                // Static ring when pulling
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 2.dp,
                    progress = { triggerFraction * 0.75f }
                )
            }
            
            // Arrow / refresh icon in center
            Icon(
                imageVector = if (isRefreshing) {
                    Icons.Default.Refresh
                } else {
                    Icons.Default.ArrowDownward
                },
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (isRefreshing) spinAngle else arrowRotation),
                tint = if (isTriggered || isRefreshing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Optimized file list with lazy loading, pagination, and performance optimizations.
 * Designed for butter-smooth 120FPS scrolling even with thousands of files.
 * Features a premium custom pull-to-refresh with spring physics and animated indicator.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun OptimizedFileList(
    filesAndFolders: List<FileItem>,
    selectedItems: Set<File>,
    isLoading: Boolean,
    favoritePaths: Set<String> = emptySet(),
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onToggleSelect: (File) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onFilePreview: (FileItem) -> Unit = {},
    onFolderClick: ((FileItem, Rect?) -> Unit)? = null,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope? = null,
    modifier: Modifier = Modifier,
    currentPath: String? = null,
    onSaveScrollPosition: ((String, Int, Int) -> Unit)? = null,
    initialScrollPosition: Pair<Int, Int>? = null,
    searchQuery: String = ""
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollPosition?.first ?: 0,
        initialFirstVisibleItemScrollOffset = initialScrollPosition?.second ?: 0
    )
    val coroutineScope = rememberCoroutineScope()
    val hasSelection = selectedItems.isNotEmpty()
    val context = LocalContext.current

    // Auto-scroll to top when directory changes - only if we don't have an initial position
    var previousDirectoryHash by remember { mutableStateOf(0) }
    LaunchedEffect(filesAndFolders.size) {
        val currentHash = filesAndFolders.hashCode()
        if (previousDirectoryHash != 0 && previousDirectoryHash != currentHash) {
            if (initialScrollPosition == null) {
                listState.scrollToItem(0)
            }
        }
        previousDirectoryHash = currentHash
    }

    // Save scroll position with snapshotFlow + debounce for efficient change detection
    LaunchedEffect(currentPath) {
        if (currentPath != null && onSaveScrollPosition != null) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .debounce(200)
                .collect { (index, offset) ->
                    onSaveScrollPosition(currentPath, index, offset)
                }
        }
    }

    // Derived scroll state for efficient recomposition of scroll-dependent UI
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }

    // Pull-to-refresh state
    val dragOffset = remember { mutableStateOf(0f) }
    val isTriggered = remember { mutableStateOf(false) }

    // Haptic when refresh triggers
    LaunchedEffect(isTriggered.value) {
        if (isTriggered.value) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(18, 200))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(18)
                }
            } catch (_: Exception) {}
        }
    }

    // Animate indicator in when refresh starts (auto-pop like browser)
    LaunchedEffect(isLoading) {
        if (isLoading && dragOffset.value < 50f) {
            dragOffset.value = 60f
            isTriggered.value = true
        }
    }

    // Snap back to zero when refresh completes (auto-disappear like browser)
    LaunchedEffect(isLoading) {
        if (!isLoading && dragOffset.value > 0f) {
            delay(150)
            // Animate smoothly back to zero
            animate(
                initialValue = dragOffset.value,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                dragOffset.value = value
            }
            isTriggered.value = false
        }
    }

    val nestedScroll = remember(isLoading, coroutineScope) {
        PullToRefreshNestedScroll(
            isRefreshing = { isLoading },
            onRefresh = onRefresh,
            dragOffset = dragOffset,
            isTriggered = isTriggered,
            coroutineScope = coroutineScope
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScroll)
    ) {
        // The actual content
        if (isLoading && filesAndFolders.isEmpty()) {
            // First load — centered spinner, no indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        } else if (filesAndFolders.isEmpty()) {
            EmptyDirectoryContent()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = filesAndFolders,
                    key = { it.file.absolutePath },
                    contentType = { item ->
                        if (item.isDirectory) "folder"
                        else when (item.name.substringAfterLast('.', "").lowercase()) {
                            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "raw" -> "image"
                            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v" -> "video"
                            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> "audio"
                            else -> "file"
                        }
                    }
                ) { item ->
                    val isSelected = selectedItems.contains(item.file)
                    val isFavorite = item.file.absolutePath in favoritePaths
                    DisposableKeyedFileItem(
                        item = item,
                        isSelected = isSelected,
                        hasSelection = hasSelection,
                        hasAnySelection = hasSelection,
                        isFavorite = isFavorite,
                        onClick = { onFileClick(item) },
                        onLongClick = { onFileLongClick(item) },
                        onToggleSelect = { onToggleSelect(item.file) },
                        onToggleFavorite = { onToggleFavorite(item.file.absolutePath) },
                        onThumbnailClick = { onFilePreview(item) },
                        onFolderClick = onFolderClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        listState = listState,
                        searchQuery = searchQuery
                    )
                }
            }
        }

        // Overlay refresh indicator — browser style (conditionally composed)
        if (dragOffset.value > 0f || isLoading) {
            BrowserRefreshIndicator(
                dragOffset = dragOffset.value,
                isTriggered = isTriggered.value,
                isRefreshing = isLoading
            )
        }
    }
}




/**
 * Disposable wrapper for file items to ensure proper cleanup and recomposition optimization.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun DisposableKeyedFileItem(
    item: FileItem,
    isSelected: Boolean,
    hasSelection: Boolean,
    hasAnySelection: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onThumbnailClick: () -> Unit,
    onFolderClick: ((FileItem, Rect?) -> Unit)? = null,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope? = null,
    listState: LazyListState,
    searchQuery: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    // Use remember to cache expensive computations
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

    OptimizedFileItemRow(
        item = item,
        isSelected = isSelected,
        hasSelection = hasSelection,
        hasAnySelection = hasAnySelection,
        isFavorite = isFavorite,
        dateString = dateString,
        sizeString = sizeString,
        fileType = fileType,
        onClick = onClick,
        onLongClick = onLongClick,
        onToggleSelect = onToggleSelect,
        onToggleFavorite = onToggleFavorite,
        onThumbnailClick = onThumbnailClick,
        onFolderClick = onFolderClick,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        listState = listState,
        searchQuery = searchQuery
    )
}

/**
 * Highly optimized file item row with minimal recomposition.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun OptimizedFileItemRow(
    item: FileItem,
    isSelected: Boolean,
    hasSelection: Boolean,
    hasAnySelection: Boolean,
    isFavorite: Boolean,
    dateString: String,
    sizeString: String,
    fileType: FileType,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onThumbnailClick: () -> Unit,
    onFolderClick: ((FileItem, Rect?) -> Unit)? = null,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope? = null,
    listState: LazyListState,
    searchQuery: String = ""
) {
    val isImage = fileType == FileType.IMAGE
    val fileColor = getColorForFileType(fileType)
    val isFolder = item.isDirectory

    // Store folder bounds for legacy animation if needed, but we prefer Container Transform
    var folderBounds by remember { mutableStateOf<Rect?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFolder && sharedTransitionScope != null && animatedContentScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "folder_${item.file.absolutePath}"),
                            animatedVisibilityScope = animatedContentScope,
                            boundsTransform = { _, _ ->
                                tween(
                                    durationMillis = 400,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFolder && onFolderClick != null) {
                        Modifier.onGloballyPositioned { coordinates ->
                            val topLeft = coordinates.localToRoot(Offset.Zero)
                            val bottomRight = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                            folderBounds = Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                        }
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isFolder && onFolderClick != null) {
                            onFolderClick(item, folderBounds)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon or image preview with performance optimizations
            if (isImage && !item.isDirectory) {
                // Performance: Use cached image loading with memory optimization
                // Click on thumbnail opens preview, click on row selects
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onThumbnailClick)
                ) {
                    OptimizedImageThumbnail(
                        file = item.file,
                        modifier = Modifier.fillMaxSize(),
                        listState = listState
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

            // File info column
            Column(modifier = Modifier.weight(1f)) {
                if (searchQuery.isNotBlank()) {
                    SearchHighlightText(
                        text = item.name,
                        query = searchQuery,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
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
                        },
                        lineHeight = 20.sp
                    )
                }
                
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

            // Favorite star icon - skip animation during scroll for performance
            val starVisible = !hasAnySelection && item.isDirectory

            if (starVisible) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) {
                            Color(0xFFFFD700)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Checkbox - skip animation during scroll for performance
            if (hasAnySelection) {
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

/**
 * Optimized image thumbnail with Coil caching and memory management.
 */
@Composable
private fun OptimizedImageThumbnail(
    file: File,
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(if (isScrolling) 0 else 300)
                .size(64)
                .memoryCacheKey(file.absolutePath)
                .diskCacheKey(file.absolutePath)
                .build(),
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            error = painterResource(android.R.drawable.ic_menu_gallery),
            placeholder = painterResource(android.R.drawable.ic_menu_gallery)
        )
    }
}


/**
 * Empty directory state with optimized composition.
 */
@Composable
fun EmptyDirectoryContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
 * File type enumeration for icon and color selection.
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
 * Determines file type from name - optimized with early exit.
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
 * Returns appropriate icon for file type - optimized with when expression.
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
 * Returns accent color for file type.
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

/**
 * Spring spec optimized for Material 3 animations.
 */
private fun Material3SpringSpec(): androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.spring(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)
