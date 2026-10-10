
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

    /**
     * Observes every unique track recorded in Recent Play.
     *
     * The collection is ordered by most recently played first.
     * No arbitrary item limit is applied at the database layer.
     */
    @Query(
        """
        SELECT *
        FROM recent_plays
        ORDER BY last_played_at_epoch_millis DESC,
                 uri ASC
        """,
    )
    fun observeRecentPlays(): Flow<List<RecentPlayEntity>>

    /**
     * Inserts a new recent track or updates the existing record
     * when the same track is played again.
     */
    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsert(
        entity: RecentPlayEntity,
    )

    /**
     * Records the latest playback information for a track.
     *
     * Existing tracks are updated instead of duplicated.
     * Older records are retained, regardless of the collection size.
     */
    @Transaction
    suspend fun record(
        entity: RecentPlayEntity,
    ) {
        upsert(entity)
    }
}
