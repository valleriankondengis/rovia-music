package com.rovia.music.playback.bitperfect.a14

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.rovia.music.uniffi.NativePcmSource
import com.rovia.music.playback.queue.PlaybackMetadataKeys
import com.rovia.music.playback.decoder.DecoderRegistry
import com.rovia.music.playback.decoder.DecodedSourceInfo
import com.rovia.music.playback.PlaybackDecoderStore
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
// 32 KiB: a decoded block must fit the AudioTrack buffer (about 200 ms,
// e.g. ~53 KiB for 48 kHz stereo 24-bit) so one block normally drains in
// a single pump run. A partial write is still harmless: the pump keeps
// the remainder (see pendingData) and never seeks to recover it.
private const val READ_BLOCK_BYTES = 32_768
private const val MAX_WRITE_ATTEMPTS = 8
private const val PUMP_RETRY_DELAY_MS = 5L

internal class NativeA14PlaybackCoordinator(
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

    private val source = NativePcmSource()
    private val mixer: NativeA14MixerController? =
        if (Build.VERSION.SDK_INT >= 34) {
            NativeA14MixerController(context.applicationContext)
        } else {
            null
        }

    private val pumpThread = HandlerThread("RoviaNativeA14Pump").apply { start() }
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
    private var selection: NativeA14MixerController.Selection? = null

    @Volatile
    private var pumpPlaying: Boolean = false

    @Volatile
    private var sourceEos: Boolean = false

    @Volatile
    private var framesWritten: Long = 0L

    // Remainder of the current decoded block not yet written to the
    // AudioTrack. The pump owns this: a block is never discarded, and a
    // partial write never seeks the decoder. (2026-11-21: the old
    // reanchorAfterPartialWrite sought to positionBaseFrames + the
    // absolute playback head, without flushing, so the head was counted
    // twice on every partial write and the decoder raced ahead — the
    // song played in fast-forward jumps. A 64 KiB block is larger than
    // the ~53 KiB AudioTrack buffer, so partial writes happened on
    // every block.)
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
                // Finish the pending remainder before decoding more: the
                // decoder only advances when the previous block is fully
                // written, so no audio is ever skipped or repeated.
                var data = pendingData
                var offset = pendingOffset
                if (data == null) {
                    val block = source.readBlock(READ_BLOCK_BYTES.toUInt())
                    if (block.data.isNotEmpty()) {
                        data = block.data
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
                                "Native A14 AudioTrack write failed: $written",
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
                        // Track buffer full: keep the remainder for the
                        // next pump run. Never discard it, never seek.
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
                    failure.message ?: "Native A14 PCM source failed",
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
                mixer?.clear()
                selection = null
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
                mixer?.clear()
                selection = null
                runCatching { source.close() }.onFailure { }
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
        val activeSelection = selection
        if (activeSelection != null) {
            val routedId = runCatching { activeTrack.routedDevice?.id }.getOrNull()
            if (routedId != null && routedId != activeSelection.device.id) {
                setFailure(generation.get(), "Native A14 audio route changed")
                return
            }
            val controller = mixer
            if (controller != null && !controller.isSelectionIntact(activeSelection)) {
                setFailure(generation.get(), "Native A14 mixer attributes changed")
                return
            }
        }
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
        val controller = mixer
        if (controller == null) {
            setFailure(streamGeneration, "Native A14 requires Android 14 or newer")
            return false
        }
        var attempts = 0
        while (true) {
            val item = currentItem
            if (item == null) {
                setFailure(streamGeneration, "No current media item is available for Native A14 playback")
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
                source.openSource(fileDescriptor, item.extensionHint)
                // Publish the decoded source (decoder id comes from the
                // registry, never a hardcoded backend name here).
                .also { info ->
                    PlaybackDecoderStore.setDecoderInfo(
                        DecodedSourceInfo.fromSourceInfoDto(
                            DecoderRegistry.defaultBackendId(),
                            DecoderRegistry.defaultBackendDisplayName(),
                            info,
                        ),
                    )
                }
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                setFailure(
                    streamGeneration,
                    failure.message ?: "The media source is not supported by Native A14",
                )
                return false
            }

            val encoding = when (info.bitsPerSample.toInt()) {
                16 -> AudioFormat.ENCODING_PCM_16BIT
                24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                32 -> AudioFormat.ENCODING_PCM_32BIT
                else -> {
                    setFailure(
                        streamGeneration,
                        "Native A14 does not support a ${info.bitsPerSample}-bit source",
                    )
                    return false
                }
            }
            val channelMask = when (info.channels.toInt()) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                else -> {
                    setFailure(
                        streamGeneration,
                        "Native A14 does not support ${info.channels} channels",
                    )
                    return false
                }
            }
            val chosen = controller.select(info.sampleRate.toInt(), channelMask, encoding)
            if (chosen == null) {
                setFailure(
                    streamGeneration,
                    "Native A14 has no BIT_PERFECT route for this track",
                )
                return false
            }
            selection = chosen
            val format = chosen.attributes.format
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                else -> 4
            }
            sampleRate = format.sampleRate
            frameBytes = info.channels.toInt() * bytesPerSample
            durationUs = info.durationUs.toLong()
            val minBuffer = AudioTrack.getMinBufferSize(
                format.sampleRate,
                format.channelMask,
                format.encoding,
            )
            val targetBuffer = format.sampleRate / 5 * frameBytes
            val built = try {
                AudioTrack.Builder()
                    .setAudioAttributes(chosen.platformAttributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, targetBuffer))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                setFailure(
                    streamGeneration,
                    failure.message ?: "Native A14 AudioTrack could not be created",
                )
                return false
            }
            if (built.state != AudioTrack.STATE_INITIALIZED) {
                built.release()
                setFailure(streamGeneration, "Native A14 AudioTrack could not be initialized")
                return false
            }
            built.preferredDevice = chosen.device
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
                runCatching { source.seek(startSourceUs.toULong()) }.onFailure {}
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "Native A14 seek failed")
                        return false
                    }
            }
            preparedGeneration = streamGeneration
            preparedMediaId = item.mediaId
            anchorTo(startSourceUs, playing = false)
            return true
        }
    }

    private fun startLocked(streamGeneration: Long) {
        if (streamGeneration != generation.get()) return
        if (startedGeneration == streamGeneration) return
        val activeTrack = track
        val activeSelection = selection
        val controller = mixer
        if (activeTrack == null || activeSelection == null || controller == null) {
            setFailure(streamGeneration, "Native A14 output is not prepared")
            return
        }
        try {
            activeTrack.play()
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            setFailure(streamGeneration, failure.message ?: "Native A14 AudioTrack could not start")
            return
        }
        if (!controller.verify(activeSelection, activeTrack.routedDevice)) {
            runCatching { activeTrack.pause() }.onFailure { }
            setFailure(streamGeneration, "Native A14 bit-perfect route could not be verified")
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
        runCatching { source.seek(framesToUs(positionFrames).toULong()) }.onFailure { }
        positionBaseFrames = positionFrames
        framesWritten = 0L
        // The track was flushed, so any unwritten remainder is gone too.
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
        runCatching { source.seek(sourcePositionUs.toULong()) }
            .onFailure { failure ->
                setFailure(streamGeneration, failure.message ?: "Native A14 seek failed")
            }
        positionBaseFrames = usToFrames(sourcePositionUs)
        framesWritten = 0L
        // The track was flushed, so any unwritten remainder is gone too.
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
        runCatching { source.releaseSource() }.onFailure { }
        framesWritten = 0L
        pendingData = null
        pendingOffset = 0
        positionBaseFrames = 0L
        durationUs = 0L
        sourceEos = false
        resetHeadCounters()
    }
}
