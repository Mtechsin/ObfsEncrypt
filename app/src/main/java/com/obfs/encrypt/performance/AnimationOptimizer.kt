package com.obfs.encrypt.performance

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Performance-optimized animation specifications.
 * 
 * Usage:
 * - Use [fastAnimationSpec] for UI elements that need to feel snappy
 * - Use [smoothAnimationSpec] for transitions that should feel fluid
 * - Use [minimalAnimationSpec] for low-end devices or when battery is low
 */
object AnimationOptimizer {

    /**
     * Animation performance mode - automatically selected based on device capabilities.
     */
    enum class PerformanceMode {
        HIGH,      // 120Hz, full animations
        STANDARD,  // 60-90Hz, optimized animations
        MINIMAL    // Low-end devices, reduced animations
    }

    private var currentMode: PerformanceMode? = null

    /**
     * Get the current performance mode (cached).
     */
    fun getPerformanceMode(): PerformanceMode {
        return currentMode ?: detectPerformanceMode().also { currentMode = it }
    }

    /**
     * Detect optimal performance mode based on device capabilities.
     */
    private fun detectPerformanceMode(): PerformanceMode {
        return when {
            // High-end: 8GB+ RAM, Android 10+
            Runtime.getRuntime().maxMemory() >= 8L * 1024 * 1024 * 1024 &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                PerformanceMode.HIGH
            }
            // Low-end: <2GB RAM or old Android
            Runtime.getRuntime().maxMemory() < 2L * 1024 * 1024 * 1024 ||
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O -> {
                PerformanceMode.MINIMAL
            }
            // Standard: everything else
            else -> PerformanceMode.STANDARD
        }
    }

    /**
     * Fast spring animation for snappy UI interactions.
     * Optimized for 120Hz displays.
     */
    @Composable
    fun fastAnimationSpec(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessHigh
    ): AnimationSpec<Float> {
        return when (getPerformanceMode()) {
            PerformanceMode.MINIMAL -> tween(durationMillis = 100)
            PerformanceMode.STANDARD -> spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
            PerformanceMode.HIGH -> spring(
                dampingRatio = dampingRatio,
                stiffness = stiffness
            )
        }
    }

    /**
     * Smooth spring animation for fluid transitions.
     */
    @Composable
    fun smoothAnimationSpec(
        dampingRatio: Float = Spring.DampingRatioLowBouncy,
        stiffness: Float = Spring.StiffnessMediumLow
    ): AnimationSpec<Float> {
        return when (getPerformanceMode()) {
            PerformanceMode.MINIMAL -> tween(durationMillis = 200)
            PerformanceMode.STANDARD -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
            PerformanceMode.HIGH -> spring(
                dampingRatio = dampingRatio,
                stiffness = stiffness
            )
        }
    }

    /**
     * Minimal animation for low-end devices.
     * Uses simple tweens without physics.
     */
    @Composable
    fun minimalAnimationSpec(durationMs: Int = 150): AnimationSpec<Float> {
        return tween(durationMillis = durationMs)
    }

    /**
     * Get animation duration based on performance mode.
     */
    fun getAnimationDuration(baseDurationMs: Int = 300): Int {
        return when (getPerformanceMode()) {
            PerformanceMode.MINIMAL -> (baseDurationMs * 0.5).toInt()
            PerformanceMode.STANDARD -> baseDurationMs
            PerformanceMode.HIGH -> (baseDurationMs * 1.2).toInt()
        }
    }

    /**
     * Check if complex animations should be disabled.
     */
    fun shouldReduceMotion(): Boolean {
        return getPerformanceMode() == PerformanceMode.MINIMAL
    }
}

/**
 * Optimized spring spec for Material 3 components.
 * Automatically adjusts based on device performance.
 */
@Composable
fun optimizedSpring(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMedium
): AnimationSpec<Float> {
    return AnimationOptimizer.fastAnimationSpec(dampingRatio, stiffness)
}

/**
 * Get the optimal stiffness value for the current device.
 */
@Composable
@ReadOnlyComposable
fun optimizedStiffness(): Float {
    return when (AnimationOptimizer.getPerformanceMode()) {
        AnimationOptimizer.PerformanceMode.MINIMAL -> Spring.StiffnessLow
        AnimationOptimizer.PerformanceMode.STANDARD -> Spring.StiffnessMedium
        AnimationOptimizer.PerformanceMode.HIGH -> Spring.StiffnessMediumLow
    }
}

/**
 * Get the optimal damping ratio for the current device.
 */
@Composable
@ReadOnlyComposable
fun optimizedDamping(): Float {
    return when (AnimationOptimizer.getPerformanceMode()) {
        AnimationOptimizer.PerformanceMode.MINIMAL -> Spring.DampingRatioNoBouncy
        AnimationOptimizer.PerformanceMode.STANDARD -> Spring.DampingRatioMediumBouncy
        AnimationOptimizer.PerformanceMode.HIGH -> Spring.DampingRatioLowBouncy
    }
}
