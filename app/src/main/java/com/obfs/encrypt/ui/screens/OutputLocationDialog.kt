package com.obfs.encrypt.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun OutputLocationDialog(
    currentOutputUri: Uri?,
    selectedUri: Uri?,
    onSelectCustomFolder: () -> Unit,
    onClearCustomFolder: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    android.util.Log.d("OutputLocationDialog", "OutputLocationDialog Rendering as Overlay - current: $currentOutputUri")
    
    LaunchedEffect(Unit) {
        android.widget.Toast.makeText(context, "OutputLocationDialog Mounted", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    val effectiveUri = selectedUri ?: currentOutputUri

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(enabled = false, onClick = {})
                    .padding(vertical = 40.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Output Location",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Where should encrypted/decrypted files be saved?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Use source directory (default)
                    Card(
                        onClick = onClearCustomFolder,
                        colors = CardDefaults.cardColors(
                            containerColor = if (effectiveUri == null)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (effectiveUri == null)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Same as Source",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (effectiveUri == null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Save in the same directory as the original file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (effectiveUri == null)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Custom folder
                    Card(
                        onClick = onSelectCustomFolder,
                        colors = CardDefaults.cardColors(
                            containerColor = if (effectiveUri != null)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (effectiveUri != null)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Choose Custom Folder",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (effectiveUri != null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = effectiveUri?.lastPathSegment ?: "Tap to select a folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (effectiveUri != null)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Info text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "This setting will be saved and used for all future operations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = onConfirm) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
}
