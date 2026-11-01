package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "genres",
    indices = [Index(value = ["identityKey"], unique = true), Index(value = ["normalizedName"])]
)
data class GenreEntity(
    @PrimaryKey(autoGenerate = true)
    val genreId: Long = 0,
    val name: String,
    val normalizedName: String,
    val identityKey: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
