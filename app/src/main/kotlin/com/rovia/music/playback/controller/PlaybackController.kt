package com.rovia.music.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private const val EXTRA_TRACK_ID = "com.rovia.music.queue.track_id"
private const val EXTRA_TRACK_URI = "com.rovia.music.queue.track_uri"
private const val EXTRA_TRACK_DURATION_MS = "com.rovia.music.queue.track_duration_ms"
private const val EXTRA_TRACK_DATE_ADDED = "com.rovia.music.queue.track_date_added"
private const val EXTRA_TRACK_RELATIVE_PATH = "com.rovia.music.queue.track_relative_path"
private const val EXTRA_TRACK_DATE_MODIFIED = "com.rovia.music.queue.track_date_modified"

data class PlaybackQueueItem(
    val index: Int,
    val mediaId: String,
    val track: LocalAudioTrack,
)

data class PlaybackQueueState(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = -1,
)

class PlaybackController(context: Context) {

    private val appContext = context.applicationContext

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController = _mediaController.asStateFlow()

    private val _queueState = MutableStateFlow(PlaybackQueueState())
    val queueState = _queueState.asStateFlow()

    private var releaseControllerFuture: (() -> Unit)? = null
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueRequestId = AtomicLong(0L)
    private var queueListenerController: MediaController? = null
    private var queueListener: Player.Listener? = null

    fun connect() {
        if (releaseControllerFuture != null) return

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        val releaseAction: () -> Unit = { MediaController.releaseFuture(future) }
        releaseControllerFuture = releaseAction

        future.addListener(
            {
                val connectedController = runCatching { future.get() }.getOrNull()
                if (releaseControllerFuture !== releaseAction) {
                    connectedController?.release()
                } else if (connectedController == null) {
                    releaseControllerFuture = null
                } else {
                    _mediaController.value = connectedController
                    observeQueue(connectedController)
                }
            },
            appContext.mainExecutor,
        )
    }

    fun disconnect() {
        queueRequestId.incrementAndGet()
        detachQueueListener()
        _mediaController.value = null
        val releaseAction = releaseControllerFuture
        releaseControllerFuture = null
        releaseAction?.invoke()
    }

    fun playQueue(
        tracks: List<LocalAudioTrack>,
        startIndex: Int,
    ): Boolean {
        val controller = _mediaController.value ?: return false
        if (tracks.isEmpty() || startIndex !in tracks.indices) return false

        val requestedTracks = tracks.toList()
        val requestId = queueRequestId.incrementAndGet()

        queueScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                requestedTracks.map { track ->
                    val extras = Bundle().apply {
                        putLong(EXTRA_TRACK_ID, track.id)
                        putString(EXTRA_TRACK_URI, track.uri.toString())
                        putLong(EXTRA_TRACK_DURATION_MS, track.durationMs.coerceAtLeast(0L))
                        putLong(EXTRA_TRACK_DATE_ADDED, track.dateAddedSeconds)
                        track.relativePath?.let { putString(EXTRA_TRACK_RELATIVE_PATH, it) }
                        putLong(EXTRA_TRACK_DATE_MODIFIED, track.dateModifiedSeconds)
                    }

                    val metadata = MediaMetadata.Builder().apply {
                        track.title?.let { setTitle(it) }
                        track.artist?.let { setArtist(it) }
                        track.album?.let { setAlbumTitle(it) }
                        setDurationMs(track.durationMs.coerceAtLeast(0L))
                        setExtras(extras)
                    }.build()

                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(track.uri)
                        .setMediaMetadata(metadata)
                        .build()
                }
            }

            if (
                requestId != queueRequestId.get() ||
                _mediaController.value !== controller ||
                startIndex !in mediaItems.indices
            ) {
                return@launch
            }

            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        }

        return true
    }

    fun playQueueItem(index: Int): Boolean {
        val controller = _mediaController.value ?: return false
        if (index !in 0 until controller.mediaItemCount) return false

        controller.seekTo(index, 0L)
        controller.play()
        return true
    }

    private fun observeQueue(controller: MediaController) {
        detachQueueListener(resetState = false)

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publishQueueState(player)
            }
        }

        queueListenerController = controller
        queueListener = listener
        controller.addListener(listener)
        publishQueueState(controller)
    }

    private fun detachQueueListener(resetState: Boolean = true) {
        val observedController = queueListenerController
        val listener = queueListener
        if (observedController != null && listener != null) {
            observedController.removeListener(listener)
        }
        queueListenerController = null
        queueListener = null
        if (resetState) {
            _queueState.value = PlaybackQueueState()
        }
    }

    private fun publishQueueState(player: Player) {
        val items = (0 until player.mediaItemCount).map { index ->
            val mediaItem = player.getMediaItemAt(index)
            PlaybackQueueItem(
                index = index,
                mediaId = mediaItem.mediaId,
                track = mediaItem.toLocalAudioTrack(index),
            )
        }

        val currentIndex = player.currentMediaItemIndex
            .takeIf { it in items.indices }
            ?: -1

        _queueState.value = PlaybackQueueState(
            items = items,
            currentIndex = currentIndex,
        )
    }

    private fun MediaItem.toLocalAudioTrack(index: Int): LocalAudioTrack {
        val metadata = mediaMetadata
        val extras = metadata.extras
        val fallbackId = mediaId.toLongOrNull() ?: -(index.toLong() + 1L)
        val fallbackUri = extras
            ?.getString(EXTRA_TRACK_URI)
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?: Uri.EMPTY
        val durationMs = extras?.getLong(
            EXTRA_TRACK_DURATION_MS,
            metadata.durationMs ?: 0L,
        ) ?: (metadata.durationMs ?: 0L)

        return LocalAudioTrack(
            id = extras?.getLong(EXTRA_TRACK_ID, fallbackId) ?: fallbackId,
            uri = localConfiguration?.uri ?: fallbackUri,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            album = metadata.albumTitle?.toString(),
            durationMs = durationMs.coerceAtLeast(0L),
            dateAddedSeconds = extras?.getLong(EXTRA_TRACK_DATE_ADDED, 0L) ?: 0L,
            relativePath = extras?.getString(EXTRA_TRACK_RELATIVE_PATH),
            dateModifiedSeconds = extras?.getLong(EXTRA_TRACK_DATE_MODIFIED, 0L) ?: 0L,
        )
    }
}