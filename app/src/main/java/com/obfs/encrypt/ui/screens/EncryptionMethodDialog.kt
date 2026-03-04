package com.obfs.encrypt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.crypto.EncryptionMethod

@Composable
fun EncryptionMethodDialog(
    onDismiss: () -> Unit,
    onConfirm: (EncryptionMethod) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(EncryptionMethod.STANDARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Encryption Method",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose encryption strength:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                EncryptionMethod.entries.forEach { method ->
                    MethodOption(
                        method = method,
                        isSelected = selectedMethod == method,
                        onSelect = { selectedMethod = method }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMethod) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun MethodOption(
    method: EncryptionMethod,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val icon: ImageVector = when (method) {
        EncryptionMethod.FAST -> Icons.Default.Speed
        EncryptionMethod.STANDARD -> Icons.Default.Security
        EncryptionMethod.STRONG -> Icons.Default.Shield
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = method.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (method) {
                            EncryptionMethod.FAST -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                            EncryptionMethod.STANDARD -> Color(0xFF2196F3).copy(alpha = 0.2f)
                            EncryptionMethod.STRONG -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = method.speedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (method) {
                                EncryptionMethod.FAST -> Color(0xFF4CAF50)
                                EncryptionMethod.STANDARD -> Color(0xFF2196F3)
                                EncryptionMethod.STRONG -> Color(0xFFFF9800)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = method.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    }
}
