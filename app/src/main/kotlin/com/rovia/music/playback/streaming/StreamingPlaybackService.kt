package com.rovia.music.playback.streaming

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import com.rovia.music.R

/**
 * The player behind the streaming page: a separate [MediaSessionService]
 * with its own ExoPlayer on the platform decoder path. It plays network
 * streams only and never touches the library, the playback statistics, or
 * any other database. Its only contact with the regular player is the
 * pause/resume handshake in [StreamingPlaybackCoordinator].
 */
@OptIn(UnstableApi::class)
class StreamingPlaybackService : MediaSessionService() {

    private companion object {
        const val STREAMING_SESSION_ID = "rovia-streaming"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                StreamingPlaybackCoordinator.onStreamingPlaying()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = player ?: return
            if (playbackState == Player.STATE_ENDED ||
                (playbackState == Player.STATE_IDLE && current.mediaItemCount == 0)
            ) {
                StreamingPlaybackCoordinator.onStreamingSessionEnded()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_launcher_foreground)
            },
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.addListener(playerListener)
        player = exoPlayer

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId(STREAMING_SESSION_ID)
            .build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        StreamingPlaybackCoordinator.onStreamingSessionEnded()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
