package com.rovia.music.playback.streaming

import androidx.media3.common.Player

/**
 * Coordinates pause and resume between the streaming player and the regular
 * player. When streaming playback starts, a playing regular player is paused
 * (never stopped; its queue and position stay untouched). When the streaming
 * session ends, the regular player resumes if it was playing before.
 *
 * Audio focus cannot express this: a permanent focus loss never receives a
 * regain callback, so the resume is driven explicitly here. Both players
 * live in the same process, which keeps this deterministic.
 */
object StreamingPlaybackCoordinator {

    private var mainPlayer: Player? = null
    private var streamingSessionActive = false
    private var resumeMainAfterStreaming = false

    private val mainGuardListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // The user resumed the regular player during the streaming
            // session; the automatic resume no longer applies.
            if (isPlaying) {
                resumeMainAfterStreaming = false
            }
        }
    }

    /** Called by the regular PlaybackService whenever its player is (re)built. */
    fun registerMainPlayer(player: Player?) {
        if (mainPlayer === player) return
        if (streamingSessionActive) {
            mainPlayer?.removeListener(mainGuardListener)
            if (resumeMainAfterStreaming) {
                player?.addListener(mainGuardListener)
            }
        }
        mainPlayer = player
    }

    /** Called when the streaming player starts playing. */
    fun onStreamingPlaying() {
        if (streamingSessionActive) return
        streamingSessionActive = true

        val player = mainPlayer ?: return
        val mainIsPlaying = player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.mediaItemCount > 0
        if (mainIsPlaying) {
            resumeMainAfterStreaming = true
            player.addListener(mainGuardListener)
            player.pause()
        }
    }

    /** Called when the streaming session ends for any reason. Idempotent. */
    fun onStreamingSessionEnded() {
        if (!streamingSessionActive) return
        streamingSessionActive = false
        mainPlayer?.removeListener(mainGuardListener)

        if (!resumeMainAfterStreaming) return
        resumeMainAfterStreaming = false

        val player = mainPlayer ?: return
        if (player.mediaItemCount > 0 && !player.isPlaying) {
            player.play()
        }
    }
}
