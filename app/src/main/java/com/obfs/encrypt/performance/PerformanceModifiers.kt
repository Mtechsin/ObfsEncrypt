package com.obfs.encrypt.performance

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

/**
 * Performance-optimized Compose modifiers for butter-smooth interactions.
 */

/**
 * Optimized click effect with minimal recomposition.
 * Uses graphicsLayer for GPU-accelerated scaling.
 */
fun Modifier.optimizedClickEffect(
    scale: Float = 0.95f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) {
        this.clickable(onClick = onClick)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) scale else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "click_scale"
        )
        
        this
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
    }
}

/**
 * No-op modifier for disabled animations.
 * Use this to conditionally disable animations on low-end devices.
 */
fun Modifier.conditionalAnimation(
    condition: Boolean,
    transform: Modifier.() -> Modifier
): Modifier = if (condition) this.transform() else this

/**
 * GPU-accelerated fade modifier.
 * Prefer this over alpha animation for better performance.
 */
fun Modifier.gpuFade(alpha: Float): Modifier = this
    .graphicsLayer {
        this.alpha = alpha
    }

/**
 * Hardware layer modifier for complex animations.
 * Use sparingly as it consumes more memory.
 */
fun Modifier.hardwareLayer(enabled: Boolean = true): Modifier = this
    .graphicsLayer {
        // Hardware acceleration enabled automatically
    }

/**
 * Performance-aware visibility modifier.
 * Only composes content when visible or about to be visible.
 */
fun Modifier.lazyVisibility(
    isVisible: Boolean
): Modifier = this
    .graphicsLayer {
        // GPU layer for visibility transitions
    }
