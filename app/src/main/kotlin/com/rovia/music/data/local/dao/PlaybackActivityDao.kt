package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.local.entity.PlaybackActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(activity: PlaybackActivityEntity): Long

    @Query("SELECT * FROM playback_activities WHERE activityKey = :activityKey LIMIT 1")
    suspend fun getByActivityKey(activityKey: String): PlaybackActivityEntity?

    @Query("SELECT * FROM playback_activities ORDER BY timestampEpochMillis DESC, activityId DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<PlaybackActivityEntity>>

    @Query("SELECT * FROM playback_activities ORDER BY timestampEpochMillis DESC, activityId DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<PlaybackActivityEntity>

    @Query("SELECT * FROM playback_activities WHERE timestampEpochMillis < :beforeTimestampEpochMillis OR (timestampEpochMillis = :beforeTimestampEpochMillis AND activityId < :beforeActivityId) ORDER BY timestampEpochMillis DESC, activityId DESC LIMIT :limit")
    suspend fun getPageBefore(
        beforeTimestampEpochMillis: Long,
        beforeActivityId: Long,
        limit: Int,
    ): List<PlaybackActivityEntity>

    @Query("SELECT * FROM playback_activities WHERE entityType = :entityType AND entityKey = :entityKey ORDER BY timestampEpochMillis DESC, activityId DESC LIMIT :limit")
    suspend fun getForEntity(entityType: String, entityKey: String, limit: Int): List<PlaybackActivityEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM playback_activities
        WHERE entityType = 'TRACK'
          AND timeZoneIdAtEvent = :timeZoneIdAtEvent
          AND timestampEpochMillis >= :startEpochMillis
          AND timestampEpochMillis < :endEpochMillis
          AND (
              (:trackId IS NOT NULL AND triggerTrackId = :trackId)
              OR entityKey = :entityKey
          )
        """
    )
    suspend fun countTrackPlaysForDay(
        trackId: Long?,
        entityKey: String,
        timeZoneIdAtEvent: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ): Long

    @Query("SELECT COUNT(*) FROM playback_activities")
    fun observeCount(): Flow<Long>
}
