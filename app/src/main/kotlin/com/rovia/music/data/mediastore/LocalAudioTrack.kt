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
    /** Release/recording year read from MediaStore metadata, when present. */
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
) {
    val artistNames: List<String>
        get() = ArtistCreditParser.parse(artist)
}