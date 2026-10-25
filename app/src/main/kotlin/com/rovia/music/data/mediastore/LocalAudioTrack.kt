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
    /** Relative MediaStore path, retained so folder filters can be applied to the in-memory cache. */
    val relativePath: String? = null,
    /** MediaStore modification timestamp used to detect edits when refreshing the cache. */
    val dateModifiedSeconds: Long = 0L,
)