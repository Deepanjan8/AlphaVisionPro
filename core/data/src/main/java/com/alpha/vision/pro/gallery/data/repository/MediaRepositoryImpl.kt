package com.alpha.vision.pro.gallery.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.alpha.vision.pro.gallery.data.db.dao.MediaDao
import com.alpha.vision.pro.gallery.data.db.entity.MediaEntity
import com.alpha.vision.pro.gallery.domain.model.ExifData
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import com.alpha.vision.pro.gallery.domain.repository.EditableMediaRepository
import com.alpha.vision.pro.gallery.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao
) : MediaRepository, EditableMediaRepository {

    private val resolver: ContentResolver get() = context.contentResolver
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        registerContentObserver()
    }

    private fun registerContentObserver() {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                repositoryScope.launch {
                    runCatching { syncFromMediaStore() }
                }
            }
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    override suspend fun sync(): Result<Unit> = runCatching {
        syncFromMediaStore()
    }

    override fun observeAllMedia(): Flow<List<MediaItem>> =
        dao.observeAllMedia().map { list ->
            if (list.isEmpty()) syncFromMediaStore()
            list.map { it.toDomain() }
        }

    override suspend fun getMediaById(id: Long): MediaItem? =
        withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }

    override suspend fun deleteMedia(ids: List<Long>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ids.forEach { id ->
                dao.setTrashed(id, true, System.currentTimeMillis())
            }
        }
    }

    override suspend fun stripExif(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching<Unit> {
            val entity = dao.getById(id) ?: error("Not found")
            val uri    = Uri.parse(entity.uri)
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor).apply {
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE,  null)
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    setAttribute(ExifInterface.TAG_GPS_ALTITUDE,  null)
                    setAttribute(ExifInterface.TAG_MAKE,          null)
                    setAttribute(ExifInterface.TAG_MODEL,         null)
                    saveAttributes()
                }
            } ?: throw IOException("Cannot open $uri")
        }
    }

    override fun observeTrash(): Flow<List<MediaItem>> =
        dao.observeTrash().map { it.map { e -> e.toDomain() } }

    override suspend fun restoreFromTrash(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { dao.setTrashed(id, false, null) }
    }

    override suspend fun permanentlyDelete(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = dao.getById(id) ?: return@runCatching
            resolver.delete(Uri.parse(entity.uri), null, null)
            dao.deleteById(id)
        }
    }

    override suspend fun saveEditedBitmap(originalId: Long, bitmap: Bitmap): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "edited_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Insert failed")
                resolver.openOutputStream(newUri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(newUri, values, null, null)
                }
                val newId = newUri.lastPathSegment?.toLong() ?: -1L
                newId
            }
        }

    private suspend fun syncFromMediaStore() = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val cursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        ) ?: return@withContext

        val entities = mutableListOf<MediaEntity>()
        val mediaStoreIds = mutableSetOf<Long>()
        cursor.use {
            val idCol       = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol     = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol     = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val addedCol    = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modCol      = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val widthCol    = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol   = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol     = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val bucketCol   = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (it.moveToNext()) {
                val id  = it.getLong(idCol)
                mediaStoreIds.add(id)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                ).toString()
                entities += MediaEntity(
                    id           = id,
                    uri          = uri,
                    displayName  = it.getString(nameCol) ?: "",
                    mimeType     = it.getString(mimeCol) ?: "image/jpeg",
                    dateAdded    = it.getLong(addedCol),
                    dateModified = it.getLong(modCol),
                    width        = it.getInt(widthCol),
                    height       = it.getInt(heightCol),
                    size         = it.getLong(sizeCol),
                    bucketName   = it.getString(bucketCol) ?: "Camera"
                )
            }
        }
        dao.upsertAll(entities)

        // Delete items from database that are no longer in MediaStore (excluding vaulted ones)
        val localIds = dao.getAllNonVaultedIds()
        val idsToDelete = localIds.filter { it !in mediaStoreIds }
        if (idsToDelete.isNotEmpty()) {
            dao.deleteIds(idsToDelete)
        }
    }

    private fun MediaEntity.toDomain() = MediaItem(
        id           = id,
        uri          = uri,
        displayName  = displayName,
        mimeType     = mimeType,
        dateAdded    = dateAdded,
        dateModified = dateModified,
        width        = width,
        height       = height,
        size         = size,
        bucketName   = bucketName,
        isVaulted    = isVaulted
    )
}
