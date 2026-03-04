package com.obfs.encrypt.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.PermissionHelper
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.security.BiometricResult
import com.obfs.encrypt.security.BiometricStatus
import com.obfs.encrypt.ui.theme.ThemeMode
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as androidx.appcompat.app.AppCompatActivity
    val shredOriginals by viewModel.secureDeleteOriginals.collectAsState()
    val outputUri by viewModel.currentOutputUri.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    var outputDirName by remember { mutableStateOf("Not set (uses source directory)") }
    var hasStoragePermission by remember { mutableStateOf(PermissionHelper.hasStoragePermission(context)) }
    
    val appDirectoryManager = remember { AppDirectoryManagerInstanceHolder.manager }
    val appFolderPath = remember { appDirectoryManager?.getOutputDirectoryPath() ?: "Not available" }
    val hasWriteAccess = remember { appDirectoryManager?.hasWriteAccess() ?: false }

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
            outputDirName = it.lastPathSegment ?: "Selected folder"
        }
    }

    val clearOutputDir = {
        viewModel.setCurrentOutputDirectory(null)
        outputDirName = "Not set (uses source directory)"
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
                        "Settings",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingsSectionHeader(title = "Appearance")
            Spacer(modifier = Modifier.height(12.dp))

            ThemeSelectorCard(
                currentMode = currentThemeMode,
                onModeSelected = { viewModel.setThemeMode(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Storage Permission Section
            SettingsSectionHeader(title = "Storage Access")
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
                            text = "Storage Permission",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasStoragePermission) {
                                "Granted - App can access files"
                            } else {
                                "Required - Grant to encrypt/decrypt files"
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
                    Text("Grant Storage Permission")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Preferences Section
            SettingsSectionHeader(title = "Security")
            Spacer(modifier = Modifier.height(12.dp))

            // Biometric Authentication
            BiometricSettingCard(viewModel = viewModel)
            
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
                            text = "Secure Shred Originals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Overwrite source files with random data 3 times after successful operation.",
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
            SettingsSectionHeader(title = "Output Location")
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
                        text = "Default App Folder",
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
                            "App can write to this folder"
                        } else {
                            "Grant storage permission to enable this folder"
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
                        text = "Custom Output Directory",
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
                            Text("Choose")
                        }
                        if (outputUri != null) {
                            OutlinedButton(
                                onClick = clearOutputDir,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
    onModeSelected: (ThemeMode) -> Unit
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
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your preferred appearance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            ThemeOption(
                title = "Light",
                icon = Icons.Default.LightMode,
                isSelected = currentMode == ThemeMode.LIGHT,
                onClick = { onModeSelected(ThemeMode.LIGHT) }
            )
            ThemeOption(
                title = "Dark",
                icon = Icons.Default.DarkMode,
                isSelected = currentMode == ThemeMode.DARK,
                onClick = { onModeSelected(ThemeMode.DARK) }
            )
            ThemeOption(
                title = "System",
                subtitle = "Follow device settings",
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
private fun BiometricSettingCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as androidx.appcompat.app.AppCompatActivity
    val scope = rememberCoroutineScope()
    
    val biometricStatus = viewModel.biometricAuthManager.canAuthenticate()
    val isEnabled = viewModel.biometricAuthManager.isBiometricEnabled()
    var showEnrollmentPrompt by remember { mutableStateOf(false) }
    
    val statusText = when (biometricStatus) {
        BiometricStatus.AVAILABLE -> "Use fingerprint or face to unlock"
        BiometricStatus.NO_HARDWARE -> "Biometric hardware not available"
        BiometricStatus.HW_UNAVAILABLE -> "Biometric hardware unavailable"
        BiometricStatus.NOT_ENROLLED -> "No biometric enrolled on device"
        BiometricStatus.UNKNOWN -> "Biometric status unknown"
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
                        text = "Biometric Authentication",
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
                                    title = "Enable Biometric",
                                    subtitle = "Authenticate to enable biometric protection"
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
    var showGenerateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                        text = "Keyfile Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Generate a secure keyfile for authentication",
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
                Text("Generate New Keyfile")
            }
        }
    }
}

object AppDirectoryManagerInstanceHolder {
    var manager: com.obfs.encrypt.data.AppDirectoryManager? = null
    var viewModel: MainViewModel? = null
}
