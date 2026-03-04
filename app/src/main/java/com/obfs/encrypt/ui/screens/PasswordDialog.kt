package com.obfs.encrypt.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obfs.encrypt.ui.components.KeyfilePicker
import com.obfs.encrypt.ui.components.PasswordStrengthMeter
import com.obfs.encrypt.ui.theme.pressClickEffect
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Enhanced Password Input Dialog with Keyfile and Integrity Check support
 *
 * Features:
 * - Password input with strength meter
 * - Keyfile selection option (instead of or in addition to password)
 * - Integrity verification option for encryption
 * - Biometric authentication option (when available)
 */
@Composable
fun PasswordDialog(
    uris: List<Uri>,
    isFolder: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray, Boolean) -> Unit,
    showDeleteOption: Boolean = false,
    isDecryption: Boolean = false,
    onBiometricAuth: (() -> Unit)? = null,
    viewModel: MainViewModel? = null
) {
    val activity = LocalContext.current as androidx.appcompat.app.AppCompatActivity
    val vm = viewModel ?: hiltViewModel(activity)
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var deleteOriginal by remember { mutableStateOf(false) }
    
    // Keyfile support
    val keyfileUri by vm.keyfileUri.collectAsState()
    var showKeyfilePicker by remember { mutableStateOf(false) }
    
    // Integrity check
    val enableIntegrityCheck by vm.enableIntegrityCheck.collectAsState()
    
    // Biometric
    val biometricAvailable = vm.biometricAuthManager.canAuthenticate() == 
            com.obfs.encrypt.security.BiometricStatus.AVAILABLE
    val hasStoredPassword = vm.biometricAuthManager.hasStoredPassword()
    
    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            repeat(4) {
                shakeOffset.animateTo(
                    targetValue = 10f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh)
                )
                shakeOffset.animateTo(
                    targetValue = -10f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }
    
    // File picker for keyfile
    KeyfilePicker(
        showPicker = showKeyfilePicker,
        onDismiss = { showKeyfilePicker = false },
        onKeyfileSelected = { uri ->
            vm.setKeyfileUri(uri)
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        title = { 
            Text(
                text = if (isDecryption) "Secure Decryption" else "Secure Initialization",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            Column {
                Text(
                    if (isDecryption) "Enter the password used to encrypt this data." 
                    else "Enter a strong password or use a keyfile to encrypt your data.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Password input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility")
                        }
                    }
                )

                if (!isDecryption) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordStrengthMeter(password = password)
                }
                
                // Keyfile option
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Keyfile (Optional)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Use a keyfile as additional authentication factor",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (keyfileUri != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Keyfile selected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp).weight(1f)
                                        )
                                        TextButton(onClick = { vm.setKeyfileUri(null) }) {
                                            Text("Clear")
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showKeyfilePicker = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Select Keyfile")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Integrity check option (encryption only)
                if (!isDecryption) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "Enable Integrity Check",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Adds checksum verification to detect tampering",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = enableIntegrityCheck,
                                onCheckedChange = { vm.toggleIntegrityCheck(it) }
                            )
                        }
                    }
                }
                
                // Biometric option
                if (biometricAvailable && !isDecryption) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "Biometric Protection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Require fingerprint/face to decrypt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = vm.biometricAuthManager.isBiometricEnabled(),
                                onCheckedChange = { enabled ->
                                    vm.biometricAuthManager.setBiometricEnabled(enabled)
                                    if (enabled) {
                                        // Store password with biometric after successful encryption
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Use stored password button (decryption only)
                if (isDecryption && biometricAvailable && hasStoredPassword) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onBiometricAuth?.invoke() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Unlock with Biometric")
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                AnimatedVisibility(
                    visible = showDeleteOption,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = deleteOriginal,
                                onCheckedChange = { deleteOriginal = it }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete original files",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Remove source files after successful encryption",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length >= (if (isDecryption) 1 else 4)) {
                        val chars = password.toCharArray()
                        onConfirm(chars, deleteOriginal)
                    } else {
                        triggerShake()
                    }
                },
                modifier = Modifier.pressClickEffect(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isDecryption) "Decrypt" else "Encrypt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
