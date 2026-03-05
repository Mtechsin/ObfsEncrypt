package com.obfs.encrypt.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.obfs.encrypt.R
import com.obfs.encrypt.data.PermissionHelper
import com.obfs.encrypt.security.BiometricResult
import com.obfs.encrypt.security.BiometricStatus
import com.obfs.encrypt.ui.components.AppLockSettingsCard
import com.obfs.encrypt.ui.theme.AppTheme
import com.obfs.encrypt.ui.theme.ThemeMode
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    previousTabIndex: Int = 0,
    onNavigateBack: (Int) -> Unit
) {
    val context = LocalContext.current
    val activity = context as androidx.appcompat.app.AppCompatActivity
    val scope = rememberCoroutineScope()
    val shredOriginals by viewModel.secureDeleteOriginals.collectAsState()
    val outputUri by viewModel.currentOutputUri.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val currentAppTheme by viewModel.appTheme.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockTimeout by viewModel.appLockTimeout.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    var outputDirName by remember { mutableStateOf(context.getString(R.string.not_set_source)) }
    var hasStoragePermission by remember { mutableStateOf(PermissionHelper.hasStoragePermission(context)) }

    val appDirectoryManager = remember { AppDirectoryManagerInstanceHolder.manager }
    val appFolderPath = remember { appDirectoryManager?.getOutputDirectoryPath() ?: context.getString(R.string.not_available) }
    val hasWriteAccess = remember { appDirectoryManager?.hasWriteAccess() ?: false }

    // App lock timeout selection dialog
    var showTimeoutDialog by remember { mutableStateOf(false) }

    // Language selection dialog
    var showLanguageDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.setCurrentOutputDirectory(it)
            activity.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            outputDirName = it.lastPathSegment ?: context.getString(R.string.selected_folder)
        }
    }

    val clearOutputDir = {
        viewModel.setCurrentOutputDirectory(null)
        outputDirName = context.getString(R.string.not_set_source)
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = PermissionHelper.getStoragePermissionIntent(context)
            context.startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                100
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(previousTabIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Appearance Section
            SettingsSectionHeader(title = stringResource(R.string.appearance))
            Spacer(modifier = Modifier.height(12.dp))

            ThemeSelectorCard(
                currentMode = currentThemeMode,
                currentAppTheme = currentAppTheme,
                dynamicColor = dynamicColor,
                onModeSelected = { viewModel.setThemeMode(it) },
                onAppThemeSelected = { viewModel.setAppTheme(it) },
                onDynamicColorChanged = { viewModel.setDynamicColor(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Language Selection
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable { showLanguageDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (currentLanguage) {
                                "en" -> stringResource(R.string.language_english)
                                "ar" -> stringResource(R.string.language_arabic)
                                else -> stringResource(R.string.language_system)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Storage Permission Section
            SettingsSectionHeader(title = stringResource(R.string.storage_access))
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (hasStoragePermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_permission),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasStoragePermission) {
                                stringResource(R.string.granted_access)
                            } else {
                                stringResource(R.string.required_access)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasStoragePermission) {
                Button(
                    onClick = requestStoragePermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.grant_storage_permission))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Preferences Section
            SettingsSectionHeader(title = stringResource(R.string.security))
            Spacer(modifier = Modifier.height(12.dp))

            // Biometric Authentication
            BiometricSettingCard(viewModel = viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // App Lock
            AppLockSettingsCard(
                isLockEnabled = appLockEnabled,
                onToggleLock = { enabled ->
                    if (enabled) {
                        // Authenticate before enabling app lock
                        scope.launch {
                            val result = viewModel.biometricAuthManager.authenticate(
                                activity = activity,
                                title = context.getString(R.string.enable_app_lock),
                                subtitle = context.getString(R.string.app_lock_subtitle)
                            )
                            if (result is BiometricResult.Success) {
                                viewModel.enableAppLock()
                            }
                        }
                    } else {
                        viewModel.disableAppLock()
                    }
                },
                onSelectTimeout = { showTimeoutDialog = true },
                currentTimeout = appLockTimeout
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Keyfile Management
            KeyfileSettingCard(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(12.dp))

            // Secure Delete
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.secure_shred_originals),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.shred_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = shredOriginals,
                        onCheckedChange = { viewModel.toggleSecureDelete(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Output Location Section
            SettingsSectionHeader(title = stringResource(R.string.output_location))
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.default_app_folder),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = appFolderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasWriteAccess) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        text = if (hasWriteAccess) {
                            stringResource(R.string.app_can_write)
                        } else {
                            stringResource(R.string.grant_to_enable_folder)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasWriteAccess) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.custom_output_directory),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = outputDirName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            enabled = hasStoragePermission,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.choose))
                        }
                        if (outputUri != null) {
                            OutlinedButton(
                                onClick = clearOutputDir,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // App Lock Timeout Selection Dialog
    if (showTimeoutDialog) {
        AppLockTimeoutDialog(
            currentTimeout = appLockTimeout,
            onTimeoutSelected = { timeout ->
                viewModel.setAppLockTimeout(timeout)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ThemeSelectorCard(
    currentMode: ThemeMode,
    currentAppTheme: AppTheme,
    dynamicColor: Boolean,
    onModeSelected: (ThemeMode) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.choose_appearance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Color (Material You) toggle
            DynamicColorToggle(
                enabled = dynamicColor,
                onEnabledChanged = onDynamicColorChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Color Picker
            Text(
                text = stringResource(R.string.theme_color),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.select_theme_color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            ThemeColorPicker(
                selectedTheme = currentAppTheme,
                onThemeSelected = onAppThemeSelected
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThemeOption(
                title = stringResource(R.string.light),
                icon = Icons.Default.LightMode,
                isSelected = currentMode == ThemeMode.LIGHT,
                onClick = { onModeSelected(ThemeMode.LIGHT) }
            )
            ThemeOption(
                title = stringResource(R.string.dark),
                icon = Icons.Default.DarkMode,
                isSelected = currentMode == ThemeMode.DARK,
                onClick = { onModeSelected(ThemeMode.DARK) }
            )
            ThemeOption(
                title = stringResource(R.string.system),
                subtitle = stringResource(R.string.follow_device_settings),
                icon = Icons.Default.BrightnessAuto,
                isSelected = currentMode == ThemeMode.SYSTEM,
                onClick = { onModeSelected(ThemeMode.SYSTEM) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.material_you),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.adapt_colors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )
        }
    }
}

@Composable
private fun ThemeColorPicker(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Default (Blue for dark, Lemon for light)
        ThemeColorOption(
            color = Color(0xFF64B5F6),
            isSelected = selectedTheme == AppTheme.DEFAULT,
            onClick = { onThemeSelected(AppTheme.DEFAULT) }
        )
        // Red
        ThemeColorOption(
            color = Color(0xFFEF5350),
            isSelected = selectedTheme == AppTheme.RED,
            onClick = { onThemeSelected(AppTheme.RED) }
        )
        // Green
        ThemeColorOption(
            color = Color(0xFF81C784),
            isSelected = selectedTheme == AppTheme.GREEN,
            onClick = { onThemeSelected(AppTheme.GREEN) }
        )
        // Orange
        ThemeColorOption(
            color = Color(0xFFFFB74D),
            isSelected = selectedTheme == AppTheme.ORANGE,
            onClick = { onThemeSelected(AppTheme.ORANGE) }
        )
        // Navy
        ThemeColorOption(
            color = Color(0xFF42A5F5),
            isSelected = selectedTheme == AppTheme.NAVY,
            onClick = { onThemeSelected(AppTheme.NAVY) }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AMOLED (Pure black with neon accents)
        ThemeColorOption(
            color = Color(0xFF7C4DFF),
            isSelected = selectedTheme == AppTheme.AMOLED,
            onClick = { onThemeSelected(AppTheme.AMOLED) }
        )
    }
}

@Composable
private fun ThemeColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderStroke = if (isSelected) 3.dp else 0.dp
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(borderStroke, borderColor)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometricSettingCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as androidx.appcompat.app.AppCompatActivity
    val scope = rememberCoroutineScope()
    
    val biometricStatus = viewModel.biometricAuthManager.canAuthenticate()
    val isEnabled = viewModel.biometricAuthManager.isBiometricEnabled()
    
    val statusText = when (biometricStatus) {
        BiometricStatus.AVAILABLE -> stringResource(R.string.biometric_auth_subtitle)
        BiometricStatus.NO_HARDWARE -> context.getString(R.string.not_available) // Could add more specific strings if needed
        BiometricStatus.HW_UNAVAILABLE -> context.getString(R.string.not_available)
        BiometricStatus.NOT_ENROLLED -> context.getString(R.string.not_available)
        BiometricStatus.UNKNOWN -> context.getString(R.string.not_available)
    }
    
    val isAvailable = biometricStatus == BiometricStatus.AVAILABLE
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAvailable) MaterialTheme.colorScheme.surfaceContainerHighest 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = if (isAvailable) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.biometric_authentication),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = isEnabled && isAvailable,
                    onCheckedChange = { enabled ->
                        if (isAvailable) {
                            scope.launch {
                                // Authenticate before enabling
                                val result = viewModel.biometricAuthManager.authenticate(
                                    activity = activity,
                                    title = context.getString(R.string.enable_biometric),
                                    subtitle = context.getString(R.string.biometric_auth_subtitle)
                                )
                                if (result is BiometricResult.Success) {
                                    viewModel.biometricAuthManager.setBiometricEnabled(enabled)
                                }
                            }
                        }
                    },
                    enabled = isAvailable
                )
            }
        }
    }
}

@Composable
private fun KeyfileSettingCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    
    // Folder picker for saving generated keyfile
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.generateKeyfile(it)
            (context as androidx.appcompat.app.AppCompatActivity).contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.keyfile_management),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.keyfile_management_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Default.Security, 
                    contentDescription = null, 
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.generate_new_keyfile))
            }
        }
    }
}

object AppDirectoryManagerInstanceHolder {
    var manager: com.obfs.encrypt.data.AppDirectoryManager? = null
    var viewModel: MainViewModel? = null
}

/**
 * Dialog for selecting app lock timeout.
 */
@Composable
private fun AppLockTimeoutDialog(
    currentTimeout: Long,
    onTimeoutSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.auto_lock_timeout))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val options = listOf(
                    0L to stringResource(R.string.immediately),
                    5_000L to stringResource(R.string.five_seconds),
                    15_000L to stringResource(R.string.fifteen_seconds),
                    30_000L to stringResource(R.string.thirty_seconds),
                    60_000L to stringResource(R.string.one_minute),
                    300_000L to stringResource(R.string.five_minutes),
                    900_000L to stringResource(R.string.fifteen_minutes)
                )
                
                options.forEach { (timeout, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTimeoutSelected(timeout) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTimeout == timeout,
                            onClick = { onTimeoutSelected(timeout) }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.language))
        },
        text = {
            Column {
                val languages = listOf(
                    "system" to stringResource(R.string.language_system),
                    "en" to stringResource(R.string.language_english),
                    "ar" to stringResource(R.string.language_arabic)
                )
                
                languages.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == code,
                            onClick = { onLanguageSelected(code) }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
