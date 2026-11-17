package com.rovia.music.playback.external

import androidx.media3.common.Player

/**
 * Coordinates pause and resume between the external player and the regular
 * player. When external playback starts, a playing regular player is paused
 * (never stopped; its queue and position stay untouched). When the external
 * session ends, the regular player resumes if it was playing before.
 *
 * Audio focus cannot express this: a permanent focus loss never receives a
 * regain callback, so the resume is driven explicitly here. Both players
 * live in the same process, which keeps this deterministic.
 */
object ExternalPlaybackCoordinator {

    private var mainPlayer: Player? = null
    private var externalSessionActive = false
    private var resumeMainAfterExternal = false

    private val mainGuardListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // The user resumed the regular player during the external
            // session; the automatic resume no longer applies.
            if (isPlaying) {
                resumeMainAfterExternal = false
            }
        }
    }

    /** Called by the regular PlaybackService whenever its player is (re)built. */
    fun registerMainPlayer(player: Player?) {
        if (mainPlayer === player) return
        if (externalSessionActive) {
            mainPlayer?.removeListener(mainGuardListener)
            if (resumeMainAfterExternal) {
                player?.addListener(mainGuardListener)
            }
        }
        mainPlayer = player
    }

    /** Called when the external player starts playing. */
    fun onExternalPlaying() {
        if (externalSessionActive) return
        externalSessionActive = true

        val player = mainPlayer ?: return
        val mainIsPlaying = player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.mediaItemCount > 0
        if (mainIsPlaying) {
            resumeMainAfterExternal = true
            player.addListener(mainGuardListener)
            player.pause()
        }
    }

    /** Called when the external session ends for any reason. Idempotent. */
    fun onExternalSessionEnded() {
        if (!externalSessionActive) return
        externalSessionActive = false
        mainPlayer?.removeListener(mainGuardListener)

        if (!resumeMainAfterExternal) return
        resumeMainAfterExternal = false

        val player = mainPlayer ?: return
        if (player.mediaItemCount > 0 && !player.isPlaying) {
            player.play()
        }
    }
}
