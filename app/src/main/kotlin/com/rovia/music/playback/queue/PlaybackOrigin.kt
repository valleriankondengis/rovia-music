package com.rovia.music.playback.queue

enum class PlaybackOriginType {
    TRACK,
    ARTIST,
    ALBUM,
    GENRE,
    PLAYLIST,
    LIKES,
    SINGLE,
    EP,
}

data class PlaybackOrigin(
    val type: PlaybackOriginType,
    val entityKey: String,
    val title: String,
    val subtitle: String? = null,
    val artworkSourceUri: String? = null,
)