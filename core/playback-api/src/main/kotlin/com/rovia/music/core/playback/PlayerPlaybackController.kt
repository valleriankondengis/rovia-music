package com.rovia.music.core.playback

import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the playback operations required by the Player UI.
 *
 * This contract intentionally excludes recent-play history,
 * catalog access, and persistent playback settings repositories.
 *
 * Implementations may represent regular playback or playback
 * initiated by an external content URI.
 *
 * Each implementation is responsible for ensuring that playback
 * commands affect only the player context it owns.
 */
interface PlayerPlaybackController {

    /**
     * Observable state of the player represented by this controller.
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Pauses playback in this controller's context.
     */
    fun pause()

    /**
     * Resumes playback in this controller's context.
     */
    fun resume()

    /**
     * Stops playback and clears this controller's queue.
     */
    fun stopAndClearQueue()

    /**
     * Seeks to the requested playback position.
     */
    fun seekTo(positionMs: Long)

    /**
     * Restarts the current track from the beginning.
     */
    fun restartCurrentTrack()

    /**
     * Skips to the next item in this controller's queue.
     */
    fun skipToNext()

    /**
     * Skips to the previous item in this controller's queue.
     */
    fun skipToPrevious()

    /**
     * Changes the repeat mode for this controller's context.
     *
     * Implementations must not persist external-session changes
     * to the regular player's settings.
     */
    fun setRepeatMode(
        repeatMode: RepeatMode,
    )

    /**
     * Changes shuffle for this controller's context.
     *
     * Implementations must not persist external-session changes
     * to the regular player's settings.
     */
    fun setShuffleEnabled(
        enabled: Boolean,
    )
}