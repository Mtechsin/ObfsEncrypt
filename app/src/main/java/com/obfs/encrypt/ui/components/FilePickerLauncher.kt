package com.obfs.encrypt.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * FilePickerLauncher Component using Storage Access Framework (SAF)
 * 
 * Why this approach:
 * Uses ActivityResultContracts which integrates seamlessly into the Compose lifecycle.
 * SAF is robust across Android 7 to 16, bypassing scoped storage restrictions naturally
 * without requesting raw MANAGE_EXTERNAL_STORAGE permissions.
 */
@Composable
fun FilePickerLauncher(
    pickType: PickType,
    onResult: (List<Uri>) -> Unit
) {
    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onResult(listOf(uri))
    }

    val multipleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onResult(uris)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onResult(listOf(uri))
    }

    LaunchedEffect(pickType) {
        when (pickType) {
            PickType.SINGLE -> singleFileLauncher.launch(arrayOf("*/*"))
            PickType.MULTIPLE -> multipleFileLauncher.launch(arrayOf("*/*"))
            PickType.FOLDER -> folderLauncher.launch(null)
            PickType.NONE -> { /* No-op until requested */ }
        }
    }
}

enum class PickType {
    NONE, SINGLE, MULTIPLE, FOLDER
}
