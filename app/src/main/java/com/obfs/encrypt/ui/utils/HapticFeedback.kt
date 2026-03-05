package com.obfs.encrypt.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * HapticFeedback - Provides refined haptic feedback for premium feel
 *
 * Features:
 * - Click feedback for file selection
 * - Success feedback for operation completion
 * - Heavy feedback for important actions
 * - Subtle feedback for UI interactions
 */
class HapticFeedback(private val context: Context) {
    
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Subtle click feedback - for file selection, toggle switches
     * Uses Android 10+ predefined effect
     */
    fun click() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrator?.vibrate(10)
        }
    }

    /**
     * Heavy click feedback - for important selections, card taps
     * Stronger vibration for more prominent feedback
     */
    fun heavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator?.vibrate(20)
        }
    }

    /**
     * Success feedback - for operation completion
     * Double pulse pattern to indicate success
     */
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    50,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            vibrator?.vibrate(50)
        }
    }

    /**
     * Tick feedback - for extremely subtle interactions
     * Minimal vibration for refined UI elements
     */
    fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator?.vibrate(5)
        }
    }

    /**
     * Double click feedback - for special actions
     * Two quick pulses
     */
    fun doubleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 50, 30),
                    -1
                )
            )
        } else {
            vibrator?.vibrate(longArrayOf(0, 30, 50, 30), -1)
        }
    }

    /**
     * Check if haptic feedback is available
     */
    val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true
}

/**
 * Composable function to remember HapticFeedback instance
 */
@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val context = LocalContext.current
    return remember { HapticFeedback(context) }
}
