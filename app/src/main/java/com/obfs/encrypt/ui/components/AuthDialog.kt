package com.obfs.encrypt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.obfs.encrypt.R
import com.obfs.encrypt.security.AppLockManager
import com.obfs.encrypt.security.AppPasswordManager
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.security.BiometricResult

@Composable
fun AuthDialog(
    appPasswordManager: AppPasswordManager,
    appLockManager: AppLockManager,
    biometricAuthManager: BiometricAuthManager,
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordInput by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    val isBiometricAvailable = biometricAuthManager.isBiometricAvailable() && biometricAuthManager.isBiometricEnabled()
    val isPasswordEnabled = appPasswordManager.isPasswordEnabled()

    LaunchedEffect(Unit) {
        if (isBiometricAvailable && !showPasswordInput) {
            val result = biometricAuthManager.authenticate(
                activity = activity!!,
                title = context.getString(R.string.unlock_app),
                subtitle = context.getString(R.string.unlock_app_subtitle)
            )
            when (result) {
                is BiometricResult.Success -> {
                    appLockManager.unlock()
                    onAuthSuccess()
                }
                is BiometricResult.Cancelled,
                is BiometricResult.UserCancelled -> {
                    showPasswordInput = true
                }
                else -> {
                    showPasswordInput = true
                }
            }
        } else {
            showPasswordInput = true
        }
    }

    if (showForgotPassword) {
        ForgotPasswordDialog(
            appPasswordManager = appPasswordManager,
            onDismiss = { showForgotPassword = false },
            onPasswordReset = {
                showForgotPassword = false
                password = ""
                passwordError = null
            }
        )
    } else {
        AlertDialog(
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showPasswordInput) {
                        Text(
                            text = stringResource(R.string.enter_password_to_unlock),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = { Text(stringResource(R.string.password)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (appPasswordManager.verifyPassword(password)) {
                                        appLockManager.unlock()
                                        onAuthSuccess()
                                    } else {
                                        passwordError = context.getString(R.string.incorrect_password)
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                                    )
                                }
                            },
                            isError = passwordError != null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (passwordError != null) {
                            Text(
                                text = passwordError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isBiometricAvailable) {
                            TextButton(
                                onClick = {
                                    showPasswordInput = false
                                    password = ""
                                    passwordError = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.use_biometric))
                            }
                        }
                    }
                }
            },
            onDismissRequest = { },
            confirmButton = {
                if (showPasswordInput) {
                    Button(
                        onClick = {
                            if (appPasswordManager.verifyPassword(password)) {
                                appLockManager.unlock()
                                onAuthSuccess()
                            } else {
                                passwordError = context.getString(R.string.incorrect_password)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.unlock))
                    }
                }
            },
            dismissButton = {
                if (appPasswordManager.isSecurityQuestionsEnabled()) {
                    TextButton(onClick = { showForgotPassword = true }) {
                        Text(stringResource(R.string.forgot_password))
                    }
                }
            }
        )
    }
}

@Composable
fun ForgotPasswordDialog(
    appPasswordManager: AppPasswordManager,
    onDismiss: () -> Unit,
    onPasswordReset: () -> Unit
) {
    val context = LocalContext.current
    var answer by remember { mutableStateOf("") }
    var answerError by remember { mutableStateOf<String?>(null) }
    var isAnswerCorrect by remember { mutableStateOf(false) }
    
    // Reset password states
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }

    val securityQuestion = remember { appPasswordManager.getSecurityQuestion() ?: context.getString(R.string.no_security_question_set) }

    if (!isAnswerCorrect) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.security_question),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.answer_question_to_reset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = securityQuestion,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = answer,
                        onValueChange = {
                            answer = it
                            answerError = null
                        },
                        label = { Text(stringResource(R.string.answer)) },
                        singleLine = true,
                        isError = answerError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (answerError != null) {
                        Text(
                            text = answerError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appPasswordManager.verifySecurityAnswer(answer)) {
                            isAnswerCorrect = true
                        } else {
                            answerError = context.getString(R.string.incorrect_answer)
                        }
                    }
                ) {
                    Text(stringResource(R.string.verify))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.reset_password),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.reset_password_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            resetError = null
                        },
                        label = { Text(stringResource(R.string.new_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            resetError = null
                        },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        isError = resetError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resetError != null) {
                        Text(
                            text = resetError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            newPassword.isEmpty() -> {
                                resetError = context.getString(R.string.password_cannot_be_empty)
                            }
                            newPassword != confirmPassword -> {
                                resetError = context.getString(R.string.passwords_do_not_match)
                            }
                            else -> {
                                appPasswordManager.setPassword(newPassword)
                                onPasswordReset()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

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
                    onUsePasswordFallback?.invoke()
                }
                is BiometricResult.Error -> {
                    onUsePasswordFallback?.invoke() ?: onDismiss()
                }
                else -> {
                    onUsePasswordFallback?.invoke() ?: onDismiss()
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
