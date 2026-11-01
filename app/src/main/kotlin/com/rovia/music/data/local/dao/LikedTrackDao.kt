package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.LikedTrackEntity
import kotlinx.coroutines.flow.Flow

data class LikedTrackSearchRow(
    val likedTrackId: Long,
    val trackId: Long?,
    val availableTrackId: Long?,
    val sourceKeySnapshot: String?,
    val titleSnapshot: String,
    val artistCreditSnapshot: String?,
    val albumTitleSnapshot: String?,
    val artworkSourceUriSnapshot: String?,
    val likedAtEpochMillis: Long,
)

@Dao
interface LikedTrackDao {
    @Insert
    suspend fun insertLike(likedTrack: LikedTrackEntity): Long

    @Update
    suspend fun updateLike(likedTrack: LikedTrackEntity)

    @Query("SELECT * FROM liked_tracks ORDER BY likedAtEpochMillis DESC, likedTrackId DESC")
    fun observeLikedTracks(): Flow<List<LikedTrackEntity>>

    @Query("SELECT * FROM liked_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getLikeForTrack(trackId: Long): LikedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId)")
    fun observeIsLiked(trackId: Long): Flow<Boolean>

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun removeLikeForTrack(trackId: Long)

    @Query("SELECT COUNT(*) FROM liked_tracks")
    fun observeLikedTrackCount(): Flow<Long>

    @Query(
        """
        SELECT
            liked_tracks.likedTrackId AS likedTrackId,
            liked_tracks.trackId AS trackId,
            CASE
                WHEN tracks.isAvailable = 1 THEN tracks.trackId
                ELSE NULL
            END AS availableTrackId,
            liked_tracks.sourceKeySnapshot AS sourceKeySnapshot,
            liked_tracks.titleSnapshot AS titleSnapshot,
            liked_tracks.artistCreditSnapshot AS artistCreditSnapshot,
            liked_tracks.albumTitleSnapshot AS albumTitleSnapshot,
            liked_tracks.artworkSourceUriSnapshot AS artworkSourceUriSnapshot,
            liked_tracks.likedAtEpochMillis AS likedAtEpochMillis
        FROM liked_tracks
        LEFT JOIN tracks ON tracks.trackId = liked_tracks.trackId
        WHERE
            COALESCE(tracks.normalizedTitle, '') LIKE '%' || :normalizedQuery || '%'
            OR LOWER(liked_tracks.titleSnapshot) LIKE '%' || :plainQuery || '%'
            OR LOWER(COALESCE(liked_tracks.artistCreditSnapshot, tracks.artistCreditRaw, ''))
                LIKE '%' || :plainQuery || '%'
            OR LOWER(COALESCE(liked_tracks.albumTitleSnapshot, tracks.albumTitleSnapshot, ''))
                LIKE '%' || :plainQuery || '%'
        ORDER BY
            CASE
                WHEN COALESCE(tracks.normalizedTitle, '') = :normalizedQuery THEN 0
                WHEN COALESCE(tracks.normalizedTitle, '') LIKE :normalizedQuery || '%' THEN 1
                WHEN LOWER(liked_tracks.titleSnapshot) = :plainQuery THEN 0
                WHEN LOWER(liked_tracks.titleSnapshot) LIKE :plainQuery || '%' THEN 1
                ELSE 2
            END ASC,
            liked_tracks.likedAtEpochMillis DESC,
            liked_tracks.likedTrackId DESC
        LIMIT :limit
        """
    )
    fun searchLikedTracks(
        normalizedQuery: String,
        plainQuery: String,
        limit: Int,
    ): Flow<List<LikedTrackSearchRow>>
}