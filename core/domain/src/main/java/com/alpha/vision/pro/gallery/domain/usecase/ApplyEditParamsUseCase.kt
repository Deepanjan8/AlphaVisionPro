package com.alpha.vision.pro.gallery.domain.usecase

import android.graphics.Bitmap
import com.alpha.vision.pro.gallery.domain.model.EditParams
import com.alpha.vision.pro.gallery.domain.service.NativeEditService
import javax.inject.Inject

/**
 * Delegates pixel-level processing to the native C++ engine via [NativeEditService].
 * Returns a new Bitmap — original is NEVER mutated (non-destructive).
 */
class ApplyEditParamsUseCase @Inject constructor(
    private val nativeEditService: NativeEditService
) {
    suspend operator fun invoke(key: String, source: Bitmap, params: EditParams): Result<Bitmap> =
        nativeEditService.processImage(key, source, params)
}
