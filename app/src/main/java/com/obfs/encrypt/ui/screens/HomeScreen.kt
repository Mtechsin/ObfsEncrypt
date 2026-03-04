package com.obfs.encrypt.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import com.obfs.encrypt.crypto.EncryptionMethod
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obfs.encrypt.ui.components.FilePickerLauncher
import com.obfs.encrypt.ui.components.PickType
import com.obfs.encrypt.ui.theme.Motion
import com.obfs.encrypt.ui.theme.pressClickEffect
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.delay

data class RecentActivity(
    val fileName: String,
    val date: String,
    val size: String,
    val method: String,
    val type: ActivityType
)

enum class ActivityType { ENCRYPT, DECRYPT }

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDecrypt: () -> Unit,
    onNavigateToFileBrowser: () -> Unit,
    onNavigateToProgress: (String) -> Unit
) {
    android.util.Log.d("HomeScreen", "HomeScreen COMPOSING")
    
    val navItems = listOf(
        BottomNavItem(
            title = "Encrypt",
            selectedIcon = Icons.Filled.Shield,
            unselectedIcon = Icons.Outlined.Lock
        ),
        BottomNavItem(
            title = "Files",
            selectedIcon = Icons.Filled.Folder,
            unselectedIcon = Icons.Outlined.Folder
        )
    )

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var previousTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedNavigationBar(
                items = navItems,
                selectedIndex = selectedTabIndex,
                onItemSelected = { index ->
                    previousTabIndex = selectedTabIndex
                    selectedTabIndex = index
                }
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = selectedTabIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1

                val enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(
                    animationSpec = tween(220, delayMillis = 50)
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )

                val exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                    animationSpec = tween(200)
                ) + fadeOut(
                    animationSpec = tween(200)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(200)
                )

                enter togetherWith exit using SizeTransform(clip = false)
            },
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            label = "tab_transition"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> EncryptTabContent(
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDecrypt = onNavigateToDecrypt,
                    onNavigateToFileBrowser = onNavigateToFileBrowser,
                    onNavigateToProgress = onNavigateToProgress,
                    viewModel = viewModel
                )
                1 -> FileBrowserScreen(
                    onNavigateBack = { selectedTabIndex = 0 },
                    onNavigateToProgress = onNavigateToProgress,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavigationBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val density = LocalDensity.current
    var barWidth by remember { mutableStateOf(0) }
    val itemWidth = if (items.isNotEmpty() && barWidth > 0) barWidth / items.size else 0
    val indicatorOffsetPx by animateFloatAsState(
        targetValue = (selectedIndex * itemWidth + itemWidth / 2).toFloat(),
        animationSpec = Motion.IndicatorSlideSpring,
        label = "indicator_offset"
    )
    val pillWidth = 64.dp
    val pillHeight = 32.dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onGloballyPositioned { coordinates ->
                    barWidth = coordinates.size.width
                }
        ) {
            if (barWidth > 0) {
                val pillWidthPx = with(density) { pillWidth.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (indicatorOffsetPx - pillWidthPx / 2f).toInt(),
                                y = with(density) { 14.dp.roundToPx() }
                            )
                        }
                        .size(width = pillWidth, height = pillHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    AnimatedNavItem(
                        item = item,
                        isSelected = selectedIndex == index,
                        onClick = { onItemSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bounceKey by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (bounceKey > 0) 0.82f else 1f,
        animationSpec = Motion.NavIconBounceSpring,
        label = "nav_icon_scale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (bounceKey > 0) 12f else 0f,
        animationSpec = Motion.NavIconWiggleSpring,
        label = "nav_icon_rotation"
    )

    LaunchedEffect(bounceKey) {
        if (bounceKey > 0) {
            delay(100)
            bounceKey = 0
        }
    }

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "nav_icon_tint"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.6f,
        animationSpec = tween(250),
        label = "label_alpha"
    )
    val labelOffsetY by animateFloatAsState(
        targetValue = if (isSelected) 0f else 3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "label_offset"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                bounceKey++
                onClick()
            }
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isSelected,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.6f, animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ))) togetherWith
                            (fadeOut(animationSpec = tween(150)) +
                                    scaleOut(targetScale = 0.6f, animationSpec = tween(150)))
                },
                label = "icon_crossfade"
            ) { selected ->
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = iconTint.copy(alpha = labelAlpha),
            modifier = Modifier
                .graphicsLayer {
                    translationY = labelOffsetY
                    alpha = labelAlpha
                }
        )
    }
}

