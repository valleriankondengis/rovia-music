package com.rovia.music.core.playback

import com.rovia.music.core.model.Track

/**
 * Controls playback initiated by an external application.
 *
 * This contract is intended for files opened through Android
 * content URIs, such as files selected using a file manager.
 *
 * Implementations must isolate external playback from regular
 * playback, including its queue, persisted settings, and
 * recent-play history.
 *
 * External playback must not depend on the regular music catalog
 * or Folder Filter selections.
 */
interface ExternalPlaybackController : PlayerPlaybackController {

    /**
     * Starts an isolated external playback session for one track.
     *
     * The supplied track must be constructed from the externally
     * provided URI and its available metadata.
     *
     * Implementations must not insert the track into the regular
     * queue, music catalog, or Recent Plays database.
     */
    fun startExternalPlayback(
        track: Track,
    )

    /**
     * Ends the external playback session.
     *
     * Implementations must stop and clear the external player's
     * queue, release temporary resources when safe, and restore
     * the regular playback context.
     *
     * If regular playback was active before the external session,
     * its previous playback state must be restored. If it was
     * paused, it must remain paused.
     *
     * This operation must not clear the regular player's queue.
     */
    fun endExternalPlayback()
}