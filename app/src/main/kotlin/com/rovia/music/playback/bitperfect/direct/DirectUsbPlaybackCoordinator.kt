package com.rovia.music.playback.bitperfect.direct

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.rovia.music.playback.bitperfect.AudioOutputModeStoreProvider
import com.rovia.music.uniffi.PlaybackStatusDto
import com.rovia.music.playback.queue.PlaybackMetadataKeys
import com.rovia.music.playback.decoder.DecoderRegistry
import com.rovia.music.playback.decoder.DecodedSourceInfo
import com.rovia.music.playback.PlaybackDecoderStore
import com.rovia.music.playback.PipelineInfo
import com.rovia.music.playback.PlaybackPipelineStore
import com.rovia.music.playback.bitperfect.AudioOutputMode
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
private const val SESSION_WAIT_TIMEOUT_MS = 2_000L
private const val SESSION_WAIT_POLL_MS = 100L
private const val SHUTDOWN_TIMEOUT_MS = 3_000L
private const val MAX_PREPARE_ATTEMPTS = 3

internal class DirectUsbPlaybackCoordinator(
    context: Context,
    private val controller: DirectUsbController,
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
    private var volumeBlockedGeneration: Long = -1L

    @Volatile
    private var sessionGenerationSeen: Long = -1L

    @Volatile
    private var lastStatus: PlaybackStatusDto? = null

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
                    stopAndReleaseLocked()
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
                runCatching { controller.playbackPause() }
                    .onFailure { failure ->

                    }
            }
        }
    }

    private fun volumeGateBlocks(streamGeneration: Long): Boolean {
        if (preparedGeneration != streamGeneration) return false
        val volume = controller.playbackDacVolume() ?: return false
        if (volume.available) return false
        return !AudioOutputModeStoreProvider.get(applicationContext).allowFixedVolume.value
    }

    private suspend fun startLocked(streamGeneration: Long) {
        if (streamGeneration != generation.get()) return
        if (startedGeneration == streamGeneration) return
        if (volumeGateBlocks(streamGeneration)) {
            volumeBlockedGeneration = streamGeneration
            return
        }
        volumeBlockedGeneration = -1L
        runCatching { controller.playbackStart() }
            .onSuccess {
                startedGeneration = streamGeneration
                anchorPlaying = true
                anchorElapsedRealtimeMs = SystemClock.elapsedRealtime()
            }
            .onFailure { failure ->
                setFailure(
                    streamGeneration,
                    failure.message ?: "Direct USB playback could not start",
                )
            }
    }

    fun onRendererPositionReset(positionUs: Long) {
        val sourcePositionUs = (positionUs - streamOffsetUs).coerceAtLeast(0L)
        val streamGeneration = generation.get()
        if (preparedGeneration == streamGeneration && preparedMediaId != null) {
            return
        }
        pendingStartSourceUs = sourcePositionUs
        anchorTo(sourcePositionUs, playing = anchorPlaying)
        scope.launch {
            commandMutex.withLock {
                if (preparedGeneration != streamGeneration) return@withLock
                runCatching { controller.playbackSeek(sourcePositionUs) }
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "Direct USB seek failed")
                    }
            }
        }
    }

    fun onConfirmedSeek(mediaId: String, sourcePositionUs: Long) {
        val streamGeneration = generation.get()
        if (preparedMediaId != mediaId) return
        pendingStartSourceUs = sourcePositionUs
        anchorTo(sourcePositionUs, playing = anchorPlaying)
        scope.launch {
            commandMutex.withLock {
                if (preparedGeneration != streamGeneration) return@withLock
                if (preparedMediaId != mediaId) return@withLock
                runCatching { controller.playbackSeek(sourcePositionUs) }
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "Direct USB seek failed")
                    }
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
                stopAndReleaseLocked()
            }
        }
    }

    suspend fun shutdownEngineAndWait() {
        val deferred = scope.async {
            commandMutex.withLock {
                stopAndReleaseLocked()
            }
        }
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { deferred.await() }
    }

    fun clockPositionUs(): Long {
        val sourcePositionUs = if (preparedGeneration != generation.get()) {
            pendingStartSourceUs
        } else if (anchorPlaying) {
            val elapsedUs = (SystemClock.elapsedRealtime() - anchorElapsedRealtimeMs)
                .coerceAtLeast(0L) * 1_000L
            val estimated = anchorSourcePositionUs + elapsedUs
            val durationUs = lastStatus?.durationUs?.toLong() ?: 0L
            if (durationUs > 0L) minOf(estimated, durationUs) else estimated
        } else {
            anchorSourcePositionUs
        }
        return streamOffsetUs + sourcePositionUs.coerceAtLeast(0L)
    }

    fun isReady(): Boolean {
        if (failureGeneration == generation.get()) return false
        if (outputEnded) return true
        if (preparedGeneration != generation.get()) return false
        val status = lastStatus ?: return false
        return status.state == "Prepared" ||
            status.state == "Playing" ||
            status.state == "Paused" ||
            status.state == "Ended"
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
                stopAndReleaseLocked()
            }
        }
    }

    private suspend fun pollLoop() {
        while (scope.isActive && rendererEnabled) {
            pollOnce()
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pollOnce() {
        val blockedGeneration = volumeBlockedGeneration
        if (blockedGeneration >= 0L &&
            blockedGeneration == generation.get() &&
            rendererStarted &&
            failureGeneration != blockedGeneration
        ) {
            scope.launch {
                commandMutex.withLock {
                    if (generation.get() != blockedGeneration) return@withLock
                    if (volumeGateBlocks(blockedGeneration)) return@withLock
                    volumeBlockedGeneration = -1L
                    startLocked(blockedGeneration)
                }
            }
        }
        val status = controller.playbackStatus()
        if (status == null) {
            if (preparedGeneration >= 0L) {
                preparedGeneration = -1L
                startedGeneration = -1L
                setFailure(generation.get(), "Direct USB session was disconnected")
            }
            return
        }
        applyStatus(status)
        if (status.state == "Failed") {
            setFailure(
                generation.get(),
                status.errorMessage.ifBlank { "Direct USB playback failed" },
            )
        }
    }

    private fun applyStatus(status: PlaybackStatusDto) {
        lastStatus = status
        outputEnded = status.outputEos
        anchorTo(
            sourcePositionUs = status.positionUs.toLong(),
            playing = status.state == "Playing",
        )
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
        PlaybackPipelineStore.clear()

    }

    private suspend fun ensurePreparedLocked(streamGeneration: Long): Boolean {
        if (failureGeneration == streamGeneration) return false
        val sessionGeneration = controller.currentSessionGeneration()
        if (sessionGeneration != sessionGenerationSeen) {
            sessionGenerationSeen = sessionGeneration
            preparedGeneration = -1L
            preparedMediaId = null
            startedGeneration = -1L
        }
        var attempts = 0
        while (true) {
            val item = currentItem
            if (item == null) {
                setFailure(
                    streamGeneration,
                    "No current media item is available for Direct USB playback",
                )
                return false
            }
            if (preparedGeneration == streamGeneration && preparedMediaId == item.mediaId) {
                return true
            }
            if (!controller.hasRetainedSession() && !awaitSessionLocked(streamGeneration)) {
                return false
            }
            if (streamGeneration != generation.get()) return false
            val prepared = try {
                val descriptor = applicationContext.contentResolver.openFileDescriptor(item.uri, "r")
                    ?: throw IllegalStateException("The media source could not be opened")
                val fileDescriptor = descriptor.detachFd()
                val info = controller.playbackOpenSource(fileDescriptor, item.extensionHint)
                PlaybackDecoderStore.setDecoderInfo(
                    DecodedSourceInfo.fromSourceInfoDto(
                        DecoderRegistry.defaultBackendId(),
                        DecoderRegistry.defaultBackendDisplayName(),
                        info,
                    ),
                )

                val status = controller.playbackPrepare()
                status
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                preparedGeneration = -1L
                preparedMediaId = null
                setFailure(
                    streamGeneration,
                    failure.message ?: "The media source is not supported by Direct USB",
                )
                return false
            }
            if (streamGeneration != generation.get()) {
                stopAndReleaseLocked()
                return false
            }
            val latest = currentItem
            if (latest != null && latest.mediaId != item.mediaId && attempts < MAX_PREPARE_ATTEMPTS) {
                attempts++

                stopAndReleaseLocked()
                continue
            }
            preparedGeneration = streamGeneration
            preparedMediaId = item.mediaId
            applyStatus(prepared)
            val dacVolume = controller.playbackDacVolume()
            val deviceItem = controller.state.value.let { state ->
                state.devices.firstOrNull { it.deviceId == state.selectedDeviceId }
                    ?: state.devices.firstOrNull()
            }
            PlaybackPipelineStore.set(
                PipelineInfo(
                    mode = AudioOutputMode.DIRECT_USB,
                    usbNegotiatedRateHz = prepared.sampleRate.toInt(),
                    usbNegotiatedChannels = prepared.channels.toInt(),
                    usbNegotiatedBits = prepared.validBits.toInt(),
                    usbUacVersion = controller.activeDeviceUacVersion(),
                    usbVendorId = deviceItem?.vendorId,
                    usbProductId = deviceItem?.productId,
                    usbManufacturer = deviceItem?.manufacturerName?.takeIf { it.isNotBlank() },
                    usbDeviceName = deviceItem?.displayName,
                    dacVolumeAvailable = dacVolume?.available == true,
                    dacVolumeMinDb = dacVolume?.minDb,
                    dacVolumeMaxDb = dacVolume?.maxDb,
                    dacVolumeCurrentDb = dacVolume?.currentDb,
                    fixedVolumeAllowed = AudioOutputModeStoreProvider.get(applicationContext).allowFixedVolume.value,
                ),
            )
            val startSourceUs = pendingStartSourceUs
            if (startSourceUs > 0L) {
                runCatching { controller.playbackSeek(startSourceUs) }
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "Direct USB seek failed")
                        return false
                    }
                anchorTo(startSourceUs, playing = false)
            }
            return true
        }
    }

    private suspend fun awaitSessionLocked(streamGeneration: Long): Boolean {
        var waitedMs = 0L
        while (!controller.hasRetainedSession()) {
            if (streamGeneration != generation.get() || !rendererEnabled) return false
            if (waitedMs >= SESSION_WAIT_TIMEOUT_MS) {
                setFailure(streamGeneration, "Direct USB session is not connected")
                return false
            }
            delay(SESSION_WAIT_POLL_MS)
            waitedMs += SESSION_WAIT_POLL_MS
        }
        return true
    }

    private suspend fun stopAndReleaseLocked() {
        volumeBlockedGeneration = -1L
        startedGeneration = -1L
        preparedGeneration = -1L
        PlaybackPipelineStore.clear()
        preparedMediaId = null
        outputEnded = false
        anchorPlaying = false
        runCatching { controller.playbackStop() }
            .onFailure {  }
        runCatching { controller.playbackReleaseSource() }
            .onFailure {  }
    }
}
