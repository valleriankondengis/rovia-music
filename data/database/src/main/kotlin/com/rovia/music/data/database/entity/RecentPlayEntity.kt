package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(
    tableName = "recent_plays",
)
data class RecentPlayEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String?,

    @ColumnInfo(name = "album")
    val album: String?,

    @ColumnInfo(name = "genre")
    val genre: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "date_added_epoch_seconds")
    val dateAddedEpochSeconds: Long,

    @ColumnInfo(name = "mime_type")
    val mimeType: String?,

    @ColumnInfo(name = "sample_rate_hz")
    val sampleRateHz: Int?,

    @ColumnInfo(name = "bitrate_bps")
    val bitrateBps: Int?,

    @ColumnInfo(name = "relative_path")
    val relativePath: String?,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,

    @ColumnInfo(name = "last_played_at_epoch_millis")
    val lastPlayedAtEpochMillis: Long,
)