package com.rovia.music.playback.controller

import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicLong
import kotlin.uuid.Uuid
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
import com.rovia.music.playback.queue.PlaybackMetadataKeys
import com.rovia.music.playback.queue.PlaybackOrigin
import com.rovia.music.playback.queue.PlaybackOriginType
import com.rovia.music.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXTRA_TRACK_ID = PlaybackMetadataKeys.TRACK_ID
private const val EXTRA_TRACK_URI = PlaybackMetadataKeys.TRACK_URI
private const val EXTRA_TRACK_DURATION_MS = PlaybackMetadataKeys.TRACK_DURATION_MS
private const val EXTRA_TRACK_DATE_ADDED = PlaybackMetadataKeys.TRACK_DATE_ADDED
private const val EXTRA_TRACK_RELATIVE_PATH = PlaybackMetadataKeys.TRACK_RELATIVE_PATH
private const val EXTRA_TRACK_DATE_MODIFIED = PlaybackMetadataKeys.TRACK_DATE_MODIFIED

data class PlaybackQueueItem(
    val index: Int,
    val mediaId: String,
    val track: LocalAudioTrack,
)

data class PlaybackQueueState(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = -1,
)

@OptIn(ExperimentalAtomicApi::class)
class PlaybackController(
    context: Context,
) {
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
        queueRequestId.incrementAndFetch()
        detachQueueListener()
        _mediaController.value = null
        val releaseAction = releaseControllerFuture
        releaseControllerFuture = null
        releaseAction?.invoke()
    }

    fun playQueue(
        tracks: List<LocalAudioTrack>,
        startIndex: Int,
        origin: PlaybackOrigin? = null,
    ): Boolean {
        val controller = _mediaController.value ?: return false
        if (tracks.isEmpty() || startIndex !in tracks.indices) return false

        val requestedTracks = tracks.toList()
        val selectedTrack = requestedTracks[startIndex]
        val playbackOrigin = origin ?: PlaybackOrigin(
            type = PlaybackOriginType.TRACK,
            entityKey = buildString {
                append("media-store:")
                append(selectedTrack.volumeName?.takeIf(String::isNotBlank) ?: "external")
                append(':')
                append(selectedTrack.id)
            },
            title = selectedTrack.title
                ?: selectedTrack.displayName?.substringBeforeLast('.')
                ?: selectedTrack.uri.lastPathSegment.orEmpty(),
            subtitle = selectedTrack.artist,
            artworkSourceUri = selectedTrack.uri.toString(),
        )
        val queueSessionKey = Uuid.random().toString()
        val requestId = queueRequestId.incrementAndFetch()

        queueScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                requestedTracks.mapIndexed { index, track ->
                    val extras = Bundle().apply {
                        putLong(EXTRA_TRACK_ID, track.id)
                        putString(EXTRA_TRACK_URI, track.uri.toString())
                        putLong(
                            EXTRA_TRACK_DURATION_MS,
                            track.durationMs.coerceAtLeast(0L),
                        )
                        putLong(EXTRA_TRACK_DATE_ADDED, track.dateAddedSeconds)
                        track.relativePath?.let {
                            putString(EXTRA_TRACK_RELATIVE_PATH, it)
                        }
                        putLong(EXTRA_TRACK_DATE_MODIFIED, track.dateModifiedSeconds)

                        track.volumeName?.let {
                            putString(PlaybackMetadataKeys.TRACK_VOLUME_NAME, it)
                        }
                        track.fileSizeBytes?.let {
                            putLong(PlaybackMetadataKeys.TRACK_FILE_SIZE, it)
                        }
                        track.mimeType?.let {
                            putString(PlaybackMetadataKeys.TRACK_MIME_TYPE, it)
                        }
                        track.displayName?.let {
                            putString(PlaybackMetadataKeys.TRACK_DISPLAY_NAME, it)
                        }
                        track.albumArtist?.let {
                            putString(PlaybackMetadataKeys.TRACK_ALBUM_ARTIST, it)
                        }

                        putString(
                            PlaybackMetadataKeys.TRACK_ARTWORK_SOURCE_URI,
                            track.uri.toString(),
                        )
                        putString(
                            PlaybackMetadataKeys.QUEUE_SESSION_KEY,
                            queueSessionKey,
                        )
                        putString(
                            PlaybackMetadataKeys.QUEUE_ITEM_OCCURRENCE_KEY,
                            "$queueSessionKey:$index",
                        )

                        putString(
                            PlaybackMetadataKeys.ORIGIN_TYPE,
                            playbackOrigin.type.name,
                        )
                        putString(
                            PlaybackMetadataKeys.ORIGIN_ENTITY_KEY,
                            playbackOrigin.entityKey,
                        )
                        putString(
                            PlaybackMetadataKeys.ORIGIN_TITLE,
                            playbackOrigin.title,
                        )
                        playbackOrigin.subtitle?.let {
                            putString(
                                PlaybackMetadataKeys.ORIGIN_SUBTITLE,
                                it,
                            )
                        }
                        playbackOrigin.artworkSourceUri?.let {
                            putString(
                                PlaybackMetadataKeys.ORIGIN_ARTWORK_SOURCE_URI,
                                it,
                            )
                        }
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
                requestId != queueRequestId.load() ||
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
            albumArtist = extras?.getString(PlaybackMetadataKeys.TRACK_ALBUM_ARTIST),
            mimeType = extras?.getString(PlaybackMetadataKeys.TRACK_MIME_TYPE),
            displayName = extras?.getString(PlaybackMetadataKeys.TRACK_DISPLAY_NAME),
            fileSizeBytes = extras?.getLong(PlaybackMetadataKeys.TRACK_FILE_SIZE),
            volumeName = extras?.getString(PlaybackMetadataKeys.TRACK_VOLUME_NAME),
        )
    }
}
