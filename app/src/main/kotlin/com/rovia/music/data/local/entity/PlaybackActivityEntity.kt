package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playback_activities",
    indices = [
        Index(value = ["activityKey"], unique = true),
        Index(value = ["timestampEpochMillis"]),
        Index(value = ["entityType", "entityKey", "timestampEpochMillis"]),
        Index(value = ["triggerTrackId"]),
        Index(value = ["artworkAssetKeySnapshot"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["triggerTrackId"],
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
data class PlaybackActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val activityId: Long = 0,
    val activityKey: String,
    val entityType: String,
    val entityKey: String,
    val timestampEpochMillis: Long,
    val timeZoneIdAtEvent: String,
    val triggerTrackId: Long? = null,
    val titleSnapshot: String,
    val subtitleSnapshot: String? = null,
    val artworkAssetKeySnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null
)
