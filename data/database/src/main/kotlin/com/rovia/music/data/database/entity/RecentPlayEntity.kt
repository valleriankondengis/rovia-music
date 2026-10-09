
package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "recent_plays",
    indices = [
        Index(
            value = ["track_id", "volume_name"],
        ),
        Index(
            value = ["last_played_at_epoch_millis"],
        ),
    ],
)
data class RecentPlayEntity(
    /*
     * MediaStore content URI uniquely identifies the source item
     * more reliably than a track ID that may overlap across volumes.
     */
    @PrimaryKey
    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String?,

    @ColumnInfo(name = "album")
    val album: String?,

    @ColumnInfo(name = "genre")
    val genre: String?,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "album_artist")
    val albumArtist: String?,

    @ColumnInfo(name = "composer")
    val composer: String?,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "writer")
    val writer: String?,

    @ColumnInfo(name = "year")
    val year: Int?,

    @ColumnInfo(name = "release_date")
    val releaseDate: String?,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int?,

    @ColumnInfo(name = "disc_number")
    val discNumber: String?,

    @ColumnInfo(name = "cd_track_number")
    val cdTrackNumber: String?,

    @ColumnInfo(name = "compilation")
    val compilation: String?,

    @ColumnInfo(name = "label")
    val label: String?,

    @ColumnInfo(name = "copyright")
    val copyright: String?,

    @ColumnInfo(name = "release_type")
    val releaseType: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "date_added_epoch_seconds")
    val dateAddedEpochSeconds: Long,

    @ColumnInfo(name = "date_modified_epoch_seconds")
    val dateModifiedEpochSeconds: Long?,

    @ColumnInfo(name = "metadata_date_epoch_millis")
    val metadataDateEpochMillis: Long?,

    @ColumnInfo(name = "inferred_date_epoch_millis")
    val inferredDateEpochMillis: Long?,

    @ColumnInfo(name = "mime_type")
    val mimeType: String?,

    @ColumnInfo(name = "sample_rate_hz")
    val sampleRateHz: Int?,

    @ColumnInfo(name = "bits_per_sample")
    val bitsPerSample: Int?,

    @ColumnInfo(name = "bitrate_bps")
    val bitrateBps: Int?,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long?,

    @ColumnInfo(name = "relative_path")
    val relativePath: String?,

    @ColumnInfo(name = "volume_name")
    val volumeName: String?,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,

    @ColumnInfo(name = "last_played_at_epoch_millis")
    val lastPlayedAtEpochMillis: Long,
)
