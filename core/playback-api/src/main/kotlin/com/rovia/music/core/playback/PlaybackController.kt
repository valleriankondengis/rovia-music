package com.rovia.music.core.playback

import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {

    val playbackState: StateFlow<PlaybackState>

    val recentPlays: StateFlow<List<Track>>

    fun play(track: Track)

    fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
    )

    fun pause()

    fun resume()

    fun stopAndClearQueue()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    fun setRepeatMode(
        repeatMode: RepeatMode,
    )

    fun setShuffleEnabled(
        enabled: Boolean,
    )
}