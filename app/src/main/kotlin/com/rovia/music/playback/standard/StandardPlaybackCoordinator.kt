package com.rovia.music.playback.standard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.rovia.music.playback.PlaybackDecoderStore
import com.rovia.music.playback.PlaybackDiagnostics
import com.rovia.music.playback.decoder.DecoderRegistry
import com.rovia.music.playback.queue.PlaybackMetadataKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

private const val POLL_INTERVAL_MS = 200L
private const val SHUTDOWN_TIMEOUT_MS = 3_000L
private const val MAX_PREPARE_ATTEMPTS = 3
private const val READ_BLOCK_BYTES = 32_768
private const val MAX_WRITE_ATTEMPTS = 8
private const val PUMP_RETRY_DELAY_MS = 5L
private const val OFF_MAX_SAMPLE_RATE = 48_000

internal class StandardPlaybackCoordinator(
    context: Context,
) : AutoCloseable {

    private data class ItemReference(
        val mediaId: String,
        val uri: Uri,
        val extensionHint: String,
    )

    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val generation = AtomicLong(0L)

    private val backend = DecoderRegistry.defaultBackend()

    private val pumpThread = HandlerThread("RoviaStandardPump").apply { start() }
    private val pumpHandler = Handler(pumpThread.looper)

    @Volatile
    private var currentItem: ItemReference? = null

    @Volatile
    private var streamOffsetUs: Long = 0L

    @Volatile
    private var pendingStartSourceUs: Long = 0L

    @Volatile
    private var preparedGeneration: Long = -1L

    @Volatile
    private var preparedMediaId: String? = null

    @Volatile
    private var startedGeneration: Long = -1L

    @Volatile
    private var failureGeneration: Long = -1L

    @Volatile
    private var failureMessage: String? = null

    @Volatile
    private var outputEnded: Boolean = false

    @Volatile
    private var anchorSourcePositionUs: Long = 0L

    @Volatile
    private var anchorElapsedRealtimeMs: Long = 0L

    @Volatile
    private var anchorPlaying: Boolean = false

    @Volatile
    private var rendererEnabled: Boolean = false

    @Volatile
    private var rendererStarted: Boolean = false

    private var pollJob: Job? = null

    private var track: AudioTrack? = null

    @Volatile
    private var pumpPlaying: Boolean = false

    @Volatile
    private var sourceEos: Boolean = false

    @Volatile
    private var framesWritten: Long = 0L

    @Volatile
    private var pendingData: ByteArray? = null
    @Volatile
    private var pendingOffset: Int = 0

    @Volatile
    private var positionBaseFrames: Long = 0L

    @Volatile
    private var sampleRate: Int = 0

    @Volatile
    private var frameBytes: Int = 0

    @Volatile
    private var downsampler: PcmDownsampler? = null

    @Volatile
    private var durationUs: Long = 0L

    private var headWrapBase: Long = 0L
    private var lastHeadRaw: Long = 0L
    private var lastHeadExtended: Long = 0L

    private val pumpRunnable = object : Runnable {
        override fun run() {
            if (!pumpPlaying) return
            val activeTrack = track
            if (activeTrack == null) {
                pumpPlaying = false
                return
            }
            var progressed = false
            try {
                var data = pendingData
                var offset = pendingOffset
                if (data == null) {
                    val block = backend.readBlock(READ_BLOCK_BYTES.toUInt())
                    if (block.data.isNotEmpty()) {
                        val sampler = downsampler
                        data = sampler?.process(block.data, block.eos) ?: block.data
                        offset = 0
                    }
                    if (block.eos) {
                        sourceEos = true
                    }
                }
                if (data != null) {
                    val startOffset = offset
                    var attempts = 0
                    while (offset < data.size && pumpPlaying) {
                        val written = activeTrack.write(
                            data,
                            offset,
                            data.size - offset,
                            AudioTrack.WRITE_NON_BLOCKING,
                        )
                        if (written < 0) {
                            setFailure(
                                generation.get(),
                                "Standard AudioTrack write failed: $written",
                            )
                            pumpPlaying = false
                            break
                        }
                        offset += written
                        if (written == 0) {
                            attempts++
                            if (attempts >= MAX_WRITE_ATTEMPTS) break
                        } else {
                            attempts = 0
                            progressed = true
                        }
                    }
                    val bytesPerFrame = frameBytes
                    if (bytesPerFrame > 0) {
                        framesWritten += (offset - startOffset) / bytesPerFrame
                    }
                    if (offset < data.size) {
                        pendingData = data
                        pendingOffset = offset
                    } else {
                        pendingData = null
                        pendingOffset = 0
                    }
                }
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                setFailure(
                    generation.get(),
                    failure.message ?: "Standard PCM source failed",
                )
                pumpPlaying = false
            }
            if (pumpPlaying) {
                if (progressed) {
                    pumpHandler.post(this)
                } else {
                    pumpHandler.postDelayed(this, PUMP_RETRY_DELAY_MS)
                }
            }
        }
    }

    fun onCurrentItemChanged(item: MediaItem?) {
        if (item == null) {
            currentItem = null
            return
        }
        val uri = item.localConfiguration?.uri
            ?: item.mediaMetadata.extras
                ?.getString(PlaybackMetadataKeys.TRACK_URI)
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
        if (uri == null) {
            currentItem = null
            return
        }
        val displayName = item.mediaMetadata.extras
            ?.getString(PlaybackMetadataKeys.TRACK_DISPLAY_NAME)
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment
        val extension = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        currentItem = ItemReference(
            mediaId = item.mediaId,
            uri = uri,
            extensionHint = extension,
        )
        val prepared = preparedMediaId
        if (rendererEnabled &&
            prepared != null &&
            prepared != item.mediaId &&
            preparedGeneration == generation.get() &&
            failureGeneration != generation.get()
        ) {
            val streamGeneration = generation.get()
            scope.launch {
                commandMutex.withLock {
                    if (generation.get() != streamGeneration) return@withLock
                    releaseSegmentLocked()
                    if (!ensurePreparedLocked(streamGeneration)) return@withLock
                    if (rendererStarted) {
                        startLocked(streamGeneration)
                    }
                }
            }
        }
    }

    fun onRendererEnabled() {
        rendererEnabled = true
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { pollLoop() }
        }
    }

    fun onRendererStreamChanged(startPositionUs: Long, offsetUs: Long) {
        streamOffsetUs = offsetUs
        pendingStartSourceUs = (startPositionUs - offsetUs).coerceAtLeast(0L)
        val streamGeneration = generation.incrementAndGet()
        outputEnded = false
        anchorTo(pendingStartSourceUs, playing = false)
        scope.launch {
            commandMutex.withLock {
                if (!ensurePreparedLocked(streamGeneration)) return@withLock
                if (rendererStarted) {
                    startLocked(streamGeneration)
                }
            }
        }
    }

    fun onRendererStarted() {
        rendererStarted = true
        val streamGeneration = generation.get()
        scope.launch {
            commandMutex.withLock {
                if (failureGeneration == streamGeneration) return@withLock
                if (!ensurePreparedLocked(streamGeneration)) return@withLock
                startLocked(streamGeneration)
            }
        }
    }

    fun onRendererStopped() {
        rendererStarted = false
        val streamGeneration = generation.get()
        scope.launch {
            commandMutex.withLock {
                if (generation.get() != streamGeneration) return@withLock
                if (startedGeneration != streamGeneration) return@withLock
                startedGeneration = -1L
                anchorPlaying = false
                pauseAndReanchorLocked()
            }
        }
    }

    fun onRendererPositionReset(positionUs: Long) {
        val sourcePositionUs = (positionUs - streamOffsetUs).coerceAtLeast(0L)
        pendingStartSourceUs = sourcePositionUs
        anchorTo(sourcePositionUs, playing = anchorPlaying)
        val streamGeneration = generation.get()
        scope.launch {
            commandMutex.withLock {
                if (preparedGeneration != streamGeneration) return@withLock
                reseekLocked(sourcePositionUs, streamGeneration)
            }
        }
    }

    fun onRendererDisabled() {
        rendererEnabled = false
        rendererStarted = false
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            commandMutex.withLock {
                releaseSegmentLocked()
            }
        }
    }

    suspend fun shutdownEngineAndWait() {
        val deferred = scope.async {
            commandMutex.withLock {
                releaseSegmentLocked()
            }
        }
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { deferred.await() }
    }

    fun clockPositionUs(): Long {
        val sourcePositionUs = if (anchorPlaying) {
            val elapsedUs = (SystemClock.elapsedRealtime() - anchorElapsedRealtimeMs)
                .coerceAtLeast(0L) * 1_000L
            val estimated = anchorSourcePositionUs + elapsedUs
            val duration = durationUs
            if (duration > 0L) minOf(estimated, duration) else estimated
        } else {
            anchorSourcePositionUs
        }
        return streamOffsetUs + sourcePositionUs.coerceAtLeast(0L)
    }

    fun isReady(): Boolean {
        if (failureGeneration == generation.get()) return false
        if (outputEnded) return true
        if (preparedGeneration != generation.get()) return false
        return track != null
    }

    fun isEnded(): Boolean = outputEnded

    fun failureOrNull(): String? {
        return if (failureGeneration == generation.get()) failureMessage else null
    }

    override fun close() {
        rendererEnabled = false
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            commandMutex.withLock {
                releaseSegmentLocked()
                runCatching { backend.close() }.onFailure { }
            }
        }
        pumpThread.quitSafely()
    }

    private suspend fun pollLoop() {
        while (scope.isActive && rendererEnabled) {
            pollOnce()
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pollOnce() {
        val activeTrack = track ?: return
        if (preparedGeneration != generation.get()) return
        val head = headFrames()
        val positionFrames = positionBaseFrames + head
        anchorTo(
            sourcePositionUs = framesToUs(positionFrames),
            playing = pumpPlaying &&
                activeTrack.playState == AudioTrack.PLAYSTATE_PLAYING,
        )
        if (sourceEos && head >= framesWritten) {
            outputEnded = true
            runCatching { activeTrack.pause() }.onFailure { }
        }
    }

    private fun anchorTo(sourcePositionUs: Long, playing: Boolean) {
        anchorSourcePositionUs = sourcePositionUs.coerceAtLeast(0L)
        anchorElapsedRealtimeMs = SystemClock.elapsedRealtime()
        anchorPlaying = playing
    }

    private fun setFailure(streamGeneration: Long, message: String) {
        if (failureGeneration == streamGeneration && failureMessage != null) return
        failureGeneration = streamGeneration
        failureMessage = message
        PlaybackDecoderStore.setFailure(message)
        PlaybackDiagnostics.log("OFF failure: $message")
    }

    private fun headFrames(): Long {
        val activeTrack = track ?: return lastHeadExtended
        val raw = activeTrack.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
        if (raw < lastHeadRaw) {
            headWrapBase += 1L shl 32
        }
        lastHeadRaw = raw
        lastHeadExtended = headWrapBase + raw
        return lastHeadExtended
    }

    private fun resetHeadCounters() {
        headWrapBase = 0L
        lastHeadRaw = 0L
        lastHeadExtended = 0L
    }

    private fun framesToUs(frames: Long): Long {
        val rate = sampleRate
        if (rate <= 0) return 0L
        return frames * 1_000_000L / rate
    }

    private fun usToFrames(positionUs: Long): Long {
        val rate = sampleRate
        if (rate <= 0) return 0L
        return positionUs * rate / 1_000_000L
    }

    private suspend fun ensurePreparedLocked(streamGeneration: Long): Boolean {
        if (failureGeneration == streamGeneration) return false
        var attempts = 0
        while (true) {
            val item = currentItem
            if (item == null) {
                setFailure(streamGeneration, "No current media item is available for standard playback")
                return false
            }
            if (preparedGeneration == streamGeneration && preparedMediaId == item.mediaId) {
                return true
            }
            if (streamGeneration != generation.get()) return false
            releaseSegmentLocked()
            val info = try {
                val descriptor = applicationContext.contentResolver.openFileDescriptor(item.uri, "r")
                    ?: throw IllegalStateException("The media source could not be opened")
                val fileDescriptor = descriptor.detachFd()
                backend.openSource(fileDescriptor, item.extensionHint)
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                setFailure(
                    streamGeneration,
                    failure.message ?: "The media source is not supported by the decoder",
                )
                return false
            }
            PlaybackDecoderStore.setDecoderInfo(info)

            val encoding = when (info.bitsPerSample) {
                16 -> AudioFormat.ENCODING_PCM_16BIT
                24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                32 -> AudioFormat.ENCODING_PCM_32BIT
                else -> {
                    setFailure(
                        streamGeneration,
                        "Standard playback does not support a ${info.bitsPerSample}-bit source",
                    )
                    return false
                }
            }
            val channelMask = when (info.channels) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                else -> {
                    setFailure(
                        streamGeneration,
                        "Standard playback does not support ${info.channels} channels",
                    )
                    return false
                }
            }
            val outputSampleRate = if (info.sampleRateHz > OFF_MAX_SAMPLE_RATE) {
                OFF_MAX_SAMPLE_RATE
            } else {
                info.sampleRateHz
            }
            val format = AudioFormat.Builder()
                .setSampleRate(outputSampleRate)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .build()
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                else -> 4
            }
            sampleRate = outputSampleRate
            frameBytes = info.channels * bytesPerSample
            downsampler = if (outputSampleRate != info.sampleRateHz) {
                PcmDownsampler(
                    inputRate = info.sampleRateHz,
                    outputRate = outputSampleRate,
                    channels = info.channels,
                    bytesPerSample = bytesPerSample,
                )
            } else {
                null
            }
            durationUs = info.durationUs
            val minBuffer = AudioTrack.getMinBufferSize(
                format.sampleRate,
                format.channelMask,
                format.encoding,
            )
            val targetBuffer = format.sampleRate / 5 * frameBytes
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val built = try {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, targetBuffer))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                setFailure(
                    streamGeneration,
                    failure.message ?: "Standard AudioTrack could not be created",
                )
                return false
            }
            if (built.state != AudioTrack.STATE_INITIALIZED) {
                built.release()
                setFailure(streamGeneration, "Standard AudioTrack could not be initialized")
                return false
            }
            runCatching { built.setVolume(1.0f) }.onFailure { }
            track = built
            if (streamGeneration != generation.get()) {
                releaseSegmentLocked()
                return false
            }
            val latest = currentItem
            if (latest != null && latest.mediaId != item.mediaId && attempts < MAX_PREPARE_ATTEMPTS) {
                attempts++
                releaseSegmentLocked()
                continue
            }
            val startSourceUs = pendingStartSourceUs
            positionBaseFrames = usToFrames(startSourceUs)
            framesWritten = 0L
            sourceEos = false
            resetHeadCounters()
            if (startSourceUs > 0L) {
                runCatching { backend.seek(startSourceUs.toULong()) }.onFailure {}
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "Standard seek failed")
                        return false
                    }
            }
            preparedGeneration = streamGeneration
            preparedMediaId = item.mediaId
            anchorTo(startSourceUs, playing = false)
            PlaybackDiagnostics.log(
                "OFF prepare ${item.mediaId}: probe ${info.sampleRateHz} Hz " +
                    "${info.bitsPerSample}-bit ${info.channels}ch -> " +
                    "AudioTrack ${format.sampleRate} Hz" +
                    if (downsampler != null) " (downsampled to 48 kHz)" else "",
            )
            return true
        }
    }

    private fun startLocked(streamGeneration: Long) {
        if (streamGeneration != generation.get()) return
        if (startedGeneration == streamGeneration) return
        val activeTrack = track
        if (activeTrack == null) {
            setFailure(streamGeneration, "Standard output is not prepared")
            return
        }
        try {
            activeTrack.play()
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            setFailure(streamGeneration, failure.message ?: "Standard AudioTrack could not start")
            return
        }
        startedGeneration = streamGeneration
        anchorPlaying = true
        anchorElapsedRealtimeMs = SystemClock.elapsedRealtime()
        pumpPlaying = true
        pumpHandler.removeCallbacks(pumpRunnable)
        pumpHandler.post(pumpRunnable)
    }

    private fun pauseAndReanchorLocked() {
        pumpPlaying = false
        pumpHandler.removeCallbacks(pumpRunnable)
        val activeTrack = track ?: return
        val positionFrames = positionBaseFrames + headFrames()
        runCatching { activeTrack.pause() }.onFailure { }
        runCatching { activeTrack.flush() }.onFailure { }
        runCatching { backend.seek(framesToUs(positionFrames).toULong()) }.onFailure { }
        downsampler?.reset()
        positionBaseFrames = positionFrames
        framesWritten = 0L
        pendingData = null
        pendingOffset = 0
        sourceEos = false
        resetHeadCounters()
        anchorTo(framesToUs(positionFrames), playing = false)
    }

    private fun reseekLocked(sourcePositionUs: Long, streamGeneration: Long) {
        if (preparedGeneration != streamGeneration) return
        val wasPlaying = pumpPlaying
        pumpPlaying = false
        pumpHandler.removeCallbacks(pumpRunnable)
        val activeTrack = track ?: return
        runCatching { activeTrack.pause() }.onFailure { }
        runCatching { activeTrack.flush() }.onFailure { }
        runCatching { backend.seek(sourcePositionUs.toULong()) }
            .onFailure { failure ->
                setFailure(streamGeneration, failure.message ?: "Standard seek failed")
            }
        downsampler?.reset()
        positionBaseFrames = usToFrames(sourcePositionUs)
        framesWritten = 0L
        pendingData = null
        pendingOffset = 0
        sourceEos = false
        resetHeadCounters()
        anchorTo(sourcePositionUs, playing = false)
        if (wasPlaying && rendererStarted) {
            startedGeneration = -1L
            startLocked(streamGeneration)
        }
    }

    private fun releaseSegmentLocked() {
        pumpPlaying = false
        pumpHandler.removeCallbacks(pumpRunnable)
        startedGeneration = -1L
        preparedGeneration = -1L
        preparedMediaId = null
        outputEnded = false
        anchorPlaying = false
        val activeTrack = track
        track = null
        if (activeTrack != null) {
            runCatching { activeTrack.pause() }.onFailure { }
            runCatching { activeTrack.flush() }.onFailure { }
            runCatching { activeTrack.release() }.onFailure { }
        }
        runCatching { backend.releaseSource() }.onFailure { }
        downsampler = null
        framesWritten = 0L
        pendingData = null
        pendingOffset = 0
        positionBaseFrames = 0L
        durationUs = 0L
        sourceEos = false
        resetHeadCounters()
    }
}