@Composable
private fun EncryptTabContent(
    onNavigateToSettings: () -> Unit,
    onNavigateToDecrypt: () -> Unit,
    onNavigateToFileBrowser: () -> Unit,
    onNavigateToProgress: (String) -> Unit,
    viewModel: MainViewModel
) {
    var pickType by remember { mutableStateOf(PickType.NONE) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf(EncryptionMethod.STANDARD) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingShowMethod by remember { mutableStateOf(false) }
    var pendingShowPasswordFromMethod by remember { mutableStateOf(false) }
    var selectedOutputUri by remember { mutableStateOf<Uri?>(null) }


    // Handle dialog sequencing to avoid race conditions
    LaunchedEffect(pendingShowMethod) {
        if (pendingShowMethod) {
            delay(100) // Delay to allow OutputLocationDialog to fully dismiss
            showMethodDialog = true
            pendingShowMethod = false
        }
    }

    LaunchedEffect(pendingShowPasswordFromMethod) {
        if (pendingShowPasswordFromMethod) {
            delay(100) // Delay to allow EncryptionMethodDialog to fully dismiss
            showPasswordDialog = true
            pendingShowPasswordFromMethod = false
        }
    }

    FilePickerLauncher(pickType = pickType) { uris ->
        pickType = PickType.NONE
        selectedUris = uris
        showOutputDialog = true
    }

    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedOutputUri = it
        }
    }

    if (showOutputDialog) {
        android.util.Log.d("HomeScreen", "Attempting to render OutputLocationDialog from HomeScreen")
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
                pendingShowMethod = true
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
            uris = selectedUris,
            isFolder = selectedUris.size == 1 && selectedUris.first().path?.contains("tree") == true,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password, deleteOriginal ->
                showPasswordDialog = false
                val isFolder = selectedUris.size == 1 && selectedUris.first().path?.contains("tree") == true
                if (isFolder) {
                    viewModel.encryptFolderTree(selectedUris.first(), password, selectedMethod, deleteOriginal)
                } else {
                    viewModel.encryptFiles(selectedUris, password, selectedMethod, deleteOriginal)
                }
                val intentType = if (isFolder) "folder" else "files"
                onNavigateToProgress(intentType)
            },
            showDeleteOption = true,
            viewModel = viewModel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ModernHeroHeader(encryptedCount = 0)

        SectionHeader(
            title = "Quick Actions",
            subtitle = "Select files or folders to encrypt"
        )

        ModernQuickActionGrid(
            onSingleFile = { pickType = PickType.SINGLE },
            onMultiFile = { pickType = PickType.MULTIPLE },
            onFolder = { pickType = PickType.FOLDER }
        )

        ModernToolsSection(
            onDecryptClick = onNavigateToDecrypt,
            onSettingsClick = onNavigateToSettings
        )

        SectionHeader(
            title = "Recent Activity",
            subtitle = "Your encryption history"
        )

        RecentActivitySection(
            activities = emptyList()
        )

        SectionHeader(
            title = "Security Tips",
            subtitle = "Best practices for file protection"
        )

        SecurityTipsCard()

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun ModernHeroHeader(encryptedCount: Int) {
    val density = LocalDensity.current
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Obfs Encrypt",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = with(density) { (-0.5).dp.toSp() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Military-grade encryption for your files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Column {
                            Text(
                                text = encryptedCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Files Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Column {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Protection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTabContent(
    onNavigateToProgress: (String) -> Unit,
    viewModel: MainViewModel
) {
    DeviceFoldersScreen(
        onNavigateToProgress = onNavigateToProgress,
        viewModel = viewModel
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ModernQuickActionGrid(
    onSingleFile: () -> Unit,
    onMultiFile: () -> Unit,
    onFolder: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModernQuickActionCard(
                title = "Single File",
                subtitle = "Encrypt one file",
                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onSingleFile,
                modifier = Modifier.weight(1f)
            )
            ModernQuickActionCard(
                title = "Multiple",
                subtitle = "Batch encrypt",
                icon = Icons.Outlined.FolderZip,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onMultiFile,
                modifier = Modifier.weight(1f)
            )
        }

        ModernWideActionCard(
            title = "Entire Folder",
            subtitle = "Encrypt all files recursively",
            icon = Icons.Outlined.CreateNewFolder,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onFolder
        )
    }
}

@Composable
private fun ModernQuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ModernWideActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = contentColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Surface(
                color = contentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ModernToolsSection(
    onDecryptClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            onClick = onDecryptClick,
            modifier = Modifier
                .fillMaxWidth()
                .pressClickEffect(),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
                defaultElevation = 1.dp,
                pressedElevation = 6.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Decrypt Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Unlock .obfs secure archives",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                }
            }
        }

        ElevatedCard(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .pressClickEffect(),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 4.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Application Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun RecentActivitySection(
    activities: List<RecentActivity>
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {
        if (activities.isEmpty()) {
            EmptyActivityState()
        } else {
            ActivityList(activities = activities)
        }
    }
}

@Composable
private fun EmptyActivityState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(72.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No recent activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your encryption and decryption history will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ActivityList(activities: List<RecentActivity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        activities.take(5).forEachIndexed { index, activity ->
            ActivityItem(activity = activity)
            if (index < activities.size - 1 && index < 4) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ActivityItem(activity: RecentActivity) {
    val icon = if (activity.type == ActivityType.ENCRYPT) {
        Icons.Default.Lock
    } else {
        Icons.Default.LockOpen
    }
    val iconColor = if (activity.type == ActivityType.ENCRYPT) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val containerColor = if (activity.type == ActivityType.ENCRYPT) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${activity.date}  ${activity.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = activity.method,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SecurityTipsCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = "Security Best Practices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val tips = listOf(
                "Use strong, unique passwords for each encryption",
                "Store encrypted backups in multiple locations",
                "Never share your encryption passwords via email or chat",
                "Regularly verify your encrypted files are accessible"
            )

            tips.forEachIndexed { index, tip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// Legacy composables for backward compatibility

@Composable
private fun HeroHeader(encryptedCount: Int) {
    val density = LocalDensity.current
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Obfs Encrypt",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = with(density) { (-0.5).dp.toSp() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Military-grade encryption for your files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Column {
                            Text(
                                text = encryptedCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Files Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Column {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Protection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    visible: Boolean,
    onSingleFile: () -> Unit,
    onMultiFile: () -> Unit,
    onFolder: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                ),
                modifier = Modifier.weight(1f)
            ) {
                QuickActionCard(
                    title = "Single File",
                    subtitle = "Encrypt one file",
                    icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onSingleFile
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                ),
                modifier = Modifier.weight(1f)
            ) {
                QuickActionCard(
                    title = "Multiple",
                    subtitle = "Batch encrypt",
                    icon = Icons.Outlined.FolderZip,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onMultiFile
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
        ) {
            WideActionCard(
                title = "Entire Folder",
                subtitle = "Encrypt all files recursively",
                icon = Icons.Outlined.CreateNewFolder,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onFolder
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WideActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = contentColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Surface(
                color = contentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun DecryptCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Decrypt Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unlock .obfs secure archives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SettingsCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Application Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun BrowseFilesCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickEffect(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Browse Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Quick access to common folders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}
