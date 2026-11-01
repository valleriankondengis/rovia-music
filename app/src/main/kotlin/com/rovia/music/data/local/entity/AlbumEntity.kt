package com.rovia.music.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["identityKey"], unique = true),
        Index(value = ["normalizedTitle"]),
        Index(value = ["providerVolumeName", "providerAlbumId"]),
        Index(value = ["releaseYear"]),
        Index(value = ["releaseType"]),
        Index(value = ["artworkAssetKey"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkAssetEntity::class,
            parentColumns = ["assetKey"],
            childColumns = ["artworkAssetKey"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val albumId: Long = 0,
    val identityKey: String,
    val title: String,
    val normalizedTitle: String,
    val albumArtistCreditRaw: String? = null,
    val providerAlbumId: Long? = null,
    val providerVolumeName: String? = null,
    val releaseDateRaw: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val releaseDay: Int? = null,
    val releaseDatePrecision: String? = null,
    val releaseDateSource: String? = null,
    val firstMediaStoreReleaseYear: Int? = null,
    val lastMediaStoreReleaseYear: Int? = null,
    val releaseType: String = "UNKNOWN",
    val releaseTypeSource: String = "UNKNOWN",
    val artworkAssetKey: String? = null,
    val artworkSourceUri: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
