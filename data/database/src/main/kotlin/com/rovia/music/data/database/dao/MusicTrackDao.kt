
package com.rovia.music.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicTrackDao {

    /*
     * UI catalog queries.
     * Excluded folders are hidden without deleting catalog entries.
     */

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE NOT EXISTS (
            SELECT 1
            FROM excluded_folders AS excluded
            WHERE music_tracks.relative_path IS NOT NULL
              AND substr(
                  music_tracks.relative_path,
                  1,
                  length(excluded.relative_path)
              ) = excluded.relative_path
        )
        ORDER BY title COLLATE NOCASE ASC,
                 volume_name ASC,
                 track_id ASC
        """,
    )
    fun observeAllTracks(): Flow<List<MusicTrackEntity>>

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE NOT EXISTS (
            SELECT 1
            FROM excluded_folders AS excluded
            WHERE music_tracks.relative_path IS NOT NULL
              AND substr(
                  music_tracks.relative_path,
                  1,
                  length(excluded.relative_path)
              ) = excluded.relative_path
        )
        ORDER BY title COLLATE NOCASE ASC,
                 volume_name ASC,
                 track_id ASC
        """,
    )
    suspend fun getAllTracks(): List<MusicTrackEntity>

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE NOT EXISTS (
            SELECT 1
            FROM excluded_folders AS excluded
            WHERE music_tracks.relative_path IS NOT NULL
              AND substr(
                  music_tracks.relative_path,
                  1,
                  length(excluded.relative_path)
              ) = excluded.relative_path
        )
        ORDER BY date_added_epoch_seconds DESC,
                 volume_name ASC,
                 track_id ASC
        LIMIT :limit
        """,
    )
    fun observeRecentlyAdded(
        limit: Int,
    ): Flow<List<MusicTrackEntity>>

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE NOT EXISTS (
            SELECT 1
            FROM excluded_folders AS excluded
            WHERE music_tracks.relative_path IS NOT NULL
              AND substr(
                  music_tracks.relative_path,
                  1,
                  length(excluded.relative_path)
              ) = excluded.relative_path
        )
        ORDER BY date_added_epoch_seconds DESC,
                 volume_name ASC,
                 track_id ASC
        LIMIT :limit
        """,
    )
    suspend fun getRecentlyAdded(
        limit: Int,
    ): List<MusicTrackEntity>

    /*
     * Catalog lookup and synchronization queries.
     * These include excluded folders.
     */

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE uri = :uri
        LIMIT 1
        """,
    )
    fun observeTrackByUri(
        uri: String,
    ): Flow<MusicTrackEntity?>

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE volume_name = :volumeName
          AND track_id = :trackId
        LIMIT 1
        """,
    )
    suspend fun getTrackById(
        volumeName: String,
        trackId: Long,
    ): MusicTrackEntity?

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE volume_name = :volumeName
        ORDER BY track_id ASC
        """,
    )
    suspend fun getTracksByVolume(
        volumeName: String,
    ): List<MusicTrackEntity>

    @Query(
        """
        SELECT track_id
        FROM music_tracks
        WHERE volume_name = :volumeName
        ORDER BY track_id ASC
        """,
    )
    suspend fun getTrackIdsByVolume(
        volumeName: String,
    ): List<Long>

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE volume_name = :volumeName
          AND track_id IN (:trackIds)
        """,
    )
    suspend fun getTracksByIds(
        volumeName: String,
        trackIds: List<Long>,
    ): List<MusicTrackEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM music_tracks
        """,
    )
    suspend fun getTrackCount(): Long

    /*
     * Embedded metadata enrichment queue.
     */

    @Query(
        """
        SELECT *
        FROM music_tracks
        WHERE embedded_metadata_status = 0
        ORDER BY date_added_epoch_seconds DESC,
                 volume_name ASC,
                 track_id ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingEmbeddedMetadata(
        limit: Int,
    ): List<MusicTrackEntity>

    @Query(
        """
        UPDATE music_tracks
        SET release_date = :releaseDate,
            copyright = :copyright,
            embedded_metadata_signature = :signature,
            embedded_metadata_status = :status,
            embedded_metadata_updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE volume_name = :volumeName
          AND track_id = :trackId
        """,
    )
    suspend fun updateEmbeddedMetadata(
        volumeName: String,
        trackId: Long,
        releaseDate: String?,
        copyright: String?,
        signature: String,
        status: Int,
        updatedAtEpochMillis: Long,
    )

    /*
     * Keep the denormalized Recent Play snapshot in sync with
     * the embedded metadata extracted for the same MediaStore URI.
     */
    @Query(
        """
        UPDATE recent_plays
        SET release_date = :releaseDate,
            copyright = :copyright
        WHERE uri = :uri
        """,
    )
    suspend fun updateRecentPlayEmbeddedMetadata(
        uri: String,
        releaseDate: String?,
        copyright: String?,
    )

    /*
     * Catalog mutations.
     */

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsert(
        entity: MusicTrackEntity,
    )

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun upsertAll(
        entities: List<MusicTrackEntity>,
    )

    @Query(
        """
        DELETE FROM music_tracks
        WHERE volume_name = :volumeName
          AND track_id = :trackId
        """,
    )
    suspend fun deleteByTrackId(
        volumeName: String,
        trackId: Long,
    )

    @Query(
        """
        DELETE FROM music_tracks
        WHERE volume_name = :volumeName
          AND track_id IN (:trackIds)
        """,
    )
    suspend fun deleteByVolumeAndTrackIds(
        volumeName: String,
        trackIds: List<Long>,
    )

    @Query(
        """
        DELETE FROM music_tracks
        WHERE volume_name = :volumeName
        """,
    )
    suspend fun deleteAllForVolume(
        volumeName: String,
    )

    @Query(
        """
        DELETE FROM music_tracks
        """,
    )
    suspend fun deleteAll()
}
