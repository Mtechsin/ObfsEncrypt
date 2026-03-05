package com.obfs.encrypt.ui.utils

import android.content.ClipData
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import android.content.ClipboardManager as AndroidClipboardManager

/**
 * Clipboard utility for secure password handling.
 *
 * Features:
 * - Copy password to clipboard
 * - Automatic clipboard clearing after timeout
 * - Manual clipboard clearing
 *
 * Usage:
 * ```kotlin
 * val clipboardManager = rememberClipboardManager(context)
 *
 * // Copy password (auto-clears after 60 seconds)
 * clipboardManager.copyPassword(password)
 *
 * // Or with custom timeout
 * clipboardManager.copyPassword(password, timeoutMs = 30_000)
 *
 * // Manual clear
 * clipboardManager.clearClipboard()
 * ```
 */
class ClipboardManager(private val context: Context) {

    private val clipboard: AndroidClipboardManager by lazy {
        ContextCompat.getSystemService(context, AndroidClipboardManager::class.java)!!
    }

    /**
     * Copy password to clipboard with automatic clearing.
     *
     * @param password The password to copy
     * @param timeoutMs Time in milliseconds after which clipboard is cleared (default: 60 seconds)
     */
    fun copyPassword(password: CharArray, timeoutMs: Long = 60_000) {
        val clip = ClipData.newPlainText("password", String(password))
        clipboard.setPrimaryClip(clip)

        // Schedule automatic clearing
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            clearClipboard()
        }, timeoutMs)

        // Clear the password array after copying
        password.fill('0')
    }

    /**
     * Copy text to clipboard (non-sensitive).
     */
    fun copyText(text: String) {
        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * Clear the clipboard manually.
     */
    fun clearClipboard() {
        // Create empty clip data to clear
        val emptyClip = ClipData.newPlainText("", "")
        clipboard.setPrimaryClip(emptyClip)
    }

    /**
     * Check if clipboard has text.
     */
    fun hasText(): Boolean {
        return clipboard.hasPrimaryClip() &&
               !clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty()
    }

    /**
     * Get clipboard text (use with caution for sensitive data).
     */
    fun getText(): String? {
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }
}

/**
 * Remember a ClipboardManager instance.
 */
@Composable
fun rememberClipboardManager(context: Context): ClipboardManager {
    return remember(context) {
        ClipboardManager(context)
    }
}

/**
 * Composable that automatically clears clipboard when disposed.
 *
 * Usage:
 * ```kotlin
 * // In your composable where password is shown
 * var showPassword by remember { mutableStateOf(false) }
 *
 * if (showPassword) {
 *     AutoClearClipboard(clearClipboard = { clipboardManager.clearClipboard() })
 * }
 * ```
 */
@Composable
fun AutoClearClipboard(
    clearClipboard: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            clearClipboard()
        }
    }
}
