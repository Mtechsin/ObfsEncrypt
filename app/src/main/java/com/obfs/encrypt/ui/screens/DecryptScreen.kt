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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var pendingShowPassword by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Handle dialog sequencing to avoid race conditions
    LaunchedEffect(pendingShowPassword) {
        if (pendingShowPassword) {
            delay(100) // Delay to allow OutputLocationDialog to fully dismiss
            showPasswordDialog = true
            pendingShowPassword = false
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
        }
    }

    if (showOutputDialog) {
        android.util.Log.d("DecryptScreen", "Attempting to render OutputLocationDialog from DecryptScreen")
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
                pendingShowPassword = true
            }
        )
    }

    if (showPasswordDialog) {
        PasswordDialog(
             uris = selectedUris,
             isFolder = false,
             onDismiss = { showPasswordDialog = false },
             onConfirm = { password, deleteOriginal ->
                  showPasswordDialog = false
                  viewModel.decryptFiles(selectedUris, password, deleteOriginal)
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
                        "Decrypt Files", 
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
                 .padding(horizontal = 24.dp)
        ) {
             Spacer(modifier = Modifier.height(16.dp))
             
             AnimatedVisibility(
                 visible = visible,
                 enter = fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy))
             ) {
                 ActionCard(
                     title = "Select .obfs files",
                     subtitle = "Pick one or more strictly encrypted files to unlock.",
                     icon = Icons.Default.FileOpen,
                     onClick = { pickType = PickType.MULTIPLE },
                     modifier = Modifier.pressClickEffect()
                 )
             }
        }
    }
}

