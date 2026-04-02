package com.obfs.encrypt.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.performance.LocalPerformanceConfig

private val canBlurApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun shouldApplyBlur(): Boolean {
    val config = LocalPerformanceConfig.current
    return canBlurApi && !config.isLowEndDevice
}

@Composable
fun GlassmorphicOverlay(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    opacity: Float = 0.7f,
    borderColor: Color = Color.White.copy(alpha = 0.1f),
    borderWidth: Dp = 1.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.clip(shape)) {
        if (shouldApplyBlur()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(blurRadius)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = opacity))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = opacity + 0.15f))
            )
        }

        Box(
            modifier = Modifier.matchParentSize(),
            content = content
        )
    }
}

@Composable
fun GlassmorphicTopBar(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 30.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (shouldApplyBlur()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(blurRadius)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

@Composable
fun GlassmorphicDialog(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 25.dp,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        tonalElevation = 8.dp
    ) {
        Box {
            if (shouldApplyBlur()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(blurRadius)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 15.dp,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        tonalElevation = elevation
    ) {
        Box {
            if (shouldApplyBlur()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(blurRadius)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
                        )
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}

@Composable
fun GlassmorphicBottomSheet(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 30.dp,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        color = Color.Transparent,
        tonalElevation = 8.dp
    ) {
        Box {
            if (shouldApplyBlur()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(blurRadius)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}
