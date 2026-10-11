package com.rovia.music.core.playback

import com.rovia.music.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls regular in-app music playback.
 *
 * This contract extends the shared Player UI playback operations
 * while retaining functionality specific to regular playback,
 * including track selection, queue creation, and recent-play
 * history observation.
 *
 * External playback must use ExternalPlaybackController instead
 * of invoking regular queue or history operations.
 */
interface PlaybackController : PlayerPlaybackController {

    /**
     * Observable history of tracks played through the regular
     * playback context.
     */
    val recentPlays: StateFlow<List<Track>>

    /**
     * Starts regular playback of a single track.
     */
    fun play(
        track: Track,
    )

    /**
     * Replaces the regular playback queue and starts at the
     * specified track index.
     */
    fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
    )
}