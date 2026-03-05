package com.obfs.encrypt.ui.components

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.obfs.encrypt.R
import com.obfs.encrypt.security.AppLockManager
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.security.BiometricResult

/**
 * Biometric authentication dialog for app lock.
 * 
 * @param biometricAuthManager Manager for biometric authentication
 * @param appLockManager Manager for app lock state
 * @param onDismiss Request to dismiss the dialog
 * @param onAuthSuccess Callback when authentication succeeds
 * @param onUsePasswordFallback Callback when user requests password fallback
 */
@Composable
fun BiometricAuthDialog(
    biometricAuthManager: BiometricAuthManager,
    appLockManager: AppLockManager,
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit,
    onUsePasswordFallback: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isLockEnabled by appLockManager.isLockEnabled.collectAsState()

    LaunchedEffect(Unit) {
        if (activity != null && biometricAuthManager.isBiometricAvailable()) {
            val result = biometricAuthManager.authenticate(
                activity = activity,
                title = activity.getString(R.string.unlock_app),
                subtitle = activity.getString(R.string.unlock_app_subtitle)
            )
            when (result) {
                is BiometricResult.Success -> {
                    appLockManager.unlock()
                    onAuthSuccess()
                }
                is BiometricResult.UserCancelled -> {
                    if (onUsePasswordFallback != null) {
                        onUsePasswordFallback()
                    } else {
                        onDismiss()
                    }
                }
                is BiometricResult.LockedOut -> {
                    // Biometric is locked out, require password
                    onUsePasswordFallback?.invoke()
                }
                is BiometricResult.Error -> {
                    // Show error, allow retry
                    // For now, just dismiss
                    onDismiss()
                }
                else -> {
                    // Cancelled or NotEnrolled - dismiss
                    onDismiss()
                }
            }
        }
    }

    AlertDialog(
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.auth_required),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = stringResource(R.string.auth_required_desc),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        onDismissRequest = {
            if (onUsePasswordFallback != null) {
                onUsePasswordFallback()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onUsePasswordFallback?.invoke() ?: onDismiss()
                }
            ) {
                Text(stringResource(R.string.use_password))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Simple biometric prompt without app lock integration.
 * 
 * @param title Dialog title
 * @param subtitle Dialog subtitle
 * @param biometricAuthManager Manager for biometric authentication
 * @param onDismiss Request to dismiss the dialog
 * @param onAuthSuccess Callback when authentication succeeds
 */
@Composable
fun SimpleBiometricPrompt(
    title: String = stringResource(R.string.authenticate),
    subtitle: String = stringResource(R.string.use_biometric_desc),
    biometricAuthManager: BiometricAuthManager,
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(Unit) {
        if (activity != null && biometricAuthManager.isBiometricAvailable()) {
            val result = biometricAuthManager.authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle
            )
            when (result) {
                is BiometricResult.Success -> {
                    onAuthSuccess()
                }
                is BiometricResult.UserCancelled,
                is BiometricResult.LockedOut,
                is BiometricResult.Error,
                is BiometricResult.Cancelled,
                is BiometricResult.NotEnrolled -> {
                    onDismiss()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = subtitle)
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * App lock settings card for the Settings screen.
 * 
 * @param isLockEnabled Current lock enabled state
 * @param onToggleLock Toggle lock enabled state
 * @param onSelectTimeout Open timeout selection dialog
 * @param currentTimeout Current timeout value
 */
@Composable
fun AppLockSettingsCard(
    isLockEnabled: Boolean,
    onToggleLock: (Boolean) -> Unit,
    onSelectTimeout: () -> Unit,
    currentTimeout: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_lock),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.app_lock_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                androidx.compose.material3.Switch(
                    checked = isLockEnabled,
                    onCheckedChange = onToggleLock
                )
            }

            if (isLockEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    onClick = onSelectTimeout
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.auto_lock_timeout_title),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = getTimeoutDisplayName(currentTimeout),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get human-readable display name for timeout value.
 */
@Composable
private fun getTimeoutDisplayName(timeout: Long): String {
    return when (timeout) {
        0L -> stringResource(R.string.immediately)
        5_000L -> stringResource(R.string.five_seconds)
        15_000L -> stringResource(R.string.fifteen_seconds)
        30_000L -> stringResource(R.string.thirty_seconds)
        60_000L -> stringResource(R.string.one_minute)
        300_000L -> stringResource(R.string.five_minutes)
        900_000L -> stringResource(R.string.fifteen_minutes)
        else -> "$timeout ms"
    }
}
