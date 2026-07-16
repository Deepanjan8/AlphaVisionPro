package com.alpha.vision.pro.gallery.data.db.dao

import androidx.room.*
import com.alpha.vision.pro.gallery.data.db.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE isVaulted = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun observeAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE isVaulted = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun observeVaultMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<MediaEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Upsert
    suspend fun upsert(item: MediaEntity)

    @Query("UPDATE media SET isVaulted = :vaulted WHERE id = :id")
    suspend fun setVaulted(id: Long, vaulted: Boolean)

    @Query("UPDATE media SET isTrashed = :trashed, trashedAt = :at WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, at: Long?)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media WHERE isTrashed = 1 AND trashedAt < :before")
    suspend fun purgeOldTrash(before: Long)

    @Query("SELECT id FROM media WHERE isVaulted = 0")
    suspend fun getAllNonVaultedIds(): List<Long>

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}
