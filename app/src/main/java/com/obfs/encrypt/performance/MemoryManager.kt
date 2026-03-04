package com.obfs.encrypt.performance

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Memory Manager for optimal memory usage and leak prevention.
 * 
 * Features:
 * - Automatic memory trimming on low memory warnings
 * - Cache management for images and data
 * - Memory pressure monitoring
 * - Automatic cleanup on app backgrounding
 */
class MemoryManager private constructor(
    private val application: Application
) : ComponentCallbacks2 {

    companion object {
        @Volatile
        private var instance: MemoryManager? = null

        fun getInstance(application: Application): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: MemoryManager(application).also { 
                    instance = it
                    application.registerComponentCallbacks(it)
                }
            }
        }

        /**
         * Get available memory in bytes.
         */
        fun getAvailableMemory(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        }

        /**
         * Get total allocated memory in bytes.
         */
        fun getAllocatedMemory(): Long {
            return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        }

        /**
         * Get max heap size in bytes.
         */
        fun getMaxMemory(): Long {
            return Runtime.getRuntime().maxMemory()
        }

        /**
         * Check if memory is running low (< 20% available).
         */
        fun isMemoryLow(): Boolean {
            val available = getAvailableMemory()
            val max = getMaxMemory()
            return available < max * 0.2
        }

        /**
         * Check if memory is critically low (< 10% available).
         */
        fun isMemoryCritical(): Boolean {
            val available = getAvailableMemory()
            val max = getMaxMemory()
            return available < max * 0.1
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _memoryLevel = MutableStateFlow(MemoryLevel.NORMAL)
    val memoryLevel = _memoryLevel.asStateFlow()

    enum class MemoryLevel {
        NORMAL,
        MODERATE,
        LOW,
        CRITICAL
    }

    // Registered callbacks for memory events
    private val trimCallbacks = mutableListOf<(MemoryLevel) -> Unit>()

    /**
     * Register a callback for memory trim events.
     */
    fun registerTrimCallback(callback: (MemoryLevel) -> Unit) {
        trimCallbacks.add(callback)
    }

    /**
     * Unregister a trim callback.
     */
    fun unregisterTrimCallback(callback: (MemoryLevel) -> Unit) {
        trimCallbacks.remove(callback)
    }

    override fun onTrimMemory(level: Int) {
        val memoryLevel = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> MemoryLevel.MODERATE
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryLevel.LOW
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryLevel.MODERATE
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryLevel.LOW
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryLevel.CRITICAL
            else -> MemoryLevel.NORMAL
        }

        _memoryLevel.value = memoryLevel
        
        // Notify all registered callbacks
        scope.launch {
            trimCallbacks.forEach { it.invoke(memoryLevel) }
        }

        // Automatically clear caches on critical memory
        if (memoryLevel == MemoryLevel.CRITICAL) {
            clearAllCaches()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op
    }

    override fun onLowMemory() {
        _memoryLevel.value = MemoryLevel.CRITICAL
        clearAllCaches()
    }

    /**
     * Clear all application caches.
     */
    private fun clearAllCaches() {
        // Clear Coil image cache
        application.applicationContext.let { ctx ->
            CoilImageLoader.clearCache(ctx)
        }

        // Clear code cache
        try {
            application.codeCacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // Ignore
        }

        // Clear regular cache
        try {
            application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
            // Ignore
        }

        // Suggest GC run (not guaranteed)
        System.gc()
    }

    /**
     * Perform aggressive memory cleanup.
     * Call this before memory-intensive operations.
     */
    fun performCleanup() {
        if (isMemoryLow()) {
            clearAllCaches()
        }
    }

    /**
     * Dispose and unregister from component callbacks.
     */
    fun dispose() {
        application.unregisterComponentCallbacks(this)
        trimCallbacks.clear()
        instance = null
    }
}

/**
 * Extension function to check memory status from any context.
 */
val Application.memoryManager: MemoryManager
    get() = MemoryManager.getInstance(this)
