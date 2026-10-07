package com.rovia.music.core.model

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String? = null,

    val displayName: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val author: String? = null,
    val writer: String? = null,

    val year: Int? = null,
    val releaseDate: String? = null,
    val trackNumber: Int? = null,
    val discNumber: String? = null,
    val cdTrackNumber: String? = null,
    val compilation: String? = null,

    val label: String? = null,
    val copyright: String? = null,
    val releaseType: String? = null,

    val durationMs: Long,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long? = null,
    val metadataDateEpochMillis: Long? = null,
    val inferredDateEpochMillis: Long? = null,

    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val bitrateBps: Int? = null,
    val fileSizeBytes: Long? = null,

    val relativePath: String? = null,
    val volumeName: String? = null,

    val artworkUri: String?,
)