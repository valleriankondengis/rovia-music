package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "listening_segments",
    indices = [Index(value = ["segmentKey"], unique = true), Index(value = ["startedAtEpochMillis"]), Index(value = ["trackId", "startedAtEpochMillis"]), Index(value = ["playbackEventId"]), Index(value = ["sessionId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaybackTrackEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["playbackEventId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PlaybackSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ListeningSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val segmentId: Long = 0,
    val segmentKey: String,
    val playbackEventId: Long? = null,
    val sessionId: Long? = null,
    val trackId: Long? = null,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val durationMs: Long,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val timeZoneIdAtStart: String
)
