package com.example.logistics

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import java.io.File

object ImageLoaderConfig {
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Always use cache if available
            .logger(DebugLogger()) // For debugging
            .build()
    }
    
    /**
     * Clears both memory and disk cache for images
     */
    fun clearCache(context: Context, imageLoader: ImageLoader) {
        // Clear memory cache
        imageLoader.memoryCache?.clear()
        
        // Clear disk cache
        val cacheDir = context.cacheDir.resolve("image_cache")
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
        }
    }
}
