package com.rovia.music.data.mediastore

import android.net.Uri

data class LocalAudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAddedSeconds: Long = 0L,
    val relativePath: String? = null,
    val dateModifiedSeconds: Long = 0L,
    val albumId: Long? = null,
    val albumArtist: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val metadataDateEpochMillis: Long? = null,
    val inferredDateEpochMillis: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val cdTrackNumber: String? = null,
    val compilation: String? = null,
    val releaseType: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val composer: String? = null,
    val author: String? = null,
    val writer: String? = null,
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val bitrateBps: Int? = null,
    val displayName: String? = null,
    val fileSizeBytes: Long? = null,
    val volumeName: String? = null,
) {
    val artistNames: List<String>
        get() = ArtistCreditParser.parse(artist)

    val year: Int?
        get() = releaseYear

    val genre: String?
        get() = genres.joinToString(", ").takeIf(String::isNotBlank)

    val dateAddedEpochSeconds: Long
        get() = dateAddedSeconds

    val dateModifiedEpochSeconds: Long
        get() = dateModifiedSeconds
}
