package com.obfs.encrypt.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Controlled file picker that can be shown/hidden programmatically.
 * Used for selecting keyfiles in the password dialog.
 */
@Composable
fun KeyfilePicker(
    showPicker: Boolean,
    onDismiss: () -> Unit,
    onKeyfileSelected: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onKeyfileSelected(uri)
        }
        onDismiss()
    }

    LaunchedEffect(showPicker) {
        if (showPicker) {
            launcher.launch(arrayOf("*/*"))
        }
    }
}
