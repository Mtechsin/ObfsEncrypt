package com.obfs.encrypt.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.obfs.encrypt.ui.theme.isAMOLEDTheme
import com.obfs.encrypt.ui.theme.amoledOutlinedButtonContainerColor
import com.obfs.encrypt.ui.theme.amoledOutlinedButtonContentColor
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.obfs.encrypt.R
import com.obfs.encrypt.data.PermissionHelper
import com.obfs.encrypt.security.AppPasswordManager
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
    onNavigateBack: (Int) -> Unit,
    onNavigateToHelp: () -> Unit = {}
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
    val amoledMode by viewModel.amoledMode.collectAsState()
    val appLockTimeout by viewModel.appLockTimeout.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    // Consolidated UI state
    data class SettingsUiState(
        var outputDirName: String = context.getString(R.string.not_set_source),
        var hasStoragePermission: Boolean = PermissionHelper.hasStoragePermission(context)
    )
    var uiState by remember { mutableStateOf(SettingsUiState()) }
    
    // Derived state for app directory
    val appDirectoryManager = remember { AppDirectoryManagerInstanceHolder.manager }
    val appFolderPath = remember(appDirectoryManager) { 
        appDirectoryManager?.getOutputDirectoryPath() ?: context.getString(R.string.not_available) 
    }
    val hasWriteAccess = remember(appDirectoryManager) { 
        appDirectoryManager?.hasWriteAccess() ?: false 
    }

    // App lock timeout selection dialog
    var showTimeoutDialog by remember { mutableStateOf(false) }

    // Password dialogs
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showRemovePasswordDialog by remember { mutableStateOf(false) }
    var showSecurityQuestionDialog by remember { mutableStateOf(false) }
    var showPasswordForAppLockDialog by remember { mutableStateOf(false) }

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
            uiState = uiState.copy(outputDirName = it.lastPathSegment ?: context.getString(R.string.selected_folder))
        }
    }

    val clearOutputDir = {
        viewModel.setCurrentOutputDirectory(null)
        uiState = uiState.copy(outputDirName = context.getString(R.string.not_set_source))
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
                actions = {
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = stringResource(R.string.help),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Appearance Section
            SettingsSectionHeader(title = stringResource(R.string.appearance))
            Spacer(modifier = Modifier.height(8.dp))

            ThemeSelectorCard(
                currentMode = currentThemeMode,
                currentAppTheme = currentAppTheme,
                dynamicColor = dynamicColor,
                amoledMode = amoledMode,
                onModeSelected = { viewModel.setThemeMode(it) },
                onAppThemeSelected = { viewModel.setAppTheme(it) },
                onDynamicColorChanged = { viewModel.setDynamicColor(it) },
                onAmoledModeChanged = { viewModel.setAmoledMode(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Language Selection
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clickable { showLanguageDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.titleSmall,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Storage Permission Section
            SettingsSectionHeader(title = stringResource(R.string.storage_access))
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (uiState.hasStoragePermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_permission),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.hasStoragePermission) {
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

            Spacer(modifier = Modifier.height(8.dp))

            if (!uiState.hasStoragePermission) {
                Button(
                    onClick = requestStoragePermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.grant_storage_permission), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Preferences Section
            SettingsSectionHeader(title = stringResource(R.string.security))
            Spacer(modifier = Modifier.height(8.dp))

            // Password Authentication
            PasswordSettingCard(
                onSetPassword = { showSetPasswordDialog = true },
                onChangePassword = { showChangePasswordDialog = true },
                onSetSecurityQuestion = { showSecurityQuestionDialog = true },
                onRemovePassword = { showRemovePasswordDialog = true },
                isPasswordSet = viewModel.appPasswordManager.isPasswordSet(),
                isSecurityQuestionSet = viewModel.appPasswordManager.isSecurityQuestionsEnabled()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Biometric Authentication
            BiometricSettingCard(viewModel = viewModel)

            Spacer(modifier = Modifier.height(8.dp))

            // App Lock
            AppLockSettingsCard(
                isLockEnabled = appLockEnabled,
                onToggleLock = { enabled ->
                    if (enabled) {
                        // Authenticate before enabling app lock
                        val biometricStatus = viewModel.biometricAuthManager.canAuthenticate()
                        val isBiometricAvailable = biometricStatus == com.obfs.encrypt.security.BiometricStatus.AVAILABLE
                        val isPasswordSet = viewModel.appPasswordManager.isPasswordSet()

                        if (isBiometricAvailable) {
                            // Use biometric if available
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
                        } else if (isPasswordSet) {
                            // Use password if biometric not available but password is set
                            showPasswordForAppLockDialog = true
                        }
                        // If neither biometric nor password is set, do nothing (toggle stays off)
                    } else {
                        viewModel.disableAppLock()
                    }
                },
                onSelectTimeout = { showTimeoutDialog = true },
                currentTimeout = appLockTimeout
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Keyfile Management
            KeyfileSettingCard(viewModel = viewModel)
            
            Spacer(modifier = Modifier.height(8.dp))

            // Secure Delete
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.secure_shred_originals),
                            style = MaterialTheme.typography.titleSmall,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Output Location Section
            SettingsSectionHeader(title = stringResource(R.string.output_location))
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.default_app_folder),
                        style = MaterialTheme.typography.titleSmall,
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
                        modifier = Modifier.padding(vertical = 4.dp)
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
                color = MaterialTheme.colorScheme.surfaceContainer
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
                        text = uiState.outputDirName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAMOLEDTheme()) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.hasStoragePermission,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.choose))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.hasStoragePermission,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.choose))
                            }
                        }
                        if (outputUri != null) {
                            if (isAMOLEDTheme()) {
                                OutlinedButton(
                                    onClick = clearOutputDir,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                                ) {
                                    Text(stringResource(R.string.clear))
                                }
                            } else {
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
            }

            Spacer(modifier = Modifier.height(20.dp))
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

    // Set Password Dialog
    if (showSetPasswordDialog) {
        SetPasswordDialog(
            onPasswordSet = { password ->
                viewModel.appPasswordManager.setPassword(password)
                showSetPasswordDialog = false
            },
            onDismiss = { showSetPasswordDialog = false }
        )
    }

    // Change Password Dialog
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            appPasswordManager = viewModel.appPasswordManager,
            onPasswordChanged = { newPassword ->
                viewModel.appPasswordManager.setPassword(newPassword)
                showChangePasswordDialog = false
            },
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    // Remove Password Dialog
    if (showRemovePasswordDialog) {
        RemovePasswordDialog(
            appPasswordManager = viewModel.appPasswordManager,
            biometricAuthManager = viewModel.biometricAuthManager,
            onPasswordRemoved = {
                viewModel.appPasswordManager.clearPassword()
                viewModel.biometricAuthManager.setBiometricEnabled(false)
                showRemovePasswordDialog = false
            },
            onDismiss = { showRemovePasswordDialog = false }
        )
    }

    // Security Question Dialog
    if (showSecurityQuestionDialog) {
        SecurityQuestionDialog(
            appPasswordManager = viewModel.appPasswordManager,
            onQuestionSet = { question, answer ->
                viewModel.appPasswordManager.setSecurityQuestion(question, answer)
                showSecurityQuestionDialog = false
            },
            onDismiss = { showSecurityQuestionDialog = false }
        )
    }

    // Password dialog for enabling app lock (when biometric not available)
    if (showPasswordForAppLockDialog) {
        AppLockPasswordDialog(
            appPasswordManager = viewModel.appPasswordManager,
            onAuthSuccess = {
                viewModel.enableAppLock()
                showPasswordForAppLockDialog = false
            },
            onDismiss = { showPasswordForAppLockDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.width(3.dp).height(18.dp)
        ) {}
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeSelectorCard(
    currentMode: ThemeMode,
    currentAppTheme: AppTheme,
    dynamicColor: Boolean,
    amoledMode: Boolean,
    onModeSelected: (ThemeMode) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit
) {
    // Modern card with gradient border effect
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header with icon badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon badge with gradient
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.choose_appearance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Color (Material You) toggle with animation
            DynamicColorToggle(
                enabled = dynamicColor,
                onEnabledChanged = onDynamicColorChanged
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AMOLED Mode toggle — works with ALL themes including Material You
            AmoledModeToggle(
                enabled = amoledMode,
                onEnabledChanged = onAmoledModeChanged
            )

            // Animated container for Theme Color Picker
            AnimatedVisibility(
                visible = !dynamicColor,
                enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        // Enable GPU acceleration for smoother animations
                        clip = true
                    }
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Section divider with label
                    DividerWithLabel(label = stringResource(R.string.theme_color))

                    Spacer(modifier = Modifier.height(20.dp))

                    // Theme Color Picker Section
                    Text(
                        text = stringResource(R.string.select_theme_color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeColorPicker(
                        selectedTheme = currentAppTheme,
                        onThemeSelected = onAppThemeSelected
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider
            ModernDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Mode Selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessAuto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.choose_theme_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            ThemeModeSelector(
                currentMode = currentMode,
                onModeSelected = onModeSelected
            )
        }
    }
}

@Composable
private fun DividerWithLabel(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ) {}
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ) {}
    }
}

@Composable
private fun ModernDivider() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    ) {}
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    // Animate background gradient
    val backgroundGradient = if (enabled) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
            )
        )
    }

    // Animate icon background
    val iconBackground by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "iconBackground"
    )

    // Animate icon tint
    val iconTint by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "iconTint"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { clip = true },
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (enabled) 8.dp else 2.dp,
        border = if (enabled) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundGradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon with gradient ring
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(iconBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.material_you),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = stringResource(R.string.adapt_colors),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Modern Toggle Switch
                ModernToggleSwitch(
                    enabled = enabled,
                    onEnabledChanged = onEnabledChanged
                )
            }
        }
    }
}

