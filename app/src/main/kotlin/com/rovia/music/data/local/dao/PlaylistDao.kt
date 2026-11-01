package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.PlaylistEntity
import com.rovia.music.data.local.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow


data class PlaylistSearchRow(
    val playlistId: Long,
    val name: String,
    val description: String?,
    val artworkSourceUri: String?,
    val trackCount: Long,
    val matchPriority: Int,
    val matchingTrackTitle: String?,
    val matchingTrackArtist: String?,
)

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMillis DESC, playlistId DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE normalizedName LIKE '%' || :normalizedQuery || '%' ORDER BY normalizedName COLLATE NOCASE ASC LIMIT :limit")
    fun searchPlaylists(normalizedQuery: String, limit: Int): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT
            playlists.playlistId AS playlistId,
            playlists.name AS name,
            playlists.description AS description,
            COALESCE(
                playlists.artworkSourceUri,
                (
                    SELECT playlist_artwork.artworkSourceUriSnapshot
                    FROM playlist_tracks AS playlist_artwork
                    WHERE playlist_artwork.playlistId = playlists.playlistId
                      AND playlist_artwork.artworkSourceUriSnapshot IS NOT NULL
                      AND playlist_artwork.artworkSourceUriSnapshot != ''
                    ORDER BY playlist_artwork.sortOrder ASC, playlist_artwork.playlistTrackId ASC
                    LIMIT 1
                )
            ) AS artworkSourceUri,
            (
                SELECT COUNT(*)
                FROM playlist_tracks AS playlist_count
                WHERE playlist_count.playlistId = playlists.playlistId
            ) AS trackCount,
            CASE
                WHEN playlists.normalizedName = :normalizedQuery THEN 0
                WHEN playlists.normalizedName LIKE :normalizedQuery || '%' THEN 1
                WHEN playlists.normalizedName LIKE '%' || :normalizedQuery || '%' THEN 2
                ELSE 3
            END AS matchPriority,
            (
                SELECT COALESCE(tracks.title, playlist_match.trackTitleSnapshot)
                FROM playlist_tracks AS playlist_match
                LEFT JOIN tracks ON tracks.trackId = playlist_match.trackId
                LEFT JOIN albums ON albums.albumId = tracks.albumReleaseId
                WHERE playlist_match.playlistId = playlists.playlistId
                  AND (
                    COALESCE(tracks.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR LOWER(playlist_match.trackTitleSnapshot) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_match.artistCreditSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_match.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.artistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumArtistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR COALESCE(albums.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM track_artist_cross_ref
                        INNER JOIN artists
                            ON artists.artistId = track_artist_cross_ref.artistId
                        WHERE track_artist_cross_ref.trackId = tracks.trackId
                          AND artists.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM track_genre_cross_ref
                        INNER JOIN genres
                            ON genres.genreId = track_genre_cross_ref.genreId
                        WHERE track_genre_cross_ref.trackId = tracks.trackId
                          AND genres.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                  )
                ORDER BY
                    CASE
                        WHEN COALESCE(tracks.normalizedTitle, '') = :normalizedQuery THEN 0
                        WHEN COALESCE(tracks.normalizedTitle, '') LIKE :normalizedQuery || '%' THEN 1
                        ELSE 2
                    END ASC,
                    playlist_match.sortOrder ASC,
                    playlist_match.playlistTrackId ASC
                LIMIT 1
            ) AS matchingTrackTitle,
            (
                SELECT COALESCE(
                    tracks.artistCreditRaw,
                    tracks.albumArtistCreditRaw,
                    playlist_artist.artistCreditSnapshot
                )
                FROM playlist_tracks AS playlist_artist
                LEFT JOIN tracks ON tracks.trackId = playlist_artist.trackId
                LEFT JOIN albums ON albums.albumId = tracks.albumReleaseId
                WHERE playlist_artist.playlistId = playlists.playlistId
                  AND (
                    COALESCE(tracks.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR LOWER(playlist_artist.trackTitleSnapshot) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_artist.artistCreditSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_artist.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.artistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumArtistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR COALESCE(albums.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM track_artist_cross_ref
                        INNER JOIN artists
                            ON artists.artistId = track_artist_cross_ref.artistId
                        WHERE track_artist_cross_ref.trackId = tracks.trackId
                          AND artists.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM track_genre_cross_ref
                        INNER JOIN genres
                            ON genres.genreId = track_genre_cross_ref.genreId
                        WHERE track_genre_cross_ref.trackId = tracks.trackId
                          AND genres.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                  )
                ORDER BY
                    CASE
                        WHEN COALESCE(tracks.normalizedTitle, '') = :normalizedQuery THEN 0
                        WHEN COALESCE(tracks.normalizedTitle, '') LIKE :normalizedQuery || '%' THEN 1
                        ELSE 2
                    END ASC,
                    playlist_artist.sortOrder ASC,
                    playlist_artist.playlistTrackId ASC
                LIMIT 1
            ) AS matchingTrackArtist
        FROM playlists
        WHERE
            playlists.normalizedName LIKE '%' || :normalizedQuery || '%'
            OR EXISTS (
                SELECT 1
                FROM playlist_tracks
                LEFT JOIN tracks
                    ON tracks.trackId = playlist_tracks.trackId
                LEFT JOIN albums
                    ON albums.albumId = tracks.albumReleaseId
                WHERE playlist_tracks.playlistId = playlists.playlistId
                  AND (
                    COALESCE(tracks.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR LOWER(playlist_tracks.trackTitleSnapshot) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_tracks.artistCreditSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(playlist_tracks.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.artistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumArtistCreditRaw, '')) LIKE '%' || :plainQuery || '%'
                    OR LOWER(COALESCE(tracks.albumTitleSnapshot, '')) LIKE '%' || :plainQuery || '%'
                    OR COALESCE(albums.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM track_artist_cross_ref
                        INNER JOIN artists
                            ON artists.artistId = track_artist_cross_ref.artistId
                        WHERE track_artist_cross_ref.trackId = tracks.trackId
                          AND artists.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM track_genre_cross_ref
                        INNER JOIN genres
                            ON genres.genreId = track_genre_cross_ref.genreId
                        WHERE track_genre_cross_ref.trackId = tracks.trackId
                          AND genres.normalizedName LIKE '%' || :normalizedQuery || '%'
                    )
                  )
            )
        ORDER BY
            matchPriority ASC,
            playlists.updatedAtEpochMillis DESC,
            playlists.normalizedName COLLATE NOCASE ASC,
            playlists.playlistId DESC
        LIMIT :limit
        """
    )
    fun searchPlaylistsForSearch(
        normalizedQuery: String,
        plainQuery: String,
        limit: Int,
    ): Flow<List<PlaylistSearchRow>>

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert
    suspend fun insertPlaylistTrack(entry: PlaylistTrackEntity): Long

    @Insert
    suspend fun insertPlaylistTracks(entries: List<PlaylistTrackEntity>): List<Long>

    @Update
    suspend fun updatePlaylistTrack(entry: PlaylistTrackEntity)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC, playlistTrackId ASC")
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC, playlistTrackId ASC")
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrackEntity>

    @Query("DELETE FROM playlist_tracks WHERE playlistTrackId = :playlistTrackId")
    suspend fun removePlaylistTrack(playlistTrackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observePlaylistTrackCount(playlistId: Long): Flow<Long>
}