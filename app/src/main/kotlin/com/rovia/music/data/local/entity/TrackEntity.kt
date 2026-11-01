package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["normalizedTitle"]),
        Index(value = ["dateAddedSeconds"]),
        Index(value = ["dateModifiedSeconds"]),
        Index(value = ["relativePath"]),
        Index(value = ["isAvailable", "dateAddedSeconds"]),
        Index(value = ["albumReleaseId"]),
        Index(value = ["artworkAssetKey"]),
        Index(value = ["volumeName", "mediaStoreId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumReleaseId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKey"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val trackId: Long = 0,
    val sourceKey: String? = null,
    val mediaStoreId: Long? = null,
    val volumeName: String? = null,
    val contentUri: String? = null,
    val displayName: String? = null,
    val title: String,
    val normalizedTitle: String,
    val artistCreditRaw: String? = null,
    val albumArtistCreditRaw: String? = null,
    val albumTitleSnapshot: String? = null,
    val albumReleaseId: Long? = null,
    val durationMs: Long = 0,
    val mediaStoreYear: Int? = null,
    val releaseDateRaw: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val releaseDay: Int? = null,
    val releaseDatePrecision: String? = null,
    val releaseDateSource: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val dateAddedSeconds: Long = 0,
    val dateModifiedSeconds: Long = 0,
    val relativePath: String? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val isCompilation: Boolean? = null,
    val artworkAssetKey: String? = null,
    val artworkSourceUri: String? = null,
    val metadataFingerprint: String? = null,
    val metadataExtractionVersion: Int = 0,
    val metadataExtractedAtEpochMillis: Long? = null,
    val lastSeenScanToken: String? = null,
    val lastSeenGeneration: Long? = null,
    val isAvailable: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
