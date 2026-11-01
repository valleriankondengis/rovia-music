package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playback_sessions",
    indices = [
        Index(value = ["sessionKey"], unique = true),
        Index(value = ["startedAtEpochMillis"]),
        Index(value = ["originType", "originEntityKey", "startedAtEpochMillis"]),
        Index(value = ["primaryTrackId"]),
        Index(value = ["originArtworkAssetKeySnapshot"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["primaryTrackId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["originArtworkAssetKeySnapshot"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class PlaybackSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val sessionKey: String,
    val originType: String,
    val originEntityKey: String,
    val originTitleSnapshot: String,
    val originSubtitleSnapshot: String? = null,
    val originArtworkAssetKeySnapshot: String? = null,
    val originArtworkSourceUriSnapshot: String? = null,
    val primaryTrackId: Long? = null,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val timeZoneIdAtStart: String,
    val wasInterrupted: Boolean = false
)