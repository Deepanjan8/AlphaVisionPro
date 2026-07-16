package com.alpha.vision.pro.gallery.data.cache

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = applicationContext.cacheDir.resolve("edit_cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val files = cacheDir.listFiles() ?: return@runCatching Result.success()
                val totalSize = files.sumOf { it.length() }
                val maxDiskSize = 50 * 1024 * 1024 // 50 MB
                
                if (totalSize > maxDiskSize) {
                    // Sort by last modified (oldest first)
                    val sortedFiles = files.sortedBy { it.lastModified() }
                    var currentSize = totalSize
                    for (file in sortedFiles) {
                        val size = file.length()
                        if (file.delete()) {
                            currentSize -= size
                            if (currentSize <= maxDiskSize * 0.5) { // Prune down to 25MB (50%)
                                break
                            }
                        }
                    }
                }
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
