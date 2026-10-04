package com.rovia.music.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.rovia.music.data.database.entity.ExcludedFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedFolderDao {

    @Query(
        """
        SELECT relative_path
        FROM excluded_folders
        ORDER BY relative_path COLLATE NOCASE ASC
        """,
    )
    fun observeExcludedFolders(): Flow<List<String>>

    @Query(
        """
        SELECT relative_path
        FROM excluded_folders
        ORDER BY relative_path COLLATE NOCASE ASC
        """,
    )
    suspend fun getExcludedFolders(): List<String>

    @Query(
        """
        DELETE FROM excluded_folders
        """,
    )
    suspend fun deleteAll()

    @Insert(
        onConflict = OnConflictStrategy.REPLACE,
    )
    suspend fun insertAll(
        folders: List<ExcludedFolderEntity>,
    )

    @Transaction
    suspend fun replaceAll(
        folders: List<ExcludedFolderEntity>,
    ) {
        deleteAll()
        insertAll(folders)
    }
}