package com.rovia.music.playback.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

class PlaybackController(context: Context) {

    private val appContext = context.applicationContext

    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController = _mediaController.asStateFlow()

    private var releaseControllerFuture: (() -> Unit)? = null
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueRequestId = AtomicLong(0L)

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
                }
            },
            appContext.mainExecutor,
        )
    }

    fun disconnect() {
        queueRequestId.incrementAndGet()
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

        // Freeze the exact source list and selected position for this request. No title-based
        // deduplication or substitution is performed, so the selected URI remains the one played.
        val requestedTracks = tracks.toList()
        val requestId = queueRequestId.incrementAndGet()

        queueScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                requestedTracks.map { track ->
                    val metadata = MediaMetadata.Builder().apply {
                        track.title?.let { setTitle(it) }
                        track.artist?.let { setArtist(it) }
                        track.album?.let { setAlbumTitle(it) }
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
}