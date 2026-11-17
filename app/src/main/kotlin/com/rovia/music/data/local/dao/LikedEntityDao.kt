package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.rovia.music.data.local.entity.LikedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedEntityDao {
    @Query("SELECT * FROM liked_entities ORDER BY likedAtEpochMillis DESC, likedEntityId DESC")
    fun observeAll(): Flow<List<LikedEntity>>

    @Query("SELECT * FROM liked_entities WHERE entityType = :entityType AND entityKey = :entityKey LIMIT 1")
    suspend fun getByTypeAndKey(entityType: String, entityKey: String): LikedEntity?

    @Insert
    suspend fun insert(entity: LikedEntity): Long

    @Query("DELETE FROM liked_entities WHERE entityType = :entityType AND entityKey = :entityKey")
    suspend fun deleteByTypeAndKey(entityType: String, entityKey: String)
}
