package com.obfs.encrypt.performance

import android.content.Context
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Optimized Coil image loader configuration for butter-smooth image loading.
 * Features:
 * - Aggressive memory caching for thumbnails
 * - Disk caching for faster subsequent loads
 * - Optimized for file browser thumbnails
 * - Automatic GIF support
 */
object CoilImageLoader {

    @Volatile
    private var instance: coil.ImageLoader? = null

    /**
     * Get or create the optimized image loader instance.
     */
    fun getInstance(context: Context): coil.ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: createOptimizedImageLoader(context).also { instance = it }
        }
    }

    /**
     * Creates an optimized image loader with performance-focused settings.
     */
    private fun createOptimizedImageLoader(context: Context): coil.ImageLoader {
        val cacheDir = File(context.cacheDir, "coil_thumbnails")
        val maxMemoryCacheSize = (Runtime.getRuntime().maxMemory() * 0.25).toLong() // 25% of heap
        val maxDiskCacheSize = 100L * 1024 * 1024 // 100MB

        return coil.ImageLoader.Builder(context)
            // Memory cache - optimized for thumbnails
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(maxMemoryCacheSize.toDouble() / Int.MAX_VALUE)
                    .strongReferencesEnabled(true) // Keep thumbnails in memory
                    .build()
            }
            // Disk cache - persistent storage for faster reloads
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.toOkioPath())
                    .maxSizePercent(maxDiskCacheSize.toDouble() / Long.MAX_VALUE)
                    .maxSizeBytes(maxDiskCacheSize)
                    .build()
            }
            // Optimized decoders
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // Performance optimizations
            .crossfade(true)
            .crossfade(150) // Quick crossfade for smooth transitions
            .respectCacheHeaders(false) // Ignore server cache headers for local files
            .allowHardware(android.os.Build.VERSION.SDK_INT >= 26) // Hardware bitmaps on API 26+
            // Cache policies - aggressive caching for local files
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.DISABLED) // No network needed for local files
            .build()
    }

    /**
     * Clear all cached images to free up memory.
     * Call this when navigating away from file browser or on low memory warnings.
     */
    fun clearCache(context: Context) {
        instance?.diskCache?.clear()
        instance?.memoryCache?.clear()
    }

    /**
     * Clear only memory cache (faster, use during scrolling).
     */
    fun clearMemoryCache() {
        instance?.memoryCache?.clear()
    }

    /**
     * Dispose of the image loader (call in onDestroy).
     */
    fun dispose() {
        instance?.shutdown()
        instance = null
    }
}
