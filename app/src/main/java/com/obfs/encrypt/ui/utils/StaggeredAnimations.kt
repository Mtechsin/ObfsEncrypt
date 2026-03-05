package com.obfs.encrypt.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * StaggeredAnimations - Provides premium staggered entrance animations for lists
 *
 * Features:
 * - Staggered fade-in with spring animation
 * - Staggered slide-up entrance
 * - Staggered scale-in effect
 * - Configurable delay per item
 * - Haptic feedback integration
 */

/**
 * Animate a list of items with staggered fade and slide entrance
 */
@Composable
fun <T> StaggeredLazyColumn(
    items: List<T>,
    key: ((T) -> Any)? = null,
    initialAlpha: Float = 0f,
    initialOffsetY: Dp = 20.dp,
    animationDelay: Int = 50,
    animationSpec: AnimationSpec<Float> = Motion.SnappySpring,
    itemContent: @Composable ColumnScope.(T) -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { initialOffsetY.toPx() }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            val alpha = remember { Animatable(initialAlpha) }
            val offsetY = remember { Animatable(initialOffsetPx) }
            
            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                
                alpha.animateTo(1f, animationSpec = animationSpec)
                offsetY.animateTo(0f, animationSpec = animationSpec)
            }
            
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = offsetY.value
                    }
            ) {
                itemContent(item)
            }
        }
    }
}

/**
 * Animate a list of items with staggered scale entrance
 */
@Composable
fun <T> StaggeredScaleColumn(
    items: List<T>,
    key: ((T) -> Any)? = null,
    initialScale: Float = 0.85f,
    animationDelay: Int = 40,
    animationSpec: AnimationSpec<Float> = Motion.SnappySpring,
    itemContent: @Composable ColumnScope.(T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            val scale = remember { Animatable(initialScale) }
            val alpha = remember { Animatable(0f) }
            
            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                
                scale.animateTo(1f, animationSpec = animationSpec)
                alpha.animateTo(1f, animationSpec = animationSpec)
            }
            
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
            ) {
                itemContent(item)
            }
        }
    }
}

/**
 * Animate a list of items with staggered slide from left
 */
@Composable
fun <T> StaggeredSlideRow(
    items: List<T>,
    key: ((T) -> Any)? = null,
    initialOffsetX: Dp = (-30).dp,
    animationDelay: Int = 50,
    animationSpec: AnimationSpec<Float> = Motion.SnappySpring,
    itemContent: @Composable RowScope.(T) -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { initialOffsetX.toPx() }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val offsetX = remember { Animatable(initialOffsetPx) }
            val alpha = remember { Animatable(0f) }
            
            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                
                offsetX.animateTo(0f, animationSpec = animationSpec)
                alpha.animateTo(1f, animationSpec = animationSpec)
            }
            
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = offsetX.value
                        this.alpha = alpha.value
                    }
            ) {
                itemContent(item)
            }
        }
    }
}

/**
 * Animate individual item with spring entrance
 * Use for single items or when you want manual control
 */
@Composable
fun AnimateItemEntrance(
    key: Any?,
    initialAlpha: Float = 0f,
    initialOffsetY: Dp = 15.dp,
    initialScale: Float = 1f,
    animationDelay: Int = 0,
    animationSpec: AnimationSpec<Float> = Motion.SnappySpring,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { initialOffsetY.toPx() }
    
    val alpha = remember { Animatable(initialAlpha) }
    val offsetY = remember { Animatable(initialOffsetPx) }
    val scale = remember { Animatable(initialScale) }
    
    LaunchedEffect(key) {
        delay(animationDelay.toLong())
        
        alpha.animateTo(1f, animationSpec = animationSpec)
        offsetY.animateTo(0f, animationSpec = animationSpec)
        scale.animateTo(1f, animationSpec = animationSpec)
    }
    
    Column(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
        content()
    }
}

/**
 * Animate list items with bounce effect on appearance
 */
@Composable
fun <T> BouncyListColumn(
    items: List<T>,
    key: ((T) -> Any)? = null,
    animationDelay: Int = 60,
    content: @Composable ColumnScope.(T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            val scale = remember { Animatable(0.8f) }
            val alpha = remember { Animatable(0f) }
            
            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                
                scale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = 150f
                    )
                )
                alpha.animateTo(1f, animationSpec = Motion.SnappySpring)
            }
            
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
            ) {
                content(item)
            }
        }
    }
}

/**
 * Staggered grid animation for grid layouts
 */
@Composable
fun <T> StaggeredGrid(
    items: List<T>,
    columns: Int = 2,
    key: ((T) -> Any)? = null,
    animationDelay: Int = 50,
    content: @Composable ColumnScope.(T) -> Unit
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 20.dp.toPx() }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group items into rows
        val rows = items.chunked(columns)
        
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * columns + colIndex
                    val alpha = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(initialOffsetPx) }
                    val scale = remember { Animatable(0.9f) }
                    
                    LaunchedEffect(key?.invoke(item) ?: item) {
                        delay((index * animationDelay).toLong())
                        
                        alpha.animateTo(1f, animationSpec = Motion.SnappySpring)
                        offsetY.animateTo(0f, animationSpec = Motion.SnappySpring)
                        scale.animateTo(1f, animationSpec = Motion.SnappySpring)
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                this.alpha = alpha.value
                                translationY = offsetY.value
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                    ) {
                        content(item)
                    }
                }
                
                // Fill remaining columns with spacers
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
