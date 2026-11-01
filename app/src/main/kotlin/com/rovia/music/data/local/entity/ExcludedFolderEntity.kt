package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "excluded_folders",
    indices = [Index(value = ["normalizedPath"], unique = true)]
)
data class ExcludedFolderEntity(
    @PrimaryKey
    val path: String,
    val normalizedPath: String,
    val excludedAtEpochMillis: Long
)
