package com.obfs.encrypt.performance

import android.os.Trace

/**
 * Performance tracing utilities for profiling app performance.
 * 
 * Usage:
 * ```
 * TraceSection.begin("operation_name")
 * try {
 *     // Your code here
 * } finally {
 *     TraceSection.end()
 * }
 * ```
 * 
 * Or use the auto-closeable:
 * ```
 * TraceSection.trace("operation_name") {
 *     // Your code here
 * }
 * ```
 */
object TraceSection {

    /**
     * Begin a trace section.
     * Only active in debug builds or when profiling is enabled.
     */
    fun begin(sectionName: String) {
        if (Trace.isEnabled()) {
            Trace.beginSection(sectionName)
        }
    }

    /**
     * End the current trace section.
     */
    fun end() {
        if (Trace.isEnabled()) {
            Trace.endSection()
        }
    }

    /**
     * Trace a block of code with auto-cleanup.
     * 
     * Example:
     * ```
     * val result = trace("encrypt_file") {
     *     encryptionHelper.encrypt(...)
     * }
     * ```
     */
    inline fun <T> trace(sectionName: String, block: () -> T): T {
        begin(sectionName)
        try {
            return block()
        } finally {
            end()
        }
    }

    /**
     * Check if tracing should be enabled.
     */
    fun shouldTrace(): Boolean {
        return Trace.isEnabled()
    }

    /**
     * Counter for tracking metrics.
     */
    fun setCounter(counterName: String, value: Long) {
        if (Trace.isEnabled()) {
            Trace.setCounter(counterName, value)
        }
    }

    /**
     * Async trace for tracking operations across threads.
     */
    fun beginAsync(traceName: String, cookie: Int) {
        if (Trace.isEnabled()) {
            Trace.beginAsyncSection(traceName, cookie)
        }
    }

    fun endAsync(traceName: String, cookie: Int) {
        if (Trace.isEnabled()) {
            Trace.endAsyncSection(traceName, cookie)
        }
    }
}

/**
 * Common trace sections for the app.
 */
object TraceSections {
    // File Operations
    const val FILE_LOAD = "file_load"
    const val FILE_ENCRYPT = "file_encrypt"
    const val FILE_DECRYPT = "file_decrypt"
    const val FILE_SORT = "file_sort"
    
    // UI Operations
    const val COMPOSE_LAYOUT = "compose_layout"
    const val COMPOSE_DRAW = "compose_draw"
    const val LIST_SCROLL = "list_scroll"
    const val ITEM_BIND = "item_bind"
    
    // Image Operations
    const val IMAGE_LOAD = "image_load"
    const val IMAGE_DECODE = "image_decode"
    const val IMAGE_CACHE = "image_cache"
    
    // Navigation
    const val NAVIGATE_TO = "navigate_to"
    const val NAVIGATE_BACK = "navigate_back"
    
    // Encryption
    const val KEY_DERIVATION = "key_derivation"
    const val CHUNK_ENCRYPT = "chunk_encrypt"
    const val CHUNK_DECRYPT = "chunk_decrypt"
}
