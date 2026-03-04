package com.obfs.encrypt.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import com.obfs.encrypt.ui.theme.pressClickEffect

/**
 * Permission Dialog - Explains why storage permission is needed
 * 
 * Shows on first launch to request file access permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDialog(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit = {},
    showDismiss: Boolean = false
) {
    android.util.Log.d("PermissionDialog", "PermissionDialog COMPOSING")
    AlertDialog(
        onDismissRequest = { if (showDismiss) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Storage Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Obfs Encrypt needs access to your device storage to encrypt and decrypt files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                PermissionFeatureItem(
                    icon = Icons.Default.Folder,
                    title = "Access Files",
                    description = "Select and read files to encrypt"
                )
                
                PermissionFeatureItem(
                    icon = Icons.Default.Lock,
                    title = "Save Encrypted Files",
                    description = "Write encrypted files to your chosen location"
                )
                
                PermissionFeatureItem(
                    icon = Icons.Default.Storage,
                    title = "Manage Output Folder",
                    description = "Create and manage the ObfsEncrypt output directory"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        "Tap \"Grant Permission\" to open settings and enable \"Allow management of all files\"."
                    } else {
                        "Tap \"Grant Permission\" to allow file access."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressClickEffect()
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Grant Permission", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (showDismiss) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressClickEffect()
                ) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
private fun PermissionFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Permission Granted Success Dialog
 */
@Composable
fun PermissionGrantedDialog(
    outputFolderPath: String,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Permission Granted!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Storage access has been granted. Your encrypted files will be saved here:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = outputFolderPath,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Text(
                    text = "You can change this location in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressClickEffect()
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    )
}
