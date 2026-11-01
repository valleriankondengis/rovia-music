package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["identityKey"], unique = true),
        Index(value = ["normalizedName"]),
        Index(value = ["artworkAssetKey"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKey"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val artistId: Long = 0,
    val name: String,
    val normalizedName: String,
    val identityKey: String,
    val artworkAssetKey: String? = null,
    val artworkSourceUri: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
