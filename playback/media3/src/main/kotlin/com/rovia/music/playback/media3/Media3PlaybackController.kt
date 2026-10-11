
package com.rovia.music.playback.media3

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.PlaybackContext
import com.rovia.music.core.playback.PlaybackContextState
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.playback.PlaybackSettingsRepository
import com.rovia.music.core.playback.RecentPlayRepository
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Media3PlaybackController(
    context: Context,
    private val recentPlayRepository: RecentPlayRepository,
    private val playbackSettingsRepository:
        PlaybackSettingsRepository,
    private val playbackContextState: PlaybackContextState =
        PlaybackContextState(),
) : PlaybackController {

    private val applicationContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate,
        )

    private val _playbackState =
        MutableStateFlow(
            PlaybackState(),
        )

    override val playbackState: StateFlow<PlaybackState> =
        _playbackState

    override val recentPlays: StateFlow<List<Track>> =
        recentPlayRepository.recentPlays

    private var controller: MediaController? = null

    private var queue: List<Track> = emptyList()

    private var positionJob: Job? = null

    private val isRegularContextActive: Boolean
        get() =
            playbackContextState.activeContext.value ==
                PlaybackContext.REGULAR

    private val controllerListener =
        object : MediaController.Listener {

            override fun onDisconnected(
                controller: MediaController,
            ) {
                if (
                    this@Media3PlaybackController.controller !==
                        controller
                ) {
                    return
                }

                controller.removeListener(playerListener)

                this@Media3PlaybackController.controller =
                    null

                stopPositionUpdates()

                /*
                 * While external playback is active, the regular
                 * queue and UI state must not be discarded merely
                 * because the shared MediaSession disconnects.
                 */
                if (!isRegularContextActive) {
                    return
                }

                queue = emptyList()

                val settings =
                    playbackSettingsRepository
                        .settings
                        .value

                _playbackState.value =
                    PlaybackState(
                        repeatMode = settings.repeatMode,
                        shuffleEnabled = settings.shuffleEnabled,
                    )
            }
        }

    private val playerListener =
        object : Player.Listener {

            override fun onIsPlayingChanged(
                isPlaying: Boolean,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                updatePlaybackState()

                if (isPlaying) {
                    recordCurrentTrack()
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                updatePlaybackState()

                if (
                    mediaItem != null &&
                    controller?.isPlaying == true
                ) {
                    recordCurrentTrack()
                    startPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(
                playbackState: Int,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                updatePlaybackState()

                when {
                    playbackState == Player.STATE_ENDED -> {
                        stopPositionUpdates()
                    }

                    controller?.isPlaying == true -> {
                        startPositionUpdates()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                updatePlaybackState()
            }

            override fun onRepeatModeChanged(
                repeatMode: Int,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                _playbackState.value =
                    _playbackState.value.copy(
                        repeatMode = repeatMode.toRepeatMode(),
                    )
            }

            override fun onShuffleModeEnabledChanged(
                shuffleModeEnabled: Boolean,
            ) {
                if (!isRegularContextActive) {
                    return
                }

                _playbackState.value =
                    _playbackState.value.copy(
                        shuffleEnabled = shuffleModeEnabled,
                    )
            }
        }

    init {
        scope.launch {
            playbackSettingsRepository
                .settings
                .collect { settings ->

                    /*
                     * Persistent settings belong to regular playback.
                     * Do not apply or reflect them while the external
                     * player owns the shared MediaSession.
                     */
                    if (!isRegularContextActive) {
                        return@collect
                    }

                    _playbackState.value =
                        _playbackState.value.copy(
                            repeatMode = settings.repeatMode,
                            shuffleEnabled = settings.shuffleEnabled,
                        )

                    applyPlaybackSettings()
                }
        }

        scope.launch {
            playbackContextState.activeContext.collect { context ->
                when (context) {
                    PlaybackContext.EXTERNAL -> {
                        /*
                         * Stop observing positions from the shared
                         * MediaSession while it controls external
                         * playback. Preserve the regular queue.
                         */
                        stopPositionUpdates()
                    }

                    PlaybackContext.REGULAR -> {
                        /*
                         * The service should have restored the
                         * regular player before the context changes
                         * back to REGULAR.
                         *
                         * Refresh its state without recording a
                         * Recent Play simply because it was restored.
                         */
                        applyPlaybackSettings()
                        updatePlaybackState()

                        if (
                            controller?.isPlaying == true &&
                            queue.isNotEmpty()
                        ) {
                            startPositionUpdates()
                        } else {
                            stopPositionUpdates()
                        }
                    }
                }
            }
        }

        connect()
    }

    override fun play(
        track: Track,
    ) {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        queue = listOf(track)

        mediaController.setMediaItem(
            track.toMediaItem(),
        )

        applyPlaybackSettings()

        mediaController.prepare()
        mediaController.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    override fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
    ) {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        if (tracks.isEmpty()) {
            return
        }

        val safeIndex =
            startIndex.coerceIn(
                minimumValue = 0,
                maximumValue = tracks.lastIndex,
            )

        queue = tracks

        mediaController.setMediaItems(
            tracks.map { track ->
                track.toMediaItem()
            },
            safeIndex,
            0L,
        )

        applyPlaybackSettings()

        mediaController.prepare()
        mediaController.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    override fun pause() {
        if (!isRegularContextActive) {
            return
        }

        controller?.pause()

        updatePlaybackState()
        stopPositionUpdates()
    }

    override fun resume() {
        if (!isRegularContextActive) {
            return
        }

        controller?.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    override fun stopAndClearQueue() {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: run {
                queue = emptyList()

                stopPositionUpdates()

                val settings =
                    playbackSettingsRepository
                        .settings
                        .value

                _playbackState.value =
                    PlaybackState(
                        repeatMode = settings.repeatMode,
                        shuffleEnabled = settings.shuffleEnabled,
                    )

                return
            }

        stopPositionUpdates()

        mediaController.stop()
        mediaController.clearMediaItems()

        queue = emptyList()

        val settings =
            playbackSettingsRepository
                .settings
                .value

        _playbackState.value =
            PlaybackState(
                repeatMode = settings.repeatMode,
                shuffleEnabled = settings.shuffleEnabled,
            )
    }

    override fun seekTo(
        positionMs: Long,
    ) {
        if (!isRegularContextActive) {
            return
        }

        controller?.seekTo(
            positionMs.coerceAtLeast(0L),
        )

        updatePlaybackState()
    }

    override fun restartCurrentTrack() {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        val currentIndex =
            mediaController.currentMediaItemIndex

        if (
            queue.isEmpty() ||
            currentIndex !in queue.indices
        ) {
            return
        }

        mediaController.seekTo(0L)
        mediaController.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    override fun skipToNext() {
        if (!isRegularContextActive) {
            return
        }

        controller?.seekToNext()

        updatePlaybackState()
    }

    override fun skipToPrevious() {
        if (!isRegularContextActive) {
            return
        }

        controller?.seekToPrevious()

        updatePlaybackState()
    }

    override fun setRepeatMode(
        repeatMode: RepeatMode,
    ) {
        if (!isRegularContextActive) {
            return
        }

        _playbackState.value =
            _playbackState.value.copy(
                repeatMode = repeatMode,
            )

        val mediaController =
            controller

        if (
            mediaController != null &&
            mediaController.isCommandAvailable(
                Player.COMMAND_SET_REPEAT_MODE,
            )
        ) {
            mediaController.setRepeatMode(
                repeatMode.toMedia3RepeatMode(),
            )
        }

        scope.launch {
            playbackSettingsRepository.setRepeatMode(
                repeatMode,
            )
        }
    }

    override fun setShuffleEnabled(
        enabled: Boolean,
    ) {
        if (!isRegularContextActive) {
            return
        }

        _playbackState.value =
            _playbackState.value.copy(
                shuffleEnabled = enabled,
            )

        val mediaController =
            controller

        if (
            mediaController != null &&
            mediaController.isCommandAvailable(
                Player.COMMAND_SET_SHUFFLE_MODE,
            )
        ) {
            mediaController.setShuffleModeEnabled(
                enabled,
            )
        }

        scope.launch {
            playbackSettingsRepository.setShuffleEnabled(
                enabled,
            )
        }
    }

    private fun applyPlaybackSettings() {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        val settings =
            playbackSettingsRepository
                .settings
                .value

        if (
            mediaController.isCommandAvailable(
                Player.COMMAND_SET_REPEAT_MODE,
            )
        ) {
            val media3RepeatMode =
                settings.repeatMode.toMedia3RepeatMode()

            if (
                mediaController.repeatMode != media3RepeatMode
            ) {
                mediaController.setRepeatMode(
                    media3RepeatMode,
                )
            }
        }

        if (
            mediaController.isCommandAvailable(
                Player.COMMAND_SET_SHUFFLE_MODE,
            )
        ) {
            if (
                mediaController.shuffleModeEnabled !=
                    settings.shuffleEnabled
            ) {
                mediaController.setShuffleModeEnabled(
                    settings.shuffleEnabled,
                )
            }
        }
    }

    private fun recordCurrentTrack() {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        val currentTrack =
            queue.getOrNull(
                mediaController.currentMediaItemIndex,
            ) ?: return

        scope.launch {
            /*
             * Check again when the coroutine executes, because the
             * playback context may have changed after it was launched.
             */
            if (!isRegularContextActive) {
                return@launch
            }

            recentPlayRepository.record(currentTrack)
        }
    }

    private fun connect() {
        val sessionToken =
            SessionToken(
                applicationContext,
                ComponentName(
                    applicationContext,
                    RoviaMediaSessionService::class.java,
                ),
            )

        val controllerFuture =
            MediaController.Builder(
                applicationContext,
                sessionToken,
            )
                .setListener(controllerListener)
                .buildAsync()

        controllerFuture.addListener(
            {
                val mediaController =
                    runCatching {
                        controllerFuture.get()
                    }.getOrNull()
                        ?: return@addListener

                controller = mediaController

                mediaController.addListener(
                    playerListener,
                )

                if (!isRegularContextActive) {
                    stopPositionUpdates()
                    return@addListener
                }

                applyPlaybackSettings()
                updatePlaybackState()

                if (mediaController.isPlaying) {
                    recordCurrentTrack()
                    startPositionUpdates()
                }
            },
            Executor { runnable ->
                runnable.run()
            },
        )
    }

    private fun startPositionUpdates() {
        if (
            !isRegularContextActive ||
            positionJob?.isActive == true
        ) {
            return
        }

        positionJob =
            scope.launch {
                while (isActive) {
                    if (!isRegularContextActive) {
                        break
                    }

                    val mediaController =
                        controller ?: break

                    if (queue.isEmpty()) {
                        break
                    }

                    updatePlaybackState()

                    delay(250L)

                    if (
                        !isRegularContextActive ||
                        !mediaController.isPlaying
                    ) {
                        break
                    }
                }

                positionJob = null
            }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun updatePlaybackState() {
        if (!isRegularContextActive) {
            return
        }

        val mediaController =
            controller ?: return

        val currentIndex =
            mediaController.currentMediaItemIndex

        val currentTrack =
            queue.getOrNull(currentIndex)

        _playbackState.value =
            PlaybackState(
                currentTrack = currentTrack,
                isPlaying = mediaController.isPlaying,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = mediaController.duration.coerceAtLeast(0L),
                repeatMode = mediaController.repeatMode.toRepeatMode(),
                shuffleEnabled = mediaController.shuffleModeEnabled,
            )
    }
}

private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build(),
        )
        .build()

private fun RepeatMode.toMedia3RepeatMode(): Int =
    when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

private fun Int.toRepeatMode(): RepeatMode =
    when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }
