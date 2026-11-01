package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.PlaybackSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(session: PlaybackSessionEntity): Long

    @Update
    suspend fun update(session: PlaybackSessionEntity)

    @Query("SELECT * FROM playback_sessions WHERE sessionKey = :sessionKey LIMIT 1")
    suspend fun getBySessionKey(sessionKey: String): PlaybackSessionEntity?

    @Query("SELECT * FROM playback_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: Long): PlaybackSessionEntity?

    @Query("SELECT * FROM playback_sessions ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<PlaybackSessionEntity>>

    @Query("UPDATE playback_sessions SET endedAtEpochMillis = :endedAtEpochMillis, wasInterrupted = :wasInterrupted WHERE sessionId = :sessionId")
    suspend fun finishSession(sessionId: Long, endedAtEpochMillis: Long, wasInterrupted: Boolean)
}
