package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "playback_track_events",
    indices = [
        Index(value = ["eventKey"], unique = true),
        Index(value = ["startedAtEpochMillis"]),
        Index(value = ["trackId", "startedAtEpochMillis"]),
        Index(value = ["sessionId", "playbackSequence"]),
        Index(value = ["playbackSourceType", "playbackSourceEntityKey", "startedAtEpochMillis"]),
        Index(value = ["artworkAssetKeySnapshot"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PlaybackSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
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
data class PlaybackTrackEventEntity(
    @PrimaryKey(autoGenerate = true)
    val eventId: Long = 0,
    val eventKey: String,
    val sessionId: Long? = null,
    val trackId: Long? = null,
    val playbackSourceType: String,
    val playbackSourceEntityKey: String,
    val playbackSourceTitleSnapshot: String,
    val trackTitleSnapshot: String,
    val artistCreditSnapshot: String? = null,
    val albumTitleSnapshot: String? = null,
    val artworkAssetKeySnapshot: String? = null,
    val artworkSourceUriSnapshot: String? = null,
    val trackDurationMsSnapshot: Long? = null,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val startPositionMs: Long = 0,
    val endPositionMs: Long? = null,
    val heardDurationMs: Long = 0,
    val qualifiedPlay: Boolean = false,
    val playbackSequence: Int = 0,
    val timeZoneIdAtStart: String
)
