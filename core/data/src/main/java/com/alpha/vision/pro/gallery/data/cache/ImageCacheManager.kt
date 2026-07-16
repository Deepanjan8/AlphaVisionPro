package com.alpha.vision.pro.gallery.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.alpha.vision.pro.gallery.domain.model.EditParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val maxMemory = Runtime.getRuntime().maxMemory()
    private val cacheSize = (maxMemory * 0.15).toInt() // 15% of max VM memory in bytes

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val diskCacheDir: File
        get() = context.cacheDir.resolve("edit_cache").apply {
            if (!exists()) mkdirs()
        }

    private val maxDiskSize = 50 * 1024 * 1024 // 50 MB

    fun get(key: String, params: EditParams): Bitmap? {
        val cacheKey = generateCacheKey(key, params)
        // 1. Check RAM cache
        val memoryResult = memoryCache.get(cacheKey)
        if (memoryResult != null) return memoryResult

        // 2. Check Disk cache
        val diskFile = File(diskCacheDir, hashString(cacheKey))
        if (diskFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bitmap != null) {
                // Populate back to RAM cache
                memoryCache.put(cacheKey, bitmap)
                return bitmap
            }
        }
        return null
    }

    suspend fun put(key: String, params: EditParams, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(key, params)
        // 1. Put in RAM cache
        memoryCache.put(cacheKey, bitmap)

        // 2. Save to Disk cache
        runCatching {
            val hashedName = hashString(cacheKey)
            val file = File(diskCacheDir, hashedName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            pruneDiskCacheIfNeeded()
        }
    }

    fun trimMemory() {
        memoryCache.evictAll()
    }

    private fun generateCacheKey(key: String, params: EditParams): String {
        return "$key-exp:${params.exposure}-cnt:${params.contrast}-sat:${params.saturation}-wrm:${params.warmth}-hl:${params.highlights}-sd:${params.shadows}-sh:${params.sharpness}"
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pruneDiskCacheIfNeeded() {
        val files = diskCacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize > maxDiskSize) {
            // Sort by last modified (oldest first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                val size = file.length()
                if (file.delete()) {
                    totalSize -= size
                    if (totalSize <= maxDiskSize * 0.8) {
                        break
                    }
                }
            }
        }
    }
}
