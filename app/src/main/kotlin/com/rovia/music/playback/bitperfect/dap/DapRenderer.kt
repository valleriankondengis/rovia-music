package com.rovia.music.playback.bitperfect.dap

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.MediaClock
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream

private const val MAX_INPUT_SIZE_BYTES = 1_048_576

@OptIn(UnstableApi::class)
internal class DapRenderer(
    private val coordinator: DapPlaybackCoordinator,
) : BaseRenderer(C.TRACK_TYPE_AUDIO) {

    private val mediaClock = DapMediaClock(coordinator)
    private var inputBuffer: DecoderInputBuffer? = null

    override fun getName(): String = "DapRenderer"

    override fun getMediaClock(): MediaClock = mediaClock

    override fun supportsFormat(format: Format): Int {
        val mimeType = format.sampleMimeType ?: return C.FORMAT_UNSUPPORTED_TYPE
        return if (MimeTypes.isAudio(mimeType)) {
            C.FORMAT_HANDLED
        } else {
            C.FORMAT_UNSUPPORTED_TYPE
        }
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        coordinator.failureOrNull()?.let { message ->
            throw createRendererException(
                IllegalStateException(message),
                streamFormats.firstOrNull(),
                PlaybackException.ERROR_CODE_UNSPECIFIED,
            )
        }
        drainStream(positionUs)
    }

    override fun isReady(): Boolean = coordinator.isReady()

    override fun isEnded(): Boolean = coordinator.isEnded() && hasReadStreamToEnd()

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        coordinator.onRendererEnabled()
    }

    override fun onStreamChanged(
        formats: Array<Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        coordinator.onRendererStreamChanged(startPositionUs, offsetUs)
    }

    override fun onPositionReset(
        positionUs: Long,
        joining: Boolean,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) {
        coordinator.onRendererPositionReset(positionUs)
    }

    override fun onStarted() {
        coordinator.onRendererStarted()
    }

    override fun onStopped() {
        coordinator.onRendererStopped()
    }

    override fun onDisabled() {
        coordinator.onRendererDisabled()
    }

    private fun drainStream(positionUs: Long) {
        val buffer = inputBuffer ?: DecoderInputBuffer(
            DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL,
        ).also { created ->
            created.ensureSpaceForWrite(MAX_INPUT_SIZE_BYTES)
            inputBuffer = created
        }
        val formatHolder = formatHolder
        val limitUs = if (coordinator.isEnded()) Long.MAX_VALUE else positionUs
        while (true) {
            buffer.clear()
            val peekResult = try {
                readSource(formatHolder, buffer, SampleStream.FLAG_PEEK)
            } catch (failure: Exception) {
                return
            }
            if (buffer.isEndOfStream()) {
                if (!coordinator.isEnded()) return
                buffer.clear()
                try {
                    readSource(formatHolder, buffer, 0)
                } catch (failure: Exception) {
                    return
                }
                return
            }
            when (peekResult) {
                C.RESULT_BUFFER_READ -> {
                    if (buffer.timeUs != C.TIME_UNSET && buffer.timeUs > limitUs) return
                    buffer.clear()
                    val result = try {
                        readSource(formatHolder, buffer, 0)
                    } catch (failure: Exception) {
                        return
                    }
                    if (result != C.RESULT_BUFFER_READ) return
                    if (buffer.isEndOfStream()) return
                }
                C.RESULT_FORMAT_READ -> {
                    buffer.clear()
                    try {
                        readSource(formatHolder, buffer, 0)
                    } catch (failure: Exception) {
                        return
                    }
                }
                else -> return
            }
        }
    }
}
