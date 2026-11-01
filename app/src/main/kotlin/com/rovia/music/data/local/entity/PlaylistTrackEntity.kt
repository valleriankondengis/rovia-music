package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playlist_tracks",
    indices = [
        Index(value = ["playlistId", "sortOrder"]),
        Index(value = ["trackId"]),
        Index(value = ["artworkAssetKeySnapshot"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
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
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistTrackId: Long = 0,
    val playlistId: Long,
    val trackId: Long? = null,
    val sortOrder: Long,
    val addedAtEpochMillis: Long,
    val trackTitleSnapshot: String,
    val artistCreditSnapshot: String? = null,
    val albumTitleSnapshot: String? = null,
    val artworkAssetKeySnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null
)
