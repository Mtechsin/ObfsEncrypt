package com.obfs.encrypt.performance

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Performance configuration for the app.
 * Automatically adjusts animation complexity based on device capabilities.
 */
data class PerformanceConfig(
    val isHighRefreshRate: Boolean = false,
    val isLowEndDevice: Boolean = false,
    val reduceMotion: Boolean = false,
    val maxAnimationDurationMs: Int = 500,
    val lazyListPrefetchSlots: Int = 4,
    val imageCacheSize: Int = 100,
    val enableSnapshots: Boolean = true
)

val LocalPerformanceConfig = compositionLocalOf { PerformanceConfig() }

/**
 * Detects if the device is capable of high refresh rate (90Hz+).
 */
@Composable
@ReadOnlyComposable
fun isHighRefreshRateDevice(): Boolean {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val display = context.display ?: return false
        display.refreshRate >= 90f
    } else {
        @Suppress("DEPRECATION")
        val display = (context as? Activity)?.windowManager?.defaultDisplay
        display?.refreshRate ?: 60f >= 90f
    }
}

/**
 * Returns an animation spec optimized for the current performance config.
 * On high refresh rate devices, uses smoother springs.
 * On low-end devices, uses simpler tweens.
 */
@Composable
fun optimizedAnimationSpec(
    dampingRatio: Float = Spring.DampingRatioLowBouncy,
    stiffness: Float = Spring.StiffnessMedium,
    durationMs: Int = 300
): AnimationSpec<Float> {
    val config = LocalPerformanceConfig.current
    
    return if (config.reduceMotion || config.isLowEndDevice) {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    } else if (config.isHighRefreshRate) {
        spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
    } else {
        spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
    }
}

/**
 * Checks if the device is a low-end device based on RAM and SDK version.
 */
fun isLowEndDevice(): Boolean {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory()
    val totalMemory = runtime.totalMemory()
    val freeMemory = maxMemory - (totalMemory - runtime.freeMemory())
    
    // Consider low-end if less than 2GB RAM or very old Android version
    return maxMemory < 2L * 1024 * 1024 * 1024 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
}
