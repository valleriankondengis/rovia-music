
package com.rovia.music.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "music_tracks",
    primaryKeys = [
        "volume_name",
        "track_id",
    ],
    indices = [
        Index(
            value = ["title"],
        ),
        Index(
            value = ["artist"],
        ),
        Index(
            value = ["album"],
        ),
        Index(
            value = ["date_added_epoch_seconds"],
        ),
        Index(
            value = ["relative_path"],
        ),
        Index(
            value = [
                "volume_name",
                "generation_modified",
            ],
        ),
    ],
)
data class MusicTrackEntity(
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

    /*
     * The volume is part of the catalog identity and must be
     * non-null for every indexed track.
     */
    @ColumnInfo(name = "volume_name")
    val volumeName: String,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,

    /*
     * MediaStore generation observed for this track.
     */
    @ColumnInfo(name = "generation_modified")
    val generationModified: Long? = null,

    /*
     * Signature associated with the latest embedded-metadata
     * extraction attempt.
     */
    @ColumnInfo(name = "embedded_metadata_signature")
    val embeddedMetadataSignature: String? = null,

    /*
     * 0 = pending
     * 1 = complete
     * 2 = failed
     */
    @ColumnInfo(name = "embedded_metadata_status")
    val embeddedMetadataStatus: Int =
        EMBEDDED_METADATA_PENDING,

    @ColumnInfo(name = "embedded_metadata_updated_at_epoch_millis")
    val embeddedMetadataUpdatedAtEpochMillis: Long? = null,
) {
    companion object {
        const val EMBEDDED_METADATA_PENDING = 0
        const val EMBEDDED_METADATA_COMPLETE = 1
        const val EMBEDDED_METADATA_FAILED = 2
    }
}
