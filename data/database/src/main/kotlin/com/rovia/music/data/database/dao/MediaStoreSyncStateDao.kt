package com.rovia.music.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.database.entity.MediaStoreSyncStateEntity

@Dao
interface MediaStoreSyncStateDao {

    @Query(
        """
        SELECT *
        FROM media_store_sync_state
        WHERE volume_name = :volumeName
        LIMIT 1
        """,
    )
    suspend fun getByVolume(
        volumeName: String,
    ): MediaStoreSyncStateEntity?

    @Query(
        """
        SELECT *
        FROM media_store_sync_state
        ORDER BY volume_name ASC
        """,
    )
    suspend fun getAll(): List<MediaStoreSyncStateEntity>

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsert(
        entity: MediaStoreSyncStateEntity,
    )

    @Query(
        """
        DELETE FROM media_store_sync_state
        WHERE volume_name = :volumeName
        """,
    )
    suspend fun deleteByVolume(
        volumeName: String,
    )

    @Query(
        """
        DELETE FROM media_store_sync_state
        """,
    )
    suspend fun deleteAll()
}