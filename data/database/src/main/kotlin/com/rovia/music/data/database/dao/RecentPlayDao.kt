package com.rovia.music.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.rovia.music.data.database.entity.RecentPlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPlayDao {

    @Query(
        """
        SELECT *
        FROM recent_plays
        ORDER BY last_played_at_epoch_millis DESC,
                 track_id ASC
        """,
    )
    fun observeRecentPlays(): Flow<List<RecentPlayEntity>>

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsert(
        entity: RecentPlayEntity,
    )

    @Query(
        """
        DELETE FROM recent_plays
        WHERE track_id NOT IN (
            SELECT track_id
            FROM recent_plays
            ORDER BY last_played_at_epoch_millis DESC,
                     track_id ASC
            LIMIT 10
        )
        """,
    )
    suspend fun trimToTen()

    @Transaction
    suspend fun record(
        entity: RecentPlayEntity,
    ) {
        upsert(entity)
        trimToTen()
    }
}