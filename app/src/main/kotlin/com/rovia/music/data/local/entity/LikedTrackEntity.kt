package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "liked_tracks",
    indices = [Index(value = ["trackId"], unique = true), Index(value = ["sourceKeySnapshot"], unique = true), Index(value = ["likedAtEpochMillis"]), Index(value = ["artworkAssetKeySnapshot"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKeySnapshot"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class LikedTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val likedTrackId: Long = 0,
    val trackId: Long? = null,
    val sourceKeySnapshot: String? = null,
    val titleSnapshot: String,
    val artistCreditSnapshot: String? = null,
    val albumTitleSnapshot: String? = null,
    val artworkAssetKeySnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null,
    val likedAtEpochMillis: Long
)
