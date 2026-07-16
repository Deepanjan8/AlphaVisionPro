package com.alpha.vision.pro.gallery

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alpha.vision.pro.gallery.data.cache.CacheCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class AlphaVisionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleCacheCleanup()
    }

    private fun scheduleCacheCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}
