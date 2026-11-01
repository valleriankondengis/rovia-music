package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.ArtworkAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtworkAssetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(asset: ArtworkAssetEntity): Long

    @Update
    suspend fun update(asset: ArtworkAssetEntity)

    @Query("SELECT * FROM artwork_assets WHERE assetKey = :assetKey LIMIT 1")
    suspend fun getByKey(assetKey: String): ArtworkAssetEntity?

    @Query("SELECT * FROM artwork_assets WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getByContentHash(contentHash: String): ArtworkAssetEntity?

    @Query("SELECT * FROM artwork_assets WHERE isAvailable = 1 ORDER BY lastAccessedAtEpochMillis DESC LIMIT :limit")
    fun observeRecentlyAccessed(limit: Int): Flow<List<ArtworkAssetEntity>>

    @Query("UPDATE artwork_assets SET lastAccessedAtEpochMillis = :accessedAtEpochMillis WHERE assetKey = :assetKey")
    suspend fun markAccessed(assetKey: String, accessedAtEpochMillis: Long)

    @Query("UPDATE artwork_assets SET isAvailable = 0 WHERE assetKey = :assetKey")
    suspend fun markUnavailable(assetKey: String)

    @Query("SELECT COUNT(*) FROM artwork_assets WHERE isPinned = 1 AND isAvailable = 1")
    suspend fun countPinnedAvailable(): Long
}
