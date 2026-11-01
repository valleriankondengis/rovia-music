package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class TrackSearchGenreRow(
    val trackId: Long,
    val genreName: String,
)

data class TrackSearchArtistRow(
    val trackId: Long,
    val artistName: String,
)

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun update(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): TrackEntity?

    @Query(
        "SELECT * FROM tracks WHERE mediaStoreId = :mediaStoreId AND volumeName IS :volumeName LIMIT 1"
    )
    suspend fun getByMediaStoreIdentity(
        mediaStoreId: Long,
        volumeName: String?,
    ): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE ASC, trackId ASC")
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY trackId ASC")
    suspend fun getAllAvailableTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY dateAddedSeconds DESC, trackId DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY dateAddedSeconds DESC, trackId DESC LIMIT :limit")
    suspend fun getRecentlyAdded(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND normalizedTitle LIKE '%' || :normalizedQuery || '%' ORDER BY title COLLATE NOCASE ASC LIMIT :limit")
    fun searchByNormalizedTitle(normalizedQuery: String, limit: Int): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT tracks.*
        FROM tracks
        LEFT JOIN albums ON albums.albumId = tracks.albumReleaseId
        WHERE tracks.isAvailable = 1
          AND (
            tracks.normalizedTitle LIKE '%' || :normalizedQuery || '%'
            OR LOWER(COALESCE(tracks.artistCreditRaw, '')) LIKE '%' || :normalizedQuery || '%'
            OR LOWER(COALESCE(tracks.albumArtistCreditRaw, '')) LIKE '%' || :normalizedQuery || '%'
            OR LOWER(COALESCE(tracks.albumTitleSnapshot, '')) LIKE '%' || :normalizedQuery || '%'
            OR albums.normalizedTitle LIKE '%' || :normalizedQuery || '%'
            OR LOWER(COALESCE(albums.albumArtistCreditRaw, '')) LIKE '%' || :normalizedQuery || '%'
            OR EXISTS (
                SELECT 1
                FROM track_artist_cross_ref
                INNER JOIN artists ON artists.artistId = track_artist_cross_ref.artistId
                WHERE track_artist_cross_ref.trackId = tracks.trackId
                  AND artists.normalizedName LIKE '%' || :normalizedQuery || '%'
            )
            OR EXISTS (
                SELECT 1
                FROM track_genre_cross_ref
                INNER JOIN genres ON genres.genreId = track_genre_cross_ref.genreId
                WHERE track_genre_cross_ref.trackId = tracks.trackId
                  AND genres.normalizedName LIKE '%' || :normalizedQuery || '%'
            )
          )
        ORDER BY
            CASE
                WHEN tracks.normalizedTitle = :normalizedQuery THEN 0
                WHEN tracks.normalizedTitle LIKE :normalizedQuery || '%' THEN 1
                WHEN tracks.normalizedTitle LIKE '%' || :normalizedQuery || '%' THEN 2
                ELSE 3
            END ASC,
            tracks.title COLLATE NOCASE ASC,
            tracks.trackId ASC
        LIMIT :limit
        """
    )
    fun searchAvailableTracks(
        normalizedQuery: String,
        limit: Int,
    ): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE isAvailable = 1 AND albumReleaseId IN (:albumIds) " +
            "ORDER BY albumReleaseId ASC, CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, " +
            "discNumber ASC, CASE WHEN trackNumber IS NULL THEN 1 ELSE 0 END, " +
            "trackNumber ASC, title COLLATE NOCASE ASC, trackId ASC"
    )
    suspend fun getAvailableTracksForAlbums(albumIds: List<Long>): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE isAvailable = 1 AND albumReleaseId IN (:albumIds) " +
            "ORDER BY albumReleaseId ASC, CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, " +
            "discNumber ASC, CASE WHEN trackNumber IS NULL THEN 1 ELSE 0 END, " +
            "trackNumber ASC, title COLLATE NOCASE ASC, trackId ASC"
    )
    fun observeTracksForAlbums(albumIds: List<Long>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND trackId IN (:trackIds)")
    suspend fun getAvailableTracksByIds(trackIds: List<Long>): List<TrackEntity>

    @Query(
        """
        SELECT tracks.*
        FROM tracks
        WHERE tracks.isAvailable = 1
          AND tracks.trackId IN (
              SELECT track_artist_cross_ref.trackId
              FROM track_artist_cross_ref
              INNER JOIN tracks AS creditedTracks
                  ON creditedTracks.trackId = track_artist_cross_ref.trackId
              WHERE creditedTracks.isAvailable = 1
                AND track_artist_cross_ref.artistId IN (:artistIds)
                AND (
                    creditedTracks.albumReleaseId IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM album_artist_cross_ref
                        WHERE album_artist_cross_ref.albumId = creditedTracks.albumReleaseId
                    )
                )
              UNION
              SELECT releaseTracks.trackId
              FROM album_artist_cross_ref
              INNER JOIN tracks AS releaseTracks
                  ON releaseTracks.albumReleaseId = album_artist_cross_ref.albumId
              WHERE releaseTracks.isAvailable = 1
                AND album_artist_cross_ref.artistId IN (:artistIds)
          )
        ORDER BY tracks.trackId ASC
        """
    )
    suspend fun getAvailableTracksForArtists(
        artistIds: List<Long>,
    ): List<TrackEntity>

    @Query(
        """
        SELECT tracks.*
        FROM tracks
        WHERE tracks.isAvailable = 1
          AND tracks.trackId IN (
              SELECT track_artist_cross_ref.trackId
              FROM track_artist_cross_ref
              INNER JOIN tracks AS creditedTracks
                  ON creditedTracks.trackId = track_artist_cross_ref.trackId
              WHERE creditedTracks.isAvailable = 1
                AND track_artist_cross_ref.artistId IN (:artistIds)
                AND (
                    creditedTracks.albumReleaseId IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM album_artist_cross_ref
                        WHERE album_artist_cross_ref.albumId = creditedTracks.albumReleaseId
                    )
                )
              UNION
              SELECT releaseTracks.trackId
              FROM album_artist_cross_ref
              INNER JOIN tracks AS releaseTracks
                  ON releaseTracks.albumReleaseId = album_artist_cross_ref.albumId
              WHERE releaseTracks.isAvailable = 1
                AND album_artist_cross_ref.artistId IN (:artistIds)
          )
        ORDER BY tracks.title COLLATE NOCASE ASC, tracks.trackId ASC
        """
    )
    fun observeTracksForArtists(artistIds: List<Long>): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT track_artist_cross_ref.trackId AS trackId, artists.name AS artistName
        FROM track_artist_cross_ref
        INNER JOIN artists ON artists.artistId = track_artist_cross_ref.artistId
        WHERE track_artist_cross_ref.trackId IN (:trackIds)
        ORDER BY track_artist_cross_ref.trackId ASC, track_artist_cross_ref.creditOrder ASC
        """
    )
    suspend fun getSearchArtists(trackIds: List<Long>): List<TrackSearchArtistRow>

    @Query(
        """
        SELECT track_genre_cross_ref.trackId AS trackId, genres.name AS genreName
        FROM track_genre_cross_ref
        INNER JOIN genres ON genres.genreId = track_genre_cross_ref.genreId
        WHERE track_genre_cross_ref.trackId IN (:trackIds)
        ORDER BY track_genre_cross_ref.trackId ASC, track_genre_cross_ref.genreOrder ASC
        """
    )
    suspend fun getSearchGenres(trackIds: List<Long>): List<TrackSearchGenreRow>

    @Query(
        """
        SELECT COUNT(DISTINCT tracks.trackId)
        FROM tracks
        INNER JOIN track_artist_cross_ref ON track_artist_cross_ref.trackId = tracks.trackId
        INNER JOIN artists ON artists.artistId = track_artist_cross_ref.artistId
        WHERE tracks.isAvailable = 1 AND artists.normalizedName = :normalizedArtistName
        """
    )
    suspend fun countAvailableTracksForArtist(normalizedArtistName: String): Int

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND albumReleaseId = :albumId ORDER BY CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END, discNumber ASC, CASE WHEN trackNumber IS NULL THEN 1 ELSE 0 END, trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND trackId IN (SELECT trackId FROM track_artist_cross_ref WHERE artistId = :artistId) ORDER BY title COLLATE NOCASE ASC")
    fun observeTracksForArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 AND trackId IN (SELECT trackId FROM track_genre_cross_ref WHERE genreId = :genreId) ORDER BY title COLLATE NOCASE ASC")
    fun observeTracksForGenre(genreId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT DISTINCT tracks.*
        FROM tracks
        INNER JOIN track_genre_cross_ref
            ON track_genre_cross_ref.trackId = tracks.trackId
        WHERE tracks.isAvailable = 1
          AND track_genre_cross_ref.genreId = :genreId
          AND (
              (
                  (
                      tracks.albumReleaseId IS NULL
                      OR NOT EXISTS (
                          SELECT 1
                          FROM album_artist_cross_ref
                          WHERE album_artist_cross_ref.albumId = tracks.albumReleaseId
                      )
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM track_artist_cross_ref
                      WHERE track_artist_cross_ref.trackId = tracks.trackId
                        AND track_artist_cross_ref.artistId = :artistId
                  )
              )
              OR EXISTS (
                  SELECT 1
                  FROM album_artist_cross_ref
                  WHERE album_artist_cross_ref.albumId = tracks.albumReleaseId
                    AND album_artist_cross_ref.artistId = :artistId
              )
          )
        ORDER BY tracks.title COLLATE NOCASE ASC, tracks.trackId ASC
        """
    )
    fun observeTracksForGenreAndArtist(
        genreId: Long,
        artistId: Long,
    ): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isAvailable = 1")
    fun observeAvailableTrackCount(): Flow<Long>

    @Query("UPDATE tracks SET isAvailable = 0, updatedAtEpochMillis = :updatedAtEpochMillis WHERE trackId = :trackId")
    suspend fun markUnavailable(trackId: Long, updatedAtEpochMillis: Long)

    @Query("UPDATE tracks SET isAvailable = 1, updatedAtEpochMillis = :updatedAtEpochMillis WHERE trackId = :trackId")
    suspend fun markAvailable(trackId: Long, updatedAtEpochMillis: Long)
}