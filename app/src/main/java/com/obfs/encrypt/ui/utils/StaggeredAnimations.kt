package com.obfs.encrypt.ui.utils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * StaggeredAnimations - Provides premium staggered entrance animations for lists
 *
 * Uses AnimatedVisibility instead of per-item Animatable + LaunchedEffect
 * to eliminate per-item coroutine overhead.
 */

/**
 * Animate a list of items with staggered fade and slide entrance
 */
@Composable
fun <T> StaggeredLazyColumn(
    items: List<T>,
    key: ((T) -> Any)? = null,
    animationDelay: Int = 50,
    content: @Composable ColumnScope.(T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            var visible by remember(key?.invoke(item) ?: item) { mutableStateOf(false) }

            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = Motion.SnappySpring),
                exit = fadeOut()
            ) {
                content(item)
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
    content: @Composable ColumnScope.(T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            var visible by remember(key?.invoke(item) ?: item) { mutableStateOf(false) }

            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = Motion.SnappySpring) +
                        scaleIn(initialScale = initialScale, animationSpec = Motion.SnappySpring),
                exit = fadeOut()
            ) {
                content(item)
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
    animationDelay: Int = 50,
    content: @Composable RowScope.(T) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            var visible by remember(key?.invoke(item) ?: item) { mutableStateOf(false) }

            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = Motion.SnappySpring),
                exit = fadeOut()
            ) {
                content(item)
            }
        }
    }
}

/**
 * Animate individual item with spring entrance.
 * Use for single items or when you want manual control.
 */
@Composable
fun AnimateItemEntrance(
    key: Any?,
    animationDelay: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = Motion.SnappySpring),
        exit = fadeOut()
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
            var visible by remember(key?.invoke(item) ?: item) { mutableStateOf(false) }

            LaunchedEffect(key?.invoke(item) ?: item) {
                delay((index * animationDelay).toLong())
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 150f)
                ),
                exit = fadeOut()
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
    content: @Composable (T) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = items.chunked(columns)

        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * columns + colIndex
                    var visible by remember(key?.invoke(item) ?: item) { mutableStateOf(false) }

                    LaunchedEffect(key?.invoke(item) ?: item) {
                        delay((index * animationDelay).toLong())
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.9f,
                            animationSpec = Motion.SnappySpring
                        ),
                        exit = fadeOut(),
                        modifier = Modifier.weight(1f)
                    ) {
                        content(item)
                    }
                }

                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
