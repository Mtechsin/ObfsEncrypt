package com.obfs.encrypt.ui.components

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassmorphicOverlay - Creates a frosted glass effect for premium UI
 *
 * Features:
 * - Background blur effect (Android 12+)
 * - Semi-transparent surface with backdrop filter
 * - Subtle border for depth
 * - Customizable blur radius and opacity
 *
 * Usage:
 * - Top app bars
 * - Dialog backgrounds
 * - Bottom sheets
 * - Floating overlays
 */
@Composable
fun GlassmorphicOverlay(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    opacity: Float = 0.7f,
    borderColor: Color = Color.White.copy(alpha = 0.1f),
    borderWidth: Dp = 1.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Modern approach using Compose blur modifier (Android 12+)
    Box(
        modifier = modifier
            .blur(blurRadius)
            .alpha(opacity)
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
    ) {
        content()
    }
}

/**
 * GlassmorphicTopBar - Specialized glassmorphic effect for top app bars
 *
 * Applies a frosted glass effect with subtle gradient overlay
 */
@Composable
fun GlassmorphicTopBar(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 30.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Backdrop blur layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                )
        )
        
        // Content layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f)
        ) {
            content()
        }
    }
}

/**
 * GlassmorphicDialog - Creates a frosted glass dialog background
 *
 * Premium dialog appearance with depth and blur effects
 */
@Composable
fun GlassmorphicDialog(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 25.dp,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        tonalElevation = 8.dp
    ) {
        // Apply blur to background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
        )
        
        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            content()
        }
    }
}

/**
 * GlassmorphicCard - Card with frosted glass effect
 *
 * Elevated card with subtle blur and transparency
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 15.dp,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        tonalElevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            content()
        }
    }
}

/**
 * GlassmorphicBottomSheet - Bottom sheet with frosted glass effect
 */
@Composable
fun GlassmorphicBottomSheet(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 30.dp,
    cornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        tonalElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            content()
        }
    }
}

/**
 * LegacyBlurUtils - Fallback blur implementation using RenderScript for older devices
 * 
 * Note: RenderScript is deprecated but still functional for API < 31
 */
object LegacyBlurUtils {
    
    /**
     * Apply blur to a bitmap using RenderScript (API 17-30)
     */
    fun blurBitmap(context: android.content.Context, bitmap: Bitmap, radius: Float = 25f): Bitmap {
        var rs: RenderScript? = null
        var input: Allocation? = null
        var output: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        
        try {
            rs = RenderScript.create(context)
            input = Allocation.createFromBitmap(rs, bitmap)
            output = Allocation.createTyped(rs, input.type)
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            
            blur.setRadius(radius.coerceIn(0.1f, 25f))
            blur.setInput(input)
            blur.forEach(output)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            output.copyTo(result)
            return result
        } finally {
            rs?.destroy()
            input?.destroy()
            output?.destroy()
            blur?.destroy()
        }
    }
}
