package com.alpha.vision.pro.gallery.data.nativelib

import android.graphics.Bitmap

/**
 * Kotlin-side JNI bridge to the C++ image processing engine.
 * All methods are @JvmStatic to enable direct C++ callback dispatch.
 * Buffer management: Bitmaps are passed as ARGB_8888; C++ receives
 * raw pixel pointer via AndroidBitmap_lockPixels — zero-copy.
 */
class NativeImageProcessor {

    init {
        System.loadLibrary("alpha_vision_native")
    }

    /**
     * Apply exposure, contrast, saturation adjustments via C++ engine.
     * @param src       Source ARGB_8888 bitmap (NOT recycled internally)
     * @param exposure  -1.0 to +1.0
     * @param contrast  -1.0 to +1.0
     * @param saturation -1.0 to +1.0
     * @param warmth    -1.0 to +1.0
     * @param highlights -1.0 to +1.0
     * @param shadows   -1.0 to +1.0
     * @param sharpness  0.0 to +1.0
     * @return New processed Bitmap (caller owns lifecycle)
     */
    external fun processImage(
        src        : Bitmap,
        exposure   : Float,
        contrast   : Float,
        saturation : Float,
        warmth     : Float,
        highlights : Float,
        shadows    : Float,
        sharpness  : Float
    ): Bitmap

    /**
     * Strip EXIF GPS + camera metadata at native level.
     * Operates on raw file bytes, returns cleaned byte array.
     */
    external fun stripExifNative(jpegBytes: ByteArray): ByteArray

    /**
     * Returns native heap usage in bytes for the processing buffer pool.
     */
    external fun getNativeMemoryUsage(): Long

    /**
     * Sets the native buffer pool size (in MB). Affects memory vs speed trade-off.
     */
    external fun setBufferPoolSizeMb(sizeMb: Int)

    /**
     * Extracts EXIF metadata from raw image byte array.
     * Uses native EXIF parser and caches the parsed result in the Rust caching index.
     */
    external fun getExifMetadata(key: String, jpegBytes: ByteArray): String?

    /**
     * Clears the cached EXIF metadata for the given key, or all keys if null.
     */
    external fun clearExifCache(key: String?)
}
