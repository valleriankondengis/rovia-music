package com.rovia.music.playback.media3

import android.os.Bundle
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

        mediaSession =
            MediaSession.Builder(this, player)
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
}