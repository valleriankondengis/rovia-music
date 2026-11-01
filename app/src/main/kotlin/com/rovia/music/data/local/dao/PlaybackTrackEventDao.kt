package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.PlaybackTrackEventEntity
import kotlinx.coroutines.flow.Flow

data class TrackPlaybackAggregate(
    val trackId: Long?,
    val displayTitle: String,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long?
)

data class ArtistPlaybackAggregate(
    val artistId: Long,
    val displayName: String,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long?
)

data class AlbumPlaybackAggregate(
    val albumId: Long?,
    val displayTitle: String,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long?
)

data class PlaylistPlaybackAggregate(
    val playlistKey: String,
    val displayTitle: String,
    val eventCount: Long,
    val qualifiedPlayCount: Long,
    val totalListenedDurationMs: Long,
    val lastPlayedAtEpochMillis: Long?
)

@Dao
interface PlaybackTrackEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: PlaybackTrackEventEntity): Long

    @Update
    suspend fun update(event: PlaybackTrackEventEntity)

    @Query(
        """
        SELECT *
        FROM playback_track_events
        WHERE eventKey = :eventKey
        LIMIT 1
        """
    )
    suspend fun getByEventKey(eventKey: String): PlaybackTrackEventEntity?

    @Query(
        """
        SELECT *
        FROM playback_track_events
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
        ORDER BY startedAtEpochMillis ASC, eventId ASC
        """
    )
    suspend fun getEventsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<PlaybackTrackEventEntity>

    @Query(
        """
        SELECT *
        FROM playback_track_events
        WHERE sessionId = :sessionId
        ORDER BY startedAtEpochMillis ASC, eventId ASC
        """
    )
    suspend fun getEventsForSession(
        sessionId: Long
    ): List<PlaybackTrackEventEntity>

    @Query(
        """
        SELECT *
        FROM playback_track_events
        WHERE trackId = :trackId
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getEventsForTrack(
        trackId: Long,
        limit: Int
    ): List<PlaybackTrackEventEntity>

    @Query(
        """
        SELECT *
        FROM playback_track_events
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    fun observeLatestEvents(
        limit: Int
    ): Flow<List<PlaybackTrackEventEntity>>

    @Query(
        """
        SELECT COUNT(*)
        FROM playback_track_events
        WHERE trackId = :trackId
          AND qualifiedPlay = 1
        """
    )
    suspend fun getQualifiedPlayCount(trackId: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(heardDurationMs), 0)
        FROM playback_track_events
        WHERE trackId = :trackId
        """
    )
    suspend fun getTotalListenedDurationForTrack(trackId: Long): Long

    @Query(
        """
        SELECT
            MAX(e.trackId) AS trackId,
            MAX(COALESCE(t.title, e.trackTitleSnapshot)) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        LEFT JOIN tracks AS t ON t.trackId = e.trackId
        GROUP BY
            CASE
                WHEN e.trackId IS NOT NULL
                    THEN 'track:' || CAST(e.trackId AS TEXT)
                ELSE
                    'snapshot:' ||
                    LOWER(TRIM(e.trackTitleSnapshot)) || '|' ||
                    LOWER(TRIM(COALESCE(e.artistCreditSnapshot, ''))) || '|' ||
                    LOWER(TRIM(COALESCE(e.albumTitleSnapshot, '')))
            END
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopTracks(limit: Int): List<TrackPlaybackAggregate>

    @Query(
        """
        SELECT
            MAX(e.trackId) AS trackId,
            MAX(COALESCE(t.title, e.trackTitleSnapshot)) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        LEFT JOIN tracks AS t ON t.trackId = e.trackId
        WHERE e.startedAtEpochMillis >= :startEpochMillis
          AND e.startedAtEpochMillis < :endEpochMillis
        GROUP BY
            CASE
                WHEN e.trackId IS NOT NULL
                    THEN 'track:' || CAST(e.trackId AS TEXT)
                ELSE
                    'snapshot:' ||
                    LOWER(TRIM(e.trackTitleSnapshot)) || '|' ||
                    LOWER(TRIM(COALESCE(e.artistCreditSnapshot, ''))) || '|' ||
                    LOWER(TRIM(COALESCE(e.albumTitleSnapshot, '')))
            END
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopTracksBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<TrackPlaybackAggregate>

    @Query(
        """
        SELECT
            a.artistId AS artistId,
            a.name AS displayName,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        INNER JOIN track_artist_cross_ref AS ta ON ta.trackId = e.trackId
        INNER JOIN artists AS a ON a.artistId = ta.artistId
        GROUP BY a.artistId, a.name
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopArtists(limit: Int): List<ArtistPlaybackAggregate>

    @Query(
        """
        SELECT
            a.artistId AS artistId,
            a.name AS displayName,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        INNER JOIN track_artist_cross_ref AS ta ON ta.trackId = e.trackId
        INNER JOIN artists AS a ON a.artistId = ta.artistId
        WHERE e.startedAtEpochMillis >= :startEpochMillis
          AND e.startedAtEpochMillis < :endEpochMillis
        GROUP BY a.artistId, a.name
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopArtistsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<ArtistPlaybackAggregate>

    @Query(
        """
        SELECT
            MAX(t.albumReleaseId) AS albumId,
            MAX(COALESCE(a.title, e.albumTitleSnapshot, '')) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        LEFT JOIN tracks AS t ON t.trackId = e.trackId
        LEFT JOIN albums AS a ON a.albumId = t.albumReleaseId
        GROUP BY
            CASE
                WHEN t.albumReleaseId IS NOT NULL
                    THEN 'album:' || CAST(t.albumReleaseId AS TEXT)
                ELSE
                    'snapshot:' ||
                    LOWER(TRIM(COALESCE(e.albumTitleSnapshot, ''))) || '|' ||
                    LOWER(TRIM(COALESCE(e.artistCreditSnapshot, '')))
            END
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopAlbums(limit: Int): List<AlbumPlaybackAggregate>

    @Query(
        """
        SELECT
            MAX(t.albumReleaseId) AS albumId,
            MAX(COALESCE(a.title, e.albumTitleSnapshot, '')) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN e.qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(e.heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(e.startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events AS e
        LEFT JOIN tracks AS t ON t.trackId = e.trackId
        LEFT JOIN albums AS a ON a.albumId = t.albumReleaseId
        WHERE e.startedAtEpochMillis >= :startEpochMillis
          AND e.startedAtEpochMillis < :endEpochMillis
        GROUP BY
            CASE
                WHEN t.albumReleaseId IS NOT NULL
                    THEN 'album:' || CAST(t.albumReleaseId AS TEXT)
                ELSE
                    'snapshot:' ||
                    LOWER(TRIM(COALESCE(e.albumTitleSnapshot, ''))) || '|' ||
                    LOWER(TRIM(COALESCE(e.artistCreditSnapshot, '')))
            END
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopAlbumsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<AlbumPlaybackAggregate>

    @Query(
        """
        SELECT
            playbackSourceEntityKey AS playlistKey,
            MAX(playbackSourceTitleSnapshot) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events
        WHERE playbackSourceType = 'PLAYLIST'
        GROUP BY playbackSourceEntityKey
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopPlaylists(limit: Int): List<PlaylistPlaybackAggregate>

    @Query(
        """
        SELECT
            playbackSourceEntityKey AS playlistKey,
            MAX(playbackSourceTitleSnapshot) AS displayTitle,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN qualifiedPlay = 1 THEN 1 ELSE 0 END)
                AS qualifiedPlayCount,
            COALESCE(SUM(heardDurationMs), 0)
                AS totalListenedDurationMs,
            MAX(startedAtEpochMillis) AS lastPlayedAtEpochMillis
        FROM playback_track_events
        WHERE playbackSourceType = 'PLAYLIST'
          AND startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
        GROUP BY playbackSourceEntityKey
        ORDER BY
            qualifiedPlayCount DESC,
            totalListenedDurationMs DESC,
            lastPlayedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getTopPlaylistsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<PlaylistPlaybackAggregate>

    @Query(
        """
        SELECT COUNT(*)
        FROM playback_track_events
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
        """
    )
    suspend fun countEventsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM playback_track_events
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
          AND qualifiedPlay = 1
        """
    )
    suspend fun countQualifiedEventsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Long

    @Query(
        """
        SELECT COUNT(
            DISTINCT CASE
                WHEN trackId IS NOT NULL
                    THEN 'track:' || CAST(trackId AS TEXT)
                ELSE
                    'snapshot:' ||
                    LOWER(TRIM(trackTitleSnapshot)) || '|' ||
                    LOWER(TRIM(COALESCE(artistCreditSnapshot, ''))) || '|' ||
                    LOWER(TRIM(COALESCE(albumTitleSnapshot, '')))
            END
        )
        FROM playback_track_events
        WHERE startedAtEpochMillis >= :startEpochMillis
          AND startedAtEpochMillis < :endEpochMillis
        """
    )
    suspend fun countDistinctTrackIdentitiesBetween(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Long
}