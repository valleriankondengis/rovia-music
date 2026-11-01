package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.rovia.music.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(genre: GenreEntity): Long

    @Update
    suspend fun update(genre: GenreEntity)

    @Query("SELECT * FROM genres WHERE genreId = :genreId LIMIT 1")
    suspend fun getById(genreId: Long): GenreEntity?

    @Query("SELECT * FROM genres WHERE identityKey = :identityKey LIMIT 1")
    suspend fun getByIdentityKey(identityKey: String): GenreEntity?

    @Query("SELECT * FROM genres WHERE identityKey = :identityKey LIMIT 1")
    fun observeByIdentityKey(identityKey: String): Flow<GenreEntity?>

    @Query("SELECT * FROM genres ORDER BY normalizedName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE normalizedName LIKE '%' || :normalizedQuery || '%' ORDER BY normalizedName COLLATE NOCASE ASC LIMIT :limit")
    fun search(normalizedQuery: String, limit: Int): Flow<List<GenreEntity>>
}
