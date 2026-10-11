
package com.rovia.music.playback.media3

import android.app.PendingIntent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class RoviaMediaSessionService : MediaSessionService() {

    private lateinit var regularPlayer: ExoPlayer
    private lateinit var externalPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private var isExternalPlayerActive = false

    /**
     * Captures whether regular playback should resume after
     * an external playback session has ended.
     *
     * This state is kept in memory and is not persisted.
     */
    private var shouldResumeRegularPlayback = false

    override fun onCreate() {
        super.onCreate()

        regularPlayer = createPlayer()
        externalPlayer = createPlayer()

        val sessionActivityIntent =
            checkNotNull(
                packageManager.getLaunchIntentForPackage(packageName),
            ) {
                "Rovia launcher Activity could not be found."
            }

        val sessionActivityPendingIntent =
            PendingIntent.getActivity(
                this,
                SESSION_ACTIVITY_REQUEST_CODE,
                sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        mediaSession =
            MediaSession.Builder(
                this,
                regularPlayer,
            )
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(SessionCallback())
                .build()
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession = mediaSession

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val resultBuilder =
                MediaSession.ConnectionResult.AcceptedResultBuilder(
                    session,
                    controller,
                )

            /*
             * Only controllers belonging to Rovia receive the
             * internal commands that switch the active player.
             *
             * System media controllers retain their normal
             * session commands without receiving these commands.
             */
            if (controller.packageName != packageName) {
                return resultBuilder.build()
            }

            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(
                        SessionCommand(
                            COMMAND_ACTIVATE_EXTERNAL_PLAYER,
                            Bundle.EMPTY,
                        ),
                    )
                    .add(
                        SessionCommand(
                            COMMAND_RESTORE_REGULAR_PLAYER,
                            Bundle.EMPTY,
                        ),
                    )
                    .build()

            return resultBuilder
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            /*
             * The package check is repeated when executing the
             * command. Availability alone is not authorization.
             */
            if (controller.packageName != packageName) {
                return Futures.immediateFuture(
                    SessionResult(
                        SessionResult.RESULT_ERROR_NOT_SUPPORTED,
                    ),
                )
            }

            val resultCode =
                when (customCommand.customAction) {
                    COMMAND_ACTIVATE_EXTERNAL_PLAYER -> {
                        activateExternalPlayer()
                        SessionResult.RESULT_SUCCESS
                    }

                    COMMAND_RESTORE_REGULAR_PLAYER -> {
                        restoreRegularPlayer()
                        SessionResult.RESULT_SUCCESS
                    }

                    else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
                }

            return Futures.immediateFuture(
                SessionResult(resultCode),
            )
        }
    }

    /**
     * Switches the MediaSession to the external player.
     *
     * The regular player's queue and current position are
     * retained. Its playback intent is remembered before pause.
     */
    private fun activateExternalPlayer() {
        if (isExternalPlayerActive) {
            return
        }

        shouldResumeRegularPlayback =
            regularPlayer.playWhenReady

        regularPlayer.pause()

        mediaSession.setPlayer(externalPlayer)
        isExternalPlayerActive = true
    }

    /**
     * Stops the external session and restores the regular player.
     *
     * The external queue is cleared without modifying the
     * regular player's queue.
     */
    private fun restoreRegularPlayer() {
        if (!isExternalPlayerActive) {
            return
        }

        externalPlayer.pause()
        externalPlayer.stop()
        externalPlayer.clearMediaItems()

        mediaSession.setPlayer(regularPlayer)
        isExternalPlayerActive = false

        val shouldResume =
            shouldResumeRegularPlayback &&
                regularPlayer.mediaItemCount > 0

        shouldResumeRegularPlayback = false

        if (shouldResume) {
            regularPlayer.play()
        } else {
            regularPlayer.pause()
        }
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }

        if (::externalPlayer.isInitialized) {
            externalPlayer.release()
        }

        if (::regularPlayer.isInitialized) {
            regularPlayer.release()
        }

        super.onDestroy()
    }

    companion object {
        const val COMMAND_ACTIVATE_EXTERNAL_PLAYER =
            "com.rovia.music.playback.ACTIVATE_EXTERNAL_PLAYER"

        const val COMMAND_RESTORE_REGULAR_PLAYER =
            "com.rovia.music.playback.RESTORE_REGULAR_PLAYER"

        private const val SESSION_ACTIVITY_REQUEST_CODE = 0
    }
}
