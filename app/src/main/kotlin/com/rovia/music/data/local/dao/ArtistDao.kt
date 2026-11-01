package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow


data class ArtistSearchRow(
    val artistId: Long,
    val name: String,
    val artworkSourceUri: String?,
    val trackCount: Int,
    val albumReleaseCount: Int,
)

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(artist: ArtistEntity): Long

    @Update
    suspend fun update(artist: ArtistEntity)

    @Query("SELECT * FROM artists WHERE artistId = :artistId LIMIT 1")
    suspend fun getById(artistId: Long): ArtistEntity?

    @Query("SELECT * FROM artists")
    suspend fun getAll(): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE identityKey IN (:identityKeys)")
    suspend fun getByIdentityKeys(identityKeys: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE identityKey = :identityKey LIMIT 1")
    suspend fun getByIdentityKey(identityKey: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE identityKey = :identityKey LIMIT 1")
    fun observeByIdentityKey(identityKey: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists ORDER BY normalizedName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE normalizedName LIKE '%' || :normalizedQuery || '%' ORDER BY normalizedName COLLATE NOCASE ASC LIMIT :limit")
    fun search(normalizedQuery: String, limit: Int): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT
            artists.artistId AS artistId,
            artists.name AS name,
            artists.artworkSourceUri AS artworkSourceUri,
            (
                SELECT COUNT(DISTINCT tracks.trackId)
                FROM tracks
                INNER JOIN track_artist_cross_ref
                    ON track_artist_cross_ref.trackId = tracks.trackId
                WHERE tracks.isAvailable = 1
                  AND track_artist_cross_ref.artistId = artists.artistId
            ) AS trackCount,
            (
                SELECT COUNT(DISTINCT album_artist_cross_ref.albumId)
                FROM album_artist_cross_ref
                WHERE album_artist_cross_ref.artistId = artists.artistId
            ) AS albumReleaseCount
        FROM artists
        WHERE artists.normalizedName LIKE '%' || :normalizedQuery || '%'
        ORDER BY artists.normalizedName COLLATE NOCASE ASC, artists.artistId ASC
        LIMIT :limit
        """
    )
    fun searchForDatabaseSearch(
        normalizedQuery: String,
        limit: Int,
    ): Flow<List<ArtistSearchRow>>

    @Query("SELECT artists.* FROM artists INNER JOIN track_artist_cross_ref ON artists.artistId = track_artist_cross_ref.artistId WHERE track_artist_cross_ref.trackId = :trackId ORDER BY track_artist_cross_ref.creditOrder ASC")
    suspend fun getArtistsForTrack(trackId: Long): List<ArtistEntity>
}