@Composable
private fun ModernToggleSwitch(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Switch(
        checked = enabled,
        onCheckedChange = onEnabledChanged,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun AmoledModeToggle(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    // Animate background gradient
    val backgroundGradient = if (enabled) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF1A1A2E).copy(alpha = 0.6f),
                Color(0xFF0F0F1A).copy(alpha = 0.4f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
            )
        )
    }

    // Animate icon background
    val iconBackground by animateColorAsState(
        targetValue = if (enabled) {
            Color(0xFF000000)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "amoledIconBackground"
    )

    // Animate icon tint
    val iconTint by animateColorAsState(
        targetValue = if (enabled) {
            Color(0xFFE0E0E0)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "amoledIconTint"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { clip = true },
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (enabled) 8.dp else 2.dp,
        border = if (enabled) {
            BorderStroke(2.dp, Color(0xFF333333))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundGradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pure black circle icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(iconBackground)
                            .then(
                                if (enabled) Modifier.border(2.dp, Color(0xFF444444), CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.theme_amoled),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) {
                                Color(0xFFE0E0E0)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = stringResource(R.string.amoled_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) {
                                Color(0xFFAAAAAA)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Modern Toggle Switch
                ModernToggleSwitch(
                    enabled = enabled,
                    onEnabledChanged = onEnabledChanged
                )
            }
        }
    }
}

@Composable
private fun ThemeColorPicker(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // First row - 3 colors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Default (Blue for dark, Lemon for light)
            ThemeColorOption(
                color = Color(0xFF64B5F6),
                label = stringResource(R.string.theme_default),
                isSelected = selectedTheme == AppTheme.DEFAULT,
                onClick = { onThemeSelected(AppTheme.DEFAULT) },
                modifier = Modifier.weight(1f)
            )
            // Red
            ThemeColorOption(
                color = Color(0xFFEF5350),
                label = stringResource(R.string.theme_red),
                isSelected = selectedTheme == AppTheme.RED,
                onClick = { onThemeSelected(AppTheme.RED) },
                modifier = Modifier.weight(1f)
            )
            // Green
            ThemeColorOption(
                color = Color(0xFF81C784),
                label = stringResource(R.string.theme_green),
                isSelected = selectedTheme == AppTheme.GREEN,
                onClick = { onThemeSelected(AppTheme.GREEN) },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Second row - 2 colors centered
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            // Orange
            ThemeColorOption(
                color = Color(0xFFFFB74D),
                label = stringResource(R.string.theme_orange),
                isSelected = selectedTheme == AppTheme.ORANGE,
                onClick = { onThemeSelected(AppTheme.ORANGE) },
                modifier = Modifier.weight(1f)
            )
            // Navy
            ThemeColorOption(
                color = Color(0xFF42A5F5),
                label = stringResource(R.string.theme_navy),
                isSelected = selectedTheme == AppTheme.NAVY,
                onClick = { onThemeSelected(AppTheme.NAVY) },
                modifier = Modifier.weight(1f)
            )
            // Empty spacer to balance the grid with the top row
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemeModeChip(
            title = stringResource(R.string.light),
            icon = Icons.Default.LightMode,
            isSelected = currentMode == ThemeMode.LIGHT,
            onClick = { onModeSelected(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f)
        )
        ThemeModeChip(
            title = stringResource(R.string.dark),
            icon = Icons.Default.DarkMode,
            isSelected = currentMode == ThemeMode.DARK,
            onClick = { onModeSelected(ThemeMode.DARK) },
            modifier = Modifier.weight(1f)
        )
        ThemeModeChip(
            title = stringResource(R.string.system),
            icon = Icons.Default.BrightnessAuto,
            isSelected = currentMode == ThemeMode.SYSTEM,
            onClick = { onModeSelected(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeColorOption(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate size change
    val circleSize by animateDpAsState(
        targetValue = if (isSelected) 50.dp else 48.dp,
        animationSpec = tween(150),
        label = "circleSize"
    )
    
    // Animate border width
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        animationSpec = tween(150),
        label = "borderWidth"
    )
    
    // Animate shadow elevation
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 2.dp,
        animationSpec = tween(150),
        label = "elevation"
    )
    
    // Animate label color
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "labelColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    // Enable GPU acceleration for smoother animations
                    clip = true
                },
            contentAlignment = Alignment.Center
        ) {
            // Selection ring with fade animation
            if (isSelected) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {}
            }
            
            // Color circle with animated size and border
            Surface(
                modifier = Modifier.size(circleSize),
                shape = CircleShape,
                color = color,
                border = BorderStroke(
                    borderWidth,
                    if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
                ),
                shadowElevation = elevation
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Label with animated color
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ThemeModeChip(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(150),
        label = "chipBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "chipContent"
    )

    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .graphicsLayer {
                // Enable GPU acceleration for smoother animations
                clip = true
            },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
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
    val isPasswordSet = viewModel.appPasswordManager.isPasswordSet()
    val isBiometricAvailable = biometricStatus == BiometricStatus.AVAILABLE
    
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    val statusText = when {
        !isPasswordSet -> stringResource(R.string.set_password_first)
        isBiometricAvailable -> stringResource(R.string.biometric_auth_subtitle)
        biometricStatus == BiometricStatus.NO_HARDWARE -> context.getString(R.string.not_available)
        biometricStatus == BiometricStatus.HW_UNAVAILABLE -> context.getString(R.string.not_available)
        biometricStatus == BiometricStatus.NOT_ENROLLED -> context.getString(R.string.not_available)
        else -> context.getString(R.string.not_available)
    }
    
    val isAvailable = isBiometricAvailable && isPasswordSet
    
    if (showPasswordDialog) {
        PasswordAuthDialog(
            appPasswordManager = viewModel.appPasswordManager,
            onAuthSuccess = { password ->
                viewModel.biometricAuthManager.storePasswordWithBiometric(password.toCharArray())
                viewModel.biometricAuthManager.setBiometricEnabled(true)
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false }
        )
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAvailable) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceContainerLow
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
                            if (enabled) {
                                showPasswordDialog = true
                            } else {
                                viewModel.biometricAuthManager.setBiometricEnabled(false)
                                viewModel.biometricAuthManager.clearStoredPassword()
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
private fun PasswordAuthDialog(
    appPasswordManager: AppPasswordManager,
    onAuthSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        title = {
            Text(stringResource(R.string.authenticate))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.enter_password_to_enable_biometric),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appPasswordManager.verifyPassword(password)) {
                        onAuthSuccess(password)
                    } else {
                        error = context.getString(R.string.incorrect_password)
                    }
                }
            ) {
                Text(stringResource(R.string.enable_biometric))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AppLockPasswordDialog(
    appPasswordManager: AppPasswordManager,
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val incorrectPasswordText = stringResource(R.string.incorrect_password)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        title = {
            Text(stringResource(R.string.enable_app_lock))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.enter_password_to_enable_app_lock),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appPasswordManager.verifyPassword(password)) {
                        onAuthSuccess()
                    } else {
                        error = incorrectPasswordText
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PasswordSettingCard(
    onSetPassword: () -> Unit,
    onChangePassword: () -> Unit,
    onSetSecurityQuestion: () -> Unit,
    onRemovePassword: () -> Unit,
    isPasswordSet: Boolean,
    isSecurityQuestionSet: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
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
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_password),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isPasswordSet) stringResource(R.string.password_set_desc) 
                               else stringResource(R.string.password_not_set_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isPasswordSet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAMOLEDTheme()) {
                        OutlinedButton(
                            onClick = onChangePassword,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                        ) {
                            Text(stringResource(R.string.change_password))
                        }
                        OutlinedButton(
                            onClick = onRemovePassword,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                        ) {
                            Text(stringResource(R.string.remove))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onChangePassword,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.change_password))
                        }
                        OutlinedButton(
                            onClick = onRemovePassword,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                }

                if (!isSecurityQuestionSet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isAMOLEDTheme()) {
                        OutlinedButton(
                            onClick = onSetSecurityQuestion,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                        ) {
                            Text(stringResource(R.string.set_security_question))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSetSecurityQuestion,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.set_security_question))
                        }
                    }
                }
            } else {
                Button(
                    onClick = onSetPassword,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.set_password))
                }
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
        color = MaterialTheme.colorScheme.surfaceContainer
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
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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

            if (isAMOLEDTheme()) {
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = amoledOutlinedButtonContainerColor(), contentColor = amoledOutlinedButtonContentColor())
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.generate_new_keyfile))
                }
            } else {
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
                tint = MaterialTheme.colorScheme.onPrimaryContainer
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
                tint = MaterialTheme.colorScheme.onPrimaryContainer
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

@Composable
private fun SetPasswordDialog(
    onPasswordSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        title = {
            Text(stringResource(R.string.set_password))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.isEmpty() -> {
                            error = context.getString(R.string.password_cannot_be_empty)
                        }
                        password != confirmPassword -> {
                            error = context.getString(R.string.passwords_do_not_match)
                        }
                        else -> {
                            onPasswordSet(password)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.set_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    appPasswordManager: AppPasswordManager,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        title = {
            Text(stringResource(R.string.change_password))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.current_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        !appPasswordManager.verifyPassword(currentPassword) -> {
                            error = context.getString(R.string.incorrect_password)
                        }
                        newPassword.isEmpty() -> {
                            error = context.getString(R.string.password_cannot_be_empty)
                        }
                        newPassword != confirmPassword -> {
                            error = context.getString(R.string.passwords_do_not_match)
                        }
                        else -> {
                            onPasswordChanged(newPassword)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.change_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RemovePasswordDialog(
    appPasswordManager: AppPasswordManager,
    biometricAuthManager: com.obfs.encrypt.security.BiometricAuthManager,
    onPasswordRemoved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.remove_password))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.remove_password_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.current_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appPasswordManager.verifyPassword(currentPassword)) {
                        onPasswordRemoved()
                    } else {
                        error = context.getString(R.string.incorrect_password)
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private val securityQuestions = listOf(
    "What is your mother's maiden name?",
    "What was the name of your first pet?",
    "What city were you born in?",
    "What is the name of your favorite childhood friend?",
    "What is the name of your first school?",
    "What is your favorite movie?",
    "What is your favorite color?",
    "What is the name of your favorite teacher?"
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SecurityQuestionDialog(
    appPasswordManager: AppPasswordManager,
    onQuestionSet: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedQuestion by remember { mutableStateOf(securityQuestions[0]) }
    var customQuestion by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var confirmAnswer by remember { mutableStateOf("") }
    var showCustomQuestion by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        title = {
            Text(stringResource(R.string.set_security_question))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showCustomQuestion) {
                    OutlinedTextField(
                        value = customQuestion,
                        onValueChange = { customQuestion = it },
                        label = { Text(stringResource(R.string.custom_question)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedQuestion,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_question)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            securityQuestions.forEach { question ->
                                DropdownMenuItem(
                                    text = { Text(question) },
                                    onClick = {
                                        selectedQuestion = question
                                        expanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.custom_question_option)) },
                                onClick = {
                                    showCustomQuestion = true
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.your_answer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmAnswer,
                    onValueChange = {
                        confirmAnswer = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.confirm_answer)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val question = if (showCustomQuestion) customQuestion else selectedQuestion
                    when {
                        question.isEmpty() -> {
                            error = context.getString(R.string.question_required)
                        }
                        answer.isEmpty() -> {
                            error = context.getString(R.string.answer_required)
                        }
                        answer.lowercase().trim() != confirmAnswer.lowercase().trim() -> {
                            error = context.getString(R.string.answers_do_not_match)
                        }
                        else -> {
                            onQuestionSet(question, answer)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
