package com.rovia.music.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class SeekForwardingPlayer(
    delegate: Player,
    private val onSeek: (mediaId: String, positionUs: Long) -> Unit,
) : ForwardingPlayer(delegate) {

    private var lastSeekMediaId: String? = null
    private var lastSeekPositionMs: Long = Long.MIN_VALUE
    private var lastSeekAtMs: Long = 0L
    private var lastIncrementDirection: Int = 0
    private var lastIncrementAtMs: Long = 0L

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex != currentMediaItemIndex ||
            mediaItemIndex !in 0 until mediaItemCount
        ) {
            super.seekTo(mediaItemIndex, positionMs)
            return
        }
        val mediaId = getMediaItemAt(mediaItemIndex).mediaId
        if (isDuplicateSeek(mediaId, positionMs)) {
            return
        }
        recordSeek(mediaId, positionMs)
        onSeek(mediaId, positionMs * 1_000L)
        super.seekTo(mediaItemIndex, positionMs)
    }

    private fun isDuplicateSeek(mediaId: String, positionMs: Long): Boolean {
        return mediaId == lastSeekMediaId &&
            positionMs == lastSeekPositionMs &&
            SystemClock.elapsedRealtime() - lastSeekAtMs < DUPLICATE_SEEK_WINDOW_MS
    }

    private fun recordSeek(mediaId: String, positionMs: Long) {
        lastSeekMediaId = mediaId
        lastSeekPositionMs = positionMs
        lastSeekAtMs = SystemClock.elapsedRealtime()
    }

    override fun seekTo(positionMs: Long) {
        seekTo(currentMediaItemIndex, positionMs)
    }

    override fun seekForward() {
        val item = currentMediaItem
        if (item == null) {
            super.seekForward()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastIncrementDirection == 1 &&
            now - lastIncrementAtMs < DUPLICATE_INCREMENT_WINDOW_MS
        ) {
            return
        }
        lastIncrementDirection = 1
        lastIncrementAtMs = now
        val targetMs = currentPosition + seekForwardIncrement
        recordSeek(item.mediaId, targetMs)
        onSeek(item.mediaId, targetMs * 1_000L)
        super.seekTo(currentMediaItemIndex, targetMs)
    }

    override fun seekBack() {
        val item = currentMediaItem
        if (item == null) {
            super.seekBack()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastIncrementDirection == -1 &&
            now - lastIncrementAtMs < DUPLICATE_INCREMENT_WINDOW_MS
        ) {
            return
        }
        lastIncrementDirection = -1
        lastIncrementAtMs = now
        val targetMs = (currentPosition - seekBackIncrement).coerceAtLeast(0L)
        recordSeek(item.mediaId, targetMs)
        onSeek(item.mediaId, targetMs * 1_000L)
        super.seekTo(currentMediaItemIndex, targetMs)
    }

    private companion object {
        const val DUPLICATE_SEEK_WINDOW_MS = 1_000L
        const val DUPLICATE_INCREMENT_WINDOW_MS = 300L
    }

}
