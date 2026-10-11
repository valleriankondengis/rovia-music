
package com.rovia.music.playback.media3

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.ExternalPlaybackController
import com.rovia.music.core.playback.PlaybackContext
import com.rovia.music.core.playback.PlaybackContextState
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controls playback initiated through an external content URI.
 *
 * This controller deliberately has no dependency on the regular
 * music catalog, Recent Play repository, or persistent playback
 * settings repository.
 *
 * The external queue is limited to one track. Playback settings
 * belong to this controller's in-memory state and are never
 * persisted by this implementation.
 */
class ExternalMedia3PlaybackController(
    context: Context,
    private val playbackContextState: PlaybackContextState,
) : ExternalPlaybackController {

    private val applicationContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate,
        )

    private val mutablePlaybackState =
        MutableStateFlow(
            PlaybackState(),
        )

    override val playbackState: StateFlow<PlaybackState> =
        mutablePlaybackState.asStateFlow()

    private var controller: MediaController? = null

    private var controllerConnecting = false

    private var externalPlayerActivated = false

    private var activationInFlight = false

    private var restorationInFlight = false

    private var released = false

    private var queue: List<Track> = emptyList()

    private var pendingTrack: Track? = null

    private var positionJob: Job? = null

    private val isExternalContextActive: Boolean
        get() =
            externalPlayerActivated &&
                playbackContextState.isExternalActive

    private val controllerListener =
        object : MediaController.Listener {

            override fun onDisconnected(
                controller: MediaController,
            ) {
                if (
                    this@ExternalMedia3PlaybackController
                        .controller !== controller
                ) {
                    return
                }

                controller.removeListener(playerListener)

                this@ExternalMedia3PlaybackController
                    .controller = null

                controllerConnecting = false
                externalPlayerActivated = false
                activationInFlight = false
                restorationInFlight = false

                pendingTrack = null
                queue = emptyList()

                stopPositionUpdates()

                if (playbackContextState.isExternalActive) {
                    playbackContextState.setActiveContext(
                        PlaybackContext.REGULAR,
                    )
                }

                mutablePlaybackState.value =
                    PlaybackState()
            }
        }

    private val playerListener =
        object : Player.Listener {

            override fun onIsPlayingChanged(
                isPlaying: Boolean,
            ) {
                if (!isExternalContextActive) {
                    return
                }

                updatePlaybackState()

                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (!isExternalContextActive) {
                    return
                }

                updatePlaybackState()

                if (
                    mediaItem != null &&
                    controller?.isPlaying == true
                ) {
                    startPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(
                playbackState: Int,
            ) {
                if (!isExternalContextActive) {
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
                if (!isExternalContextActive) {
                    return
                }

                updatePlaybackState()
            }

            override fun onRepeatModeChanged(
                repeatMode: Int,
            ) {
                if (!isExternalContextActive) {
                    return
                }

                mutablePlaybackState.value =
                    mutablePlaybackState.value.copy(
                        repeatMode = repeatMode.toRoviaRepeatMode(),
                    )
            }

            override fun onShuffleModeEnabledChanged(
                shuffleModeEnabled: Boolean,
            ) {
                if (!isExternalContextActive) {
                    return
                }

                mutablePlaybackState.value =
                    mutablePlaybackState.value.copy(
                        shuffleEnabled = shuffleModeEnabled,
                    )
            }
        }

    init {
        connect()
    }

    override fun startExternalPlayback(
        track: Track,
    ) {
        if (released) {
            return
        }

        /*
         * Mark the external context before switching the MediaSession.
         * The regular controller will use this shared state to ignore
         * events produced while the external player is being activated.
         */
        playbackContextState.setActiveContext(
            PlaybackContext.EXTERNAL,
        )

        pendingTrack = track
        queue = listOf(track)

        /*
         * Repeat and Shuffle start from their default values for each
         * new external session. Changes remain in memory only.
         */
        mutablePlaybackState.value =
            PlaybackState(
                currentTrack = track,
                isPlaying = false,
                positionMs = 0L,
                durationMs = track.durationMs.coerceAtLeast(0L),
                repeatMode = RepeatMode.OFF,
                shuffleEnabled = false,
            )

        if (externalPlayerActivated) {
            loadCurrentTrack(track)
            return
        }

        val mediaController = controller

        if (mediaController == null) {
            if (!controllerConnecting) {
                connect()
            }
            return
        }

        activateExternalPlayer()
    }

    override fun endExternalPlayback() {
        if (released) {
            return
        }

        pendingTrack = null

        stopPositionUpdates()

        if (
            !playbackContextState.isExternalActive &&
            !externalPlayerActivated &&
            !activationInFlight &&
            !restorationInFlight
        ) {
            queue = emptyList()
            mutablePlaybackState.value = PlaybackState()
            return
        }

        /*
         * Do not clear the regular player's queue here.
         * The service owns the player switch and cleanup.
         */
        requestRestoreRegularPlayer()
    }

    override fun pause() {
        if (!isExternalContextActive) {
            return
        }

        controller?.pause()

        updatePlaybackState()
        stopPositionUpdates()
    }

    override fun resume() {
        if (!isExternalContextActive || queue.isEmpty()) {
            return
        }

        controller?.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    override fun stopAndClearQueue() {
        if (!isExternalContextActive) {
            queue = emptyList()
            stopPositionUpdates()
            return
        }

        stopPositionUpdates()

        val mediaController = controller

        if (
            mediaController != null &&
            mediaController.isCommandAvailable(
                Player.COMMAND_STOP,
            )
        ) {
            mediaController.stop()
        }

        if (
            mediaController != null &&
            mediaController.isCommandAvailable(
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
            )
        ) {
            mediaController.clearMediaItems()
        }

        pendingTrack = null
        queue = emptyList()

        mutablePlaybackState.value =
            mutablePlaybackState.value.copy(
                currentTrack = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
            )
    }

    override fun seekTo(
        positionMs: Long,
    ) {
        if (!isExternalContextActive) {
            return
        }

        val mediaController = controller ?: return

        if (
            !mediaController.isCommandAvailable(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            )
        ) {
            return
        }

        mediaController.seekTo(
            positionMs.coerceAtLeast(0L),
        )

        updatePlaybackState()
    }

    override fun restartCurrentTrack() {
        if (
            !isExternalContextActive ||
            queue.isEmpty()
        ) {
            return
        }

        val mediaController = controller ?: return

        if (
            !mediaController.isCommandAvailable(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            )
        ) {
            return
        }

        mediaController.seekTo(0L)
        mediaController.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    /**
     * A one-item external queue cannot advance to another track.
     */
    override fun skipToNext() {
        if (!isExternalContextActive) {
            return
        }

        // Deliberately does not access the regular player's queue.
    }

    /**
     * Previous restarts the only track instead of accessing
     * the regular player's queue.
     */
    override fun skipToPrevious() {
        restartCurrentTrack()
    }

    override fun setRepeatMode(
        repeatMode: RepeatMode,
    ) {
        if (!playbackContextState.isExternalActive) {
            return
        }

        mutablePlaybackState.value =
            mutablePlaybackState.value.copy(
                repeatMode = repeatMode,
            )

        applyExternalPlaybackSettings()
    }

    override fun setShuffleEnabled(
        enabled: Boolean,
    ) {
        if (!playbackContextState.isExternalActive) {
            return
        }

        mutablePlaybackState.value =
            mutablePlaybackState.value.copy(
                shuffleEnabled = enabled,
            )

        applyExternalPlaybackSettings()
    }

    /**
     * Releases this controller's MediaController connection.
     *
     * The caller should end the external session before releasing
     * the controller so the service can restore regular playback.
     */
    fun release() {
        if (released) {
            return
        }

        released = true

        stopPositionUpdates()

        controller?.removeListener(playerListener)
        controller?.release()
        controller = null

        scope.cancel()
    }

    private fun connect() {
        if (
            released ||
            controllerConnecting ||
            controller != null
        ) {
            return
        }

        controllerConnecting = true

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

                scope.launch {
                    controllerConnecting = false

                    if (released) {
                        mediaController?.release()
                        return@launch
                    }

                    if (mediaController == null) {
                        handleConnectionFailure()
                        return@launch
                    }

                    controller = mediaController

                    mediaController.addListener(
                        playerListener,
                    )

                    if (
                        playbackContextState.isExternalActive &&
                        pendingTrack != null
                    ) {
                        activateExternalPlayer()
                    }
                }
            },
            Executor { runnable ->
                runnable.run()
            },
        )
    }

    private fun activateExternalPlayer() {
        if (
            released ||
            activationInFlight ||
            externalPlayerActivated ||
            !playbackContextState.isExternalActive ||
            pendingTrack == null
        ) {
            return
        }

        val mediaController = controller ?: return

        activationInFlight = true

        sendInternalCommand(
            mediaController = mediaController,
            commandAction =
                RoviaMediaSessionService
                    .COMMAND_ACTIVATE_EXTERNAL_PLAYER,
        ) { succeeded ->

            activationInFlight = false

            if (!succeeded) {
                handleConnectionFailure()
                return@sendInternalCommand
            }

            externalPlayerActivated = true

            val currentTrack = pendingTrack

            if (
                !playbackContextState.isExternalActive ||
                currentTrack == null
            ) {
                requestRestoreRegularPlayer()
                return@sendInternalCommand
            }

            /*
             * Read pendingTrack again after activation so that the
             * most recently requested external file is the one loaded.
             */
            queue = listOf(currentTrack)

            loadCurrentTrack(currentTrack)
        }
    }

    private fun loadCurrentTrack(
        track: Track,
    ) {
        if (!isExternalContextActive) {
            return
        }

        val mediaController = controller ?: return

        if (
            !mediaController.isCommandAvailable(
                Player.COMMAND_SET_MEDIA_ITEM,
            )
        ) {
            handleConnectionFailure()
            return
        }

        queue = listOf(track)

        mediaController.setMediaItem(
            track.toExternalMediaItem(),
        )

        applyExternalPlaybackSettings()

        mediaController.prepare()
        mediaController.play()

        updatePlaybackState()
        startPositionUpdates()
    }

    private fun applyExternalPlaybackSettings() {
        if (!isExternalContextActive) {
            return
        }

        val mediaController = controller ?: return
        val currentState = mutablePlaybackState.value

        if (
            mediaController.isCommandAvailable(
                Player.COMMAND_SET_REPEAT_MODE,
            )
        ) {
            val media3RepeatMode =
                currentState.repeatMode.toMedia3RepeatMode()

            if (mediaController.repeatMode != media3RepeatMode) {
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
                    currentState.shuffleEnabled
            ) {
                mediaController.setShuffleModeEnabled(
                    currentState.shuffleEnabled,
                )
            }
        }
    }

    private fun requestRestoreRegularPlayer() {
        if (restorationInFlight) {
            return
        }

        val mediaController = controller

        if (mediaController == null) {
            /*
             * Without an active MediaController we cannot issue
             * the service restoration command. Avoid clearing the
             * regular queue or writing any persistent state.
             */
            return
        }

        restorationInFlight = true

        sendInternalCommand(
            mediaController = mediaController,
            commandAction =
                RoviaMediaSessionService
                    .COMMAND_RESTORE_REGULAR_PLAYER,
        ) { succeeded ->

            restorationInFlight = false

            if (!succeeded) {
                /*
                 * Do not claim that the regular context has been
                 * restored when the service has not confirmed it.
                 * A later close/recovery attempt may retry.
                 */
                mediaController.pause()
                return@sendInternalCommand
            }

            externalPlayerActivated = false
            activationInFlight = false

            pendingTrack = null
            queue = emptyList()

            stopPositionUpdates()

            mutablePlaybackState.value =
                PlaybackState()

            playbackContextState.setActiveContext(
                PlaybackContext.REGULAR,
            )
        }
    }

    private fun sendInternalCommand(
        mediaController: MediaController,
        commandAction: String,
        onComplete: (Boolean) -> Unit,
    ) {
        val sessionCommand =
            SessionCommand(
                commandAction,
                Bundle.EMPTY,
            )

        if (
            !mediaController.isSessionCommandAvailable(
                sessionCommand,
            )
        ) {
            onComplete(false)
            return
        }

        val commandFuture =
            mediaController.sendCustomCommand(
                sessionCommand,
                Bundle.EMPTY,
            )

        commandFuture.addListener(
            {
                val succeeded =
                    runCatching {
                        commandFuture.get().resultCode ==
                            SessionResult.RESULT_SUCCESS
                    }.getOrDefault(false)

                scope.launch {
                    if (!released) {
                        onComplete(succeeded)
                    }
                }
            },
            Executor { runnable ->
                runnable.run()
            },
        )
    }

    private fun handleConnectionFailure() {
        stopPositionUpdates()

        pendingTrack = null
        queue = emptyList()

        externalPlayerActivated = false
        activationInFlight = false

        mutablePlaybackState.value =
            PlaybackState()

        if (playbackContextState.isExternalActive) {
            playbackContextState.setActiveContext(
                PlaybackContext.REGULAR,
            )
        }
    }

    private fun startPositionUpdates() {
        if (
            positionJob?.isActive == true ||
            !isExternalContextActive
        ) {
            return
        }

        positionJob =
            scope.launch {
                while (isActive) {
                    val mediaController =
                        controller ?: break

                    if (
                        !isExternalContextActive ||
                        queue.isEmpty()
                    ) {
                        break
                    }

                    updatePlaybackState()

                    delay(250L)

                    if (!mediaController.isPlaying) {
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
        if (!isExternalContextActive) {
            return
        }

        val mediaController = controller ?: return

        val currentIndex =
            mediaController.currentMediaItemIndex

        val currentTrack =
            queue.getOrNull(currentIndex)

        val controllerDuration =
            mediaController.duration

        val duration =
            if (controllerDuration > 0L) {
                controllerDuration
            } else {
                currentTrack?.durationMs?.coerceAtLeast(0L)
                    ?: 0L
            }

        mutablePlaybackState.value =
            PlaybackState(
                currentTrack = currentTrack,
                isPlaying = mediaController.isPlaying,
                positionMs =
                    mediaController.currentPosition
                        .coerceAtLeast(0L),
                durationMs = duration,
                repeatMode =
                    mediaController.repeatMode.toRoviaRepeatMode(),
                shuffleEnabled =
                    mediaController.shuffleModeEnabled,
            )
    }
}

private fun Track.toExternalMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(uri)
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
        RepeatMode.OFF ->
            Player.REPEAT_MODE_OFF

        RepeatMode.ALL ->
            Player.REPEAT_MODE_ALL

        RepeatMode.ONE ->
            Player.REPEAT_MODE_ONE
    }

private fun Int.toRoviaRepeatMode(): RepeatMode =
    when (this) {
        Player.REPEAT_MODE_ALL ->
            RepeatMode.ALL

        Player.REPEAT_MODE_ONE ->
            RepeatMode.ONE

        else ->
            RepeatMode.OFF
    }
