package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.MediaStoreSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaStoreSyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: MediaStoreSyncStateEntity)

    @Update
    suspend fun update(state: MediaStoreSyncStateEntity)

    @Query("SELECT * FROM media_store_sync_state WHERE volumeName = :volumeName LIMIT 1")
    suspend fun getForVolume(volumeName: String): MediaStoreSyncStateEntity?

    @Query("SELECT * FROM media_store_sync_state ORDER BY volumeName ASC")
    fun observeAll(): Flow<List<MediaStoreSyncStateEntity>>
}
