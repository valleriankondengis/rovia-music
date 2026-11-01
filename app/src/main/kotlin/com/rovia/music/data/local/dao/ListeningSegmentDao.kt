package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.local.entity.ListeningSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningSegmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(segment: ListeningSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(
        segments: List<ListeningSegmentEntity>,
    ): List<Long>

    @Query(
        """
        SELECT *
        FROM listening_segments
        WHERE startedAtEpochMillis < :endEpochMillis
          AND endedAtEpochMillis > :startEpochMillis
        ORDER BY startedAtEpochMillis ASC
        """,
    )
    suspend fun getSegmentsBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): List<ListeningSegmentEntity>

    @Query(
        """
        SELECT *
        FROM listening_segments
        WHERE trackId = :trackId
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getSegmentsForTrack(
        trackId: Long,
        limit: Int,
    ): List<ListeningSegmentEntity>

    @Query(
        """
        SELECT COALESCE(
            SUM(
                (CASE
                    WHEN endedAtEpochMillis < :endEpochMillis
                    THEN endedAtEpochMillis
                    ELSE :endEpochMillis
                END)
                -
                (CASE
                    WHEN startedAtEpochMillis > :startEpochMillis
                    THEN startedAtEpochMillis
                    ELSE :startEpochMillis
                END)
            ),
            0
        )
        FROM listening_segments
        WHERE startedAtEpochMillis < :endEpochMillis
          AND endedAtEpochMillis > :startEpochMillis
        """,
    )
    suspend fun getListeningDurationBetween(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_segments")
    fun observeTotalListeningDuration(): Flow<Long>

    @Query(
        """
        SELECT *
        FROM listening_segments
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    fun observeLatest(limit: Int): Flow<List<ListeningSegmentEntity>>
}