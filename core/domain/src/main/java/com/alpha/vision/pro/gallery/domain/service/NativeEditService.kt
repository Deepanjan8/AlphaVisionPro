package com.alpha.vision.pro.gallery.domain.service

import android.graphics.Bitmap
import com.alpha.vision.pro.gallery.domain.model.EditParams

interface NativeEditService {
    suspend fun processImage(key: String, source: Bitmap, params: EditParams): Result<Bitmap>
}
