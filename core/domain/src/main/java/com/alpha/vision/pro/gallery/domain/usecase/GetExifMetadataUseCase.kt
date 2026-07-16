package com.alpha.vision.pro.gallery.domain.usecase

import com.alpha.vision.pro.gallery.domain.model.ExifData
import com.alpha.vision.pro.gallery.domain.repository.MediaRepository
import javax.inject.Inject

class GetExifMetadataUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(id: Long): Result<ExifData?> =
        repository.getExifMetadata(id)
}
