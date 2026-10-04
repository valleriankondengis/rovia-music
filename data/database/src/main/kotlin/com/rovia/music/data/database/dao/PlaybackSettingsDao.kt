package com.rovia.music.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.database.entity.PlaybackSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackSettingsDao {

    @Query(
        """
        SELECT *
        FROM playback_settings
        WHERE id = 1
        LIMIT 1
        """,
    )
    fun observeSettings(): Flow<PlaybackSettingsEntity?>

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsert(
        entity: PlaybackSettingsEntity,
    )
}