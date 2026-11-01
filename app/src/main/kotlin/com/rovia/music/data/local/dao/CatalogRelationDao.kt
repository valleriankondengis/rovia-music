package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.local.entity.AlbumArtistCrossRef
import com.rovia.music.data.local.entity.TrackArtistCrossRef
import com.rovia.music.data.local.entity.TrackGenreCrossRef
import kotlinx.coroutines.flow.Flow


data class ArtistTrackRelationRow(
    val artistId: Long,
    val trackId: Long,
)

@Dao
interface CatalogRelationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackArtistRelations(relations: List<TrackArtistCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumArtistRelations(relations: List<AlbumArtistCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackGenreRelations(relations: List<TrackGenreCrossRef>)

    @Query("DELETE FROM track_artist_cross_ref WHERE trackId = :trackId")
    suspend fun deleteTrackArtistRelations(trackId: Long)

    @Query("DELETE FROM album_artist_cross_ref WHERE albumId = :albumId")
    suspend fun deleteAlbumArtistRelations(albumId: Long)

    @Query("DELETE FROM track_genre_cross_ref WHERE trackId = :trackId")
    suspend fun deleteTrackGenreRelations(trackId: Long)

    @Query(
        """
        SELECT relations.*
        FROM track_artist_cross_ref AS relations
        INNER JOIN tracks ON tracks.trackId = relations.trackId
        WHERE tracks.isAvailable = 1
        ORDER BY relations.trackId ASC, relations.creditOrder ASC
        """
    )
    fun observeAllTrackArtistRelations(): Flow<List<TrackArtistCrossRef>>

    @Query(
        """
        SELECT relations.*
        FROM track_artist_cross_ref AS relations
        INNER JOIN tracks ON tracks.trackId = relations.trackId
        WHERE tracks.isAvailable = 1
        ORDER BY relations.trackId ASC, relations.creditOrder ASC
        """
    )
    suspend fun getAllAvailableTrackArtistRelations(): List<TrackArtistCrossRef>

    @Query("SELECT * FROM album_artist_cross_ref ORDER BY albumId ASC, creditOrder ASC")
    suspend fun getAllAlbumArtistRelations(): List<AlbumArtistCrossRef>

    @Query(
        """
        SELECT relations.*
        FROM track_genre_cross_ref AS relations
        INNER JOIN tracks ON tracks.trackId = relations.trackId
        WHERE tracks.isAvailable = 1
        ORDER BY relations.trackId ASC, relations.genreOrder ASC
        """
    )
    fun observeAllTrackGenreRelations(): Flow<List<TrackGenreCrossRef>>

    @Query(
        """
        SELECT artistReleaseRelations.artistId AS artistId,
               artistReleaseRelations.trackId AS trackId
        FROM (
            SELECT track_artist_cross_ref.artistId AS artistId,
                   track_artist_cross_ref.trackId AS trackId
            FROM track_artist_cross_ref
            INNER JOIN tracks
                ON tracks.trackId = track_artist_cross_ref.trackId
            WHERE tracks.isAvailable = 1
              AND (
                  tracks.albumReleaseId IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM album_artist_cross_ref
                      WHERE album_artist_cross_ref.albumId = tracks.albumReleaseId
                  )
              )
            UNION
            SELECT album_artist_cross_ref.artistId AS artistId,
                   tracks.trackId AS trackId
            FROM album_artist_cross_ref
            INNER JOIN tracks
                ON tracks.albumReleaseId = album_artist_cross_ref.albumId
            WHERE tracks.isAvailable = 1
        ) AS artistReleaseRelations
        ORDER BY artistReleaseRelations.artistId ASC,
                 artistReleaseRelations.trackId ASC
        """
    )
    fun observeAllArtistReleaseTrackRelations(): Flow<List<ArtistTrackRelationRow>>

    @Query(
        """
        SELECT artistReleaseRelations.artistId AS artistId,
               artistReleaseRelations.trackId AS trackId
        FROM (
            SELECT track_artist_cross_ref.artistId AS artistId,
                   track_artist_cross_ref.trackId AS trackId
            FROM track_artist_cross_ref
            INNER JOIN tracks
                ON tracks.trackId = track_artist_cross_ref.trackId
            WHERE tracks.isAvailable = 1
              AND track_artist_cross_ref.artistId IN (:artistIds)
              AND (
                  tracks.albumReleaseId IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM album_artist_cross_ref
                      WHERE album_artist_cross_ref.albumId = tracks.albumReleaseId
                  )
              )
            UNION
            SELECT album_artist_cross_ref.artistId AS artistId,
                   tracks.trackId AS trackId
            FROM album_artist_cross_ref
            INNER JOIN tracks
                ON tracks.albumReleaseId = album_artist_cross_ref.albumId
            WHERE tracks.isAvailable = 1
              AND album_artist_cross_ref.artistId IN (:artistIds)
        ) AS artistReleaseRelations
        ORDER BY artistReleaseRelations.artistId ASC,
                 artistReleaseRelations.trackId ASC
        """
    )
    suspend fun getAvailableTrackRelationsForArtists(
        artistIds: List<Long>,
    ): List<ArtistTrackRelationRow>

    @Query("SELECT * FROM track_artist_cross_ref WHERE trackId = :trackId ORDER BY creditOrder ASC")
    suspend fun getTrackArtistRelations(trackId: Long): List<TrackArtistCrossRef>

    @Query("SELECT * FROM album_artist_cross_ref WHERE albumId = :albumId ORDER BY creditOrder ASC")
    suspend fun getAlbumArtistRelations(albumId: Long): List<AlbumArtistCrossRef>

    @Query("SELECT * FROM track_genre_cross_ref WHERE trackId = :trackId ORDER BY genreOrder ASC")
    suspend fun getTrackGenreRelations(trackId: Long): List<TrackGenreCrossRef>
}
