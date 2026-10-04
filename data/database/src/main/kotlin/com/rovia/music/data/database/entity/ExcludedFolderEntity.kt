package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(
    tableName = "excluded_folders",
)
data class ExcludedFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
)