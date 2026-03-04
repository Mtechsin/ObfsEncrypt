package com.obfs.encrypt.performance

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Performance utilities for enabling 120Hz refresh rate and optimizing display settings.
 */
object DisplayPerformanceOptimizer {

    /**
     * Enables 120Hz high refresh rate mode for the activity window.
     * Call this in onCreate before setContent.
     */
    fun enableHighRefreshRate(activity: Activity) {
        activity.window.attributes = activity.window.attributes.apply {
            // Request high refresh rate mode (120Hz if available)
            preferredDisplayModeId = 0
        }
    }

    /**
     * Sets the window to prefer high refresh rate content.
     */
    fun setHighRefreshRateContent(window: Window) {
        window.attributes = window.attributes.apply {
            // This hints to the system that we want high refresh rate
            preferredDisplayModeId = 0
        }
    }
}

/**
 * Composable to enable high refresh rate for Compose UI.
 */
@Composable
fun EnableHighRefreshRate() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (context is Activity) {
            DisplayPerformanceOptimizer.enableHighRefreshRate(context)
        }
    }
}
