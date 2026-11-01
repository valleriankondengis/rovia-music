package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE albumId = :albumId LIMIT 1")
    suspend fun getById(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE albumId IN (:albumIds)")
    suspend fun getByIds(albumIds: List<Long>): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE identityKey = :identityKey LIMIT 1")
    suspend fun getByIdentityKey(identityKey: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE identityKey = :identityKey LIMIT 1")
    fun observeByIdentityKey(identityKey: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE providerAlbumId = :providerAlbumId")
    fun observeByProviderAlbumId(providerAlbumId: Long): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY CASE WHEN releaseYear IS NULL THEN 1 ELSE 0 END, releaseYear DESC, CASE WHEN releaseMonth IS NULL THEN 1 ELSE 0 END, releaseMonth DESC, CASE WHEN releaseDay IS NULL THEN 1 ELSE 0 END, releaseDay DESC, normalizedTitle COLLATE NOCASE ASC")
    fun observeAllByReleaseDate(): Flow<List<AlbumEntity>>

    @Query("SELECT albums.* FROM albums INNER JOIN album_artist_cross_ref ON albums.albumId = album_artist_cross_ref.albumId WHERE album_artist_cross_ref.artistId = :artistId ORDER BY CASE WHEN albums.releaseYear IS NULL THEN 1 ELSE 0 END, albums.releaseYear DESC, albums.normalizedTitle COLLATE NOCASE ASC")
    fun observeForArtist(artistId: Long): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE normalizedTitle LIKE '%' || :normalizedQuery || '%' ORDER BY normalizedTitle COLLATE NOCASE ASC LIMIT :limit")
    fun search(normalizedQuery: String, limit: Int): Flow<List<AlbumEntity>>
}
