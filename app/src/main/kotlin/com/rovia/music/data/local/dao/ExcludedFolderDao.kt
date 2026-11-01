package com.rovia.music.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.rovia.music.data.local.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedFolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE normalizedPath = :normalizedPath")
    suspend fun remove(normalizedPath: String)

    @Query("SELECT * FROM excluded_folders ORDER BY normalizedPath COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ExcludedFolderEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_folders WHERE normalizedPath = :normalizedPath)")
    suspend fun isExcluded(normalizedPath: String): Boolean
}
