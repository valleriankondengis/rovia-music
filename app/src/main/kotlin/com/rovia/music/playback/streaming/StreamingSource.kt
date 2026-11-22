package com.rovia.music.playback.streaming

import android.net.Uri
import com.rovia.music.data.mediastore.LocalAudioTrack

/** Problems a pasted stream address can have before playback is attempted. */
enum class StreamUrlProblem {
    EMPTY,
    INVALID,
    UNSUPPORTED_SCHEME,
}

/** The stream address schemes the streaming player accepts. */
private val SUPPORTED_STREAM_SCHEMES = setOf("http", "https")

private const val STREAMING_TRACK_ID = -900_001L

fun streamUrlProblem(raw: String): StreamUrlProblem? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return StreamUrlProblem.EMPTY
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        ?: return StreamUrlProblem.INVALID
    val scheme = uri.scheme?.lowercase() ?: return StreamUrlProblem.INVALID
    if (scheme !in SUPPORTED_STREAM_SCHEMES) return StreamUrlProblem.UNSUPPORTED_SCHEME
    if (uri.host.isNullOrBlank()) return StreamUrlProblem.INVALID
    return null
}

/** The label a stream goes by: its authority (host and port), or the raw address. */
fun streamLabel(rawUrl: String): String {
    val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull()
    return uri?.authority?.takeIf { it.isNotBlank() } ?: rawUrl.trim()
}

/**
 * A stream as a playable queue entry. The track is fabricated for the
 * streaming player only; it never enters the library or any database.
 */
fun streamTrack(rawUrl: String): LocalAudioTrack {
    val trimmed = rawUrl.trim()
    return LocalAudioTrack(
        id = STREAMING_TRACK_ID,
        uri = Uri.parse(trimmed),
        title = streamLabel(trimmed),
        artist = trimmed,
        album = null,
        durationMs = 0L,
    )
}
