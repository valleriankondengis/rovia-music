package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "artwork_assets",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["lastAccessedAtEpochMillis"]),
        Index(value = ["isPinned"])
    ]
)
data class ArtworkAssetEntity(
    @PrimaryKey
    val assetKey: String,
    val sourceUri: String? = null,
    val relativeFilePath: String? = null,
    val mimeType: String? = null,
    val contentHash: String? = null,
    val byteSize: Long? = null,
    val createdAtEpochMillis: Long,
    val lastAccessedAtEpochMillis: Long,
    val isPinned: Boolean = false,
    val isAvailable: Boolean = true
)
