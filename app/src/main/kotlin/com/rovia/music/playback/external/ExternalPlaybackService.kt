package com.rovia.music.playback.external

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.DefaultMediaNotificationProvider
import com.rovia.music.ExternalPlayerActivity
import com.rovia.music.R
import com.rovia.music.playback.SeekForwardingPlayer
import com.rovia.music.playback.service.RoviaRenderersFactory
import com.rovia.music.playback.standard.StandardPlaybackCoordinator
import com.rovia.music.playback.standard.StandardRenderer

/**
 * The player behind the external (Open With) entry point: a separate
 * [MediaSessionService] with its own ExoPlayer on the standard system path.
 * It never touches the library, the playback statistics, or any other
 * database, ignores the folder filter, and is not eligible for playback
 * resumption. Its only contact with the regular player is the pause/resume
 * handshake in [ExternalPlaybackCoordinator].
 */
@OptIn(UnstableApi::class)
class ExternalPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var standardCoordinator: StandardPlaybackCoordinator? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                ExternalPlaybackCoordinator.onExternalPlaying()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                ExternalPlaybackCoordinator.onExternalSessionEnded()
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
        val coordinator = StandardPlaybackCoordinator(this)
        standardCoordinator = coordinator
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                RoviaRenderersFactory(this, StandardRenderer(coordinator)),
            )
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setStuckBufferingDetectionTimeoutMs(Int.MAX_VALUE)
            .setStuckPlayingDetectionTimeoutMs(Int.MAX_VALUE)
            .setStuckPlayingNotEndingTimeoutMs(Int.MAX_VALUE)
            .setStuckSuppressedDetectionTimeoutMs(Int.MAX_VALUE)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(playerListener)

        mediaSession = MediaSession.Builder(
            this,
            SeekForwardingPlayer(player) { mediaId, positionUs ->
                standardCoordinator?.onConfirmedSeek(mediaId, positionUs)
            },
        ).build()
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Only the external player's own task ends this service; dismissing
        // the main app's task leaves background playback alone.
        val fromExternalPlayer = rootIntent?.component?.className ==
            ExternalPlayerActivity::class.java.name
        if (fromExternalPlayer) {
            pauseAllPlayersAndStopSelf()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        ExternalPlaybackCoordinator.onExternalSessionEnded()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        standardCoordinator?.close()
        standardCoordinator = null
        super.onDestroy()
    }
}
