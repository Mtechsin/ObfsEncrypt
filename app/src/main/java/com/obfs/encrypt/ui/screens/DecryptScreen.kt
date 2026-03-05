package com.obfs.encrypt.ui.screens

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.R
import com.obfs.encrypt.security.BiometricResult
import com.obfs.encrypt.ui.components.ActionCard
import com.obfs.encrypt.ui.components.FilePickerLauncher
import com.obfs.encrypt.ui.components.PickType
import com.obfs.encrypt.ui.theme.pressClickEffect
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecryptScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    var pickType by remember { mutableStateOf(PickType.NONE) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var selectedOutputUri by remember { mutableStateOf<Uri?>(null) }
    var dialogStep by remember { mutableIntStateOf(0) } // 0=none, 1=output, 2=password
    var visible by remember { mutableStateOf(false) }
    
    // Biometric quick decrypt
    val biometricAvailable = viewModel.biometricAuthManager.canAuthenticate() ==
            com.obfs.encrypt.security.BiometricStatus.AVAILABLE
    val hasStoredPassword = viewModel.biometricAuthManager.hasStoredPassword()
    val isBiometricEnabled = viewModel.biometricAuthManager.isBiometricEnabled()
    var isAuthenticating by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Step 1: File selected, show output dialog
    LaunchedEffect(selectedUris) {
        if (selectedUris.isNotEmpty()) {
            dialogStep = 1
        }
    }

    // Handle dialog sequencing
    LaunchedEffect(dialogStep) {
        when (dialogStep) {
            2 -> {
                delay(150)
                
                // If biometric quick decrypt is enabled and files are selected, authenticate immediately
                if (biometricAvailable && hasStoredPassword && isBiometricEnabled && selectedUris.isNotEmpty()) {
                    // Authenticate and decrypt
                    isAuthenticating = true
                    biometricError = null

                    val result = viewModel.biometricAuthManager.authenticate(
                        activity = activity as androidx.fragment.app.FragmentActivity,
                        title = activity.getString(R.string.quick_decrypt),
                        subtitle = activity.getString(R.string.auth_stored_password_subtitle)
                    )

                    when (result) {
                        is BiometricResult.Success -> {
                            val storedPassword = viewModel.getStoredPassword()
                            if (storedPassword != null) {
                                // Start decryption with stored password
                                viewModel.decryptFiles(selectedUris, storedPassword, deleteOriginal = false)
                                selectedUris = emptyList()
                                onNavigateToProgress("decrypt")
                            } else {
                                biometricError = activity.getString(R.string.failed)
                                isAuthenticating = false
                                showPasswordDialog = true
                            }
                        }
                        is BiometricResult.Cancelled,
                        is BiometricResult.UserCancelled -> {
                            biometricError = activity.getString(R.string.cancel)
                            isAuthenticating = false
                            showPasswordDialog = true
                        }
                        is BiometricResult.NotEnrolled -> {
                            biometricError = activity.getString(R.string.not_available)
                            isAuthenticating = false
                            showPasswordDialog = true
                        }
                        is BiometricResult.Error -> {
                            biometricError = result.message
                            isAuthenticating = false
                            showPasswordDialog = true
                        }
                        else -> {
                            biometricError = activity.getString(R.string.failed)
                            isAuthenticating = false
                            showPasswordDialog = true
                        }
                    }
                } else {
                    showPasswordDialog = true
                }
            }
        }
    }

    FilePickerLauncher(pickType = pickType) { uris ->
        pickType = PickType.NONE
        selectedUris = uris
        showOutputDialog = true
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            activity.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedOutputUri = it
            // Auto-advance after folder selection
            dialogStep = 2
            showOutputDialog = false
        }
    }

    if (showOutputDialog) {
        val currentOutputUri by viewModel.currentOutputUri.collectAsState()

        OutputLocationDialog(
            currentOutputUri = currentOutputUri,
            selectedUri = selectedOutputUri,
            onSelectCustomFolder = { folderPickerLauncher.launch(null) },
            onClearCustomFolder = {
                selectedOutputUri = null
                dialogStep = 2
                showOutputDialog = false
            },
            onDismiss = {
                showOutputDialog = false
                selectedUris = emptyList()
                dialogStep = 0
            },
            onConfirm = { uri ->
                viewModel.setCurrentOutputDirectory(uri)
                showOutputDialog = false
                dialogStep = 2
            }
        )
    }

    if (showPasswordDialog) {
        PasswordDialog(
             uris = selectedUris,
             isFolder = false,
             onDismiss = {
                 showPasswordDialog = false
                 dialogStep = 1
                 showOutputDialog = true
             },
             onConfirm = { password, deleteOriginal ->
                  showPasswordDialog = false
                  dialogStep = 0
                  viewModel.decryptFiles(selectedUris, password, deleteOriginal)
                  selectedUris = emptyList()
                  onNavigateToProgress("decrypt")
             },
             showDeleteOption = true,
             isDecryption = true,
             viewModel = viewModel
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.decrypt_files),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                 .padding(horizontal = 24.dp)
                 .verticalScroll(rememberScrollState())
        ) {
             Spacer(modifier = Modifier.height(16.dp))

             // Quick Decrypt with Biometric (if available)
             if (biometricAvailable && hasStoredPassword && isBiometricEnabled && selectedUris.isEmpty()) {
                 ElevatedCard(
                     modifier = Modifier.fillMaxWidth(),
                     shape = MaterialTheme.shapes.large
                 ) {
                     Column(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(16.dp)
                     ) {
                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             Row(
                                 verticalAlignment = Alignment.CenterVertically,
                                 modifier = Modifier.weight(1f)
                             ) {
                                 Icon(
                                     imageVector = Icons.Filled.Fingerprint,
                                     contentDescription = null,
                                     tint = MaterialTheme.colorScheme.primary,
                                     modifier = Modifier.size(32.dp)
                                 )
                                 Spacer(modifier = Modifier.width(12.dp))
                                 Column {
                                     Text(
                                         text = stringResource(R.string.quick_decrypt),
                                         style = MaterialTheme.typography.titleMedium,
                                         fontWeight = FontWeight.Bold
                                     )
                                     Text(
                                         text = stringResource(R.string.quick_decrypt_subtitle),
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant
                                     )
                                 }
                             }
                         }
                         
                         Spacer(modifier = Modifier.height(12.dp))
                         
                         // Show error if biometric failed
                         if (biometricError != null) {
                             Text(
                                 text = biometricError!!,
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.error,
                                 modifier = Modifier.padding(bottom = 8.dp)
                             )
                         }
                         
                         Text(
                             text = stringResource(R.string.quick_decrypt_description),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.padding(bottom = 12.dp)
                         )
                         
                         Button(
                             onClick = { pickType = PickType.MULTIPLE },
                             modifier = Modifier.fillMaxWidth()
                         ) {
                             Icon(
                                 imageVector = Icons.Filled.FileOpen,
                                 contentDescription = null,
                                 modifier = Modifier.padding(end = 8.dp)
                             )
                             Text(stringResource(R.string.select_files_to_decrypt))
                         }
                     }
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
             }

             AnimatedVisibility(
                 visible = visible,
                 enter = fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy))
             ) {
                 ActionCard(
                     title = stringResource(R.string.select_obfs_files),
                     subtitle = stringResource(R.string.select_obfs_files_subtitle),
                     icon = Icons.Default.FileOpen,
                     onClick = { pickType = PickType.MULTIPLE },
                     modifier = Modifier.pressClickEffect()
                 )
             }
        }
    }
}
