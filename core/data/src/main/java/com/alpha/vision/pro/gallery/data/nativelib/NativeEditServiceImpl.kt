package com.alpha.vision.pro.gallery.data.nativelib

import android.graphics.Bitmap
import com.alpha.vision.pro.gallery.domain.model.EditParams
import com.alpha.vision.pro.gallery.domain.service.NativeEditService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [NativeEditService] — bridges domain layer to [NativeImageProcessor].
 * Runs on Dispatchers.Default to avoid blocking UI or IO pool.
 */
@Singleton
class NativeEditServiceImpl @Inject constructor(
    private val processor: NativeImageProcessor,
    private val cacheManager: com.alpha.vision.pro.gallery.data.cache.ImageCacheManager
) : NativeEditService {

    override suspend fun processImage(
        key: String,
        source : Bitmap,
        params : EditParams
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            val cached = cacheManager.get(key, params)
            if (cached != null) return@runCatching cached

            // Ensure ARGB_8888 — Rust engine requires this format
            val argbSource = if (source.config == Bitmap.Config.ARGB_8888) source
                             else source.copy(Bitmap.Config.ARGB_8888, false)
            val result = processor.processImage(
                src        = argbSource,
                exposure   = params.exposure,
                contrast   = params.contrast,
                saturation = params.saturation,
                warmth     = params.warmth,
                highlights = params.highlights,
                shadows    = params.shadows,
                sharpness  = params.sharpness
            )
            // Recycle temporary copy if we created one
            if (argbSource !== source) argbSource.recycle()
            
            cacheManager.put(key, params, result)
            
            result
        }
    }
}
