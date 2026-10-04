package com.rovia.music.core.model

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String? = null,
    val durationMs: Long,
    val dateAddedEpochSeconds: Long,
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
    val relativePath: String? = null,
    val artworkUri: String?,
)