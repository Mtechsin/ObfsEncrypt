package com.obfs.encrypt.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.R
import com.obfs.encrypt.ui.theme.Motion
import com.obfs.encrypt.ui.theme.isAMOLEDTheme
import com.obfs.encrypt.ui.theme.amoledOutlinedButtonContainerColor
import com.obfs.encrypt.ui.theme.amoledOutlinedButtonContentColor
import com.obfs.encrypt.viewmodel.MainViewModel

/**
 * Animated Progress Screen
 *
 * Why this approach:
 * Showing real-time progress using smooth animations gives the user confidence during
 * large 10GB+ file operations.
 * Allows cancellation gracefully through the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: MainViewModel,
    operation: String,
    onNavigateBack: () -> Unit
) {
    val progressState by viewModel.progressState.collectAsState()
    val progress = progressState.progress
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isActive by viewModel.isOperationActive.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progress_indicator"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.progress),
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
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues), 
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = progress >= 1f && !isActive && !statusMessage.startsWith("Error", ignoreCase = true),
                    enter = fadeIn() + scaleIn(initialScale = 0.5f, animationSpec = Motion.ExtremeSpring),
                    exit = fadeOut() + scaleOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(100.dp)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isActive && statusMessage.startsWith("Error", ignoreCase = true),
                    enter = fadeIn() + scaleIn(initialScale = 0.5f, animationSpec = Motion.ExtremeSpring),
                    exit = fadeOut() + scaleOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(100.dp)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isActive,
                    enter = fadeIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(if (isPaused) 1f else pulseScale),
                        color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        strokeWidth = 8.dp,
                        progress = { if (isPaused) progress else animatedProgress }
                    )
                    
                    if (isPaused) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = progress >= 1f && !isActive && !statusMessage.startsWith("Error", ignoreCase = true),
                enter = fadeIn() + scaleIn(initialScale = 0.9f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.success),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    FilledTonalButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.return_home), fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(
                visible = !isActive && statusMessage.startsWith("Error", ignoreCase = true),
                enter = fadeIn() + scaleIn(initialScale = 0.9f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.failed),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    if (isAMOLEDTheme()) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = amoledOutlinedButtonContainerColor(),
                                contentColor = amoledOutlinedButtonContentColor(MaterialTheme.colorScheme.onErrorContainer)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(stringResource(R.string.return_home), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(stringResource(R.string.return_home), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (progressState.totalFiles > 1) {
                        Text(
                            text = "File ${progressState.currentFileIndex} of ${progressState.totalFiles}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    LinearProgressIndicator(
                        progress = { if (isPaused) progress else animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        
                        Text(
                            text = progressState.speedFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "ETA: ${progressState.etaFormatted}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = progressState.currentFile ?: statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (isAMOLEDTheme()) {
                            OutlinedButton(
                                onClick = { viewModel.togglePause() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = amoledOutlinedButtonContainerColor(),
                                    contentColor = amoledOutlinedButtonContentColor(MaterialTheme.colorScheme.primary)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(
                                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, 
                                    contentDescription = null, 
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(if (isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { viewModel.togglePause() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(
                                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, 
                                    contentDescription = null, 
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(if (isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        if (isAMOLEDTheme()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.cancelOperation()
                                    onNavigateBack()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = amoledOutlinedButtonContainerColor(),
                                    contentColor = amoledOutlinedButtonContentColor(MaterialTheme.colorScheme.error)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.cancel_operation), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.cancelOperation()
                                    onNavigateBack()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.cancel_operation), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}