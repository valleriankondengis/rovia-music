package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["normalizedName"]), Index(value = ["updatedAtEpochMillis"]), Index(value = ["artworkAssetKey"])],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKey"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,
    val name: String,
    val normalizedName: String,
    val description: String? = null,
    val artworkAssetKey: String? = null,
    val artworkSourceUri: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
