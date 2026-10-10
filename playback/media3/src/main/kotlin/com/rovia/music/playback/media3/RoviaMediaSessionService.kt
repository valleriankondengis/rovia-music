
package com.rovia.music.playback.media3

import android.app.PendingIntent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class RoviaMediaSessionService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()

        player =
            ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true)
                .build()

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
            MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ACTIVITY_REQUEST_CODE = 0
    }
}
