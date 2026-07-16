package com.alpha.vision.pro.gallery.domain.repository

import com.alpha.vision.pro.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeAllMedia(): Flow<List<MediaItem>>
    suspend fun getMediaById(id: Long): MediaItem?
    suspend fun deleteMedia(ids: List<Long>): Result<Unit>
    suspend fun stripExif(id: Long): Result<Unit>
    fun observeTrash(): Flow<List<MediaItem>>
    suspend fun restoreFromTrash(id: Long): Result<Unit>
    suspend fun permanentlyDelete(id: Long): Result<Unit>
    suspend fun sync(): Result<Unit>
    suspend fun copyMedia(id: Long, targetBucket: String): Result<Unit>
    suspend fun moveMedia(id: Long, targetBucket: String): Result<Unit>
    suspend fun renameMedia(id: Long, newName: String): Result<Unit>
    suspend fun getExifMetadata(id: Long): Result<com.alpha.vision.pro.gallery.domain.model.ExifData?>
}
