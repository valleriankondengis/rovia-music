package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.local.entity.UserActionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserActionEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: UserActionEventEntity): Long

    @Query("SELECT * FROM user_action_events ORDER BY occurredAtEpochMillis DESC, userActionEventId DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<UserActionEventEntity>

    @Query("SELECT * FROM user_action_events ORDER BY occurredAtEpochMillis DESC, userActionEventId DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<UserActionEventEntity>>

    @Query("SELECT * FROM user_action_events WHERE targetType = :targetType AND targetKey = :targetKey ORDER BY occurredAtEpochMillis DESC")
    fun observeForTarget(targetType: String, targetKey: String): Flow<List<UserActionEventEntity>>

    @Query(
        """
        SELECT * FROM user_action_events
        WHERE actionType = 'SEARCH_QUERY'
        ORDER BY occurredAtEpochMillis DESC, userActionEventId DESC
        LIMIT :limit
        """
    )
    fun observeSearchHistory(limit: Int): Flow<List<UserActionEventEntity>>

    @Query(
        """
        UPDATE user_action_events
        SET occurredAtEpochMillis = :occurredAtEpochMillis,
            timeZoneIdAtEvent = :timeZoneIdAtEvent,
            targetType = 'SEARCH_QUERY',
            targetKey = :normalizedQuery,
            titleSnapshot = :query,
            subtitleSnapshot = NULL,
            artworkAssetKeySnapshot = NULL,
            artworkSourceUriSnapshot = NULL
        WHERE actionKey = :actionKey AND actionType = 'SEARCH_QUERY'
        """
    )
    suspend fun updateSearchQuery(
        actionKey: String,
        normalizedQuery: String,
        query: String,
        occurredAtEpochMillis: Long,
        timeZoneIdAtEvent: String,
    ): Int

    @Query(
        """
        DELETE FROM user_action_events
        WHERE actionType = 'SEARCH_QUERY' AND actionKey = :actionKey
        """
    )
    suspend fun deleteSearchQuery(actionKey: String): Int

    @Query("DELETE FROM user_action_events WHERE actionType = 'SEARCH_QUERY'")
    suspend fun clearSearchHistory(): Int

    @Query(
        """
        DELETE FROM user_action_events
        WHERE actionType = 'SEARCH_QUERY'
            AND userActionEventId NOT IN (
                SELECT userActionEventId FROM user_action_events
                WHERE actionType = 'SEARCH_QUERY'
                ORDER BY occurredAtEpochMillis DESC, userActionEventId DESC
                LIMIT :keepLimit
            )
        """
    )
    suspend fun trimSearchHistory(keepLimit: Int): Int
}