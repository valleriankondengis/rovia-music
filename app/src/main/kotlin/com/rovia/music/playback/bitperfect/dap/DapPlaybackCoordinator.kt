package com.rovia.music.playback.bitperfect.dap

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.rovia.music.uniffi.DapSession
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
private const val SHUTDOWN_TIMEOUT_MS = 3_000L
private const val MAX_PREPARE_ATTEMPTS = 3

internal class DapPlaybackCoordinator(
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

    private var session: DapSession? = null
    private var sessionDeviceId: Int = -1

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
                runCatching { sessionOrNull()?.pausePlayback() }.onFailure {}
                    .onFailure { failure ->

                    }
            }
        }
    }

    private suspend fun startLocked(streamGeneration: Long) {
        if (streamGeneration != generation.get()) return
        if (startedGeneration == streamGeneration) return
        runCatching { session().startPlayback() }.onFailure {}
            .onSuccess {
                startedGeneration = streamGeneration
                anchorPlaying = true
                anchorElapsedRealtimeMs = SystemClock.elapsedRealtime()
            }
            .onFailure { failure ->
                setFailure(
                    streamGeneration,
                    failure.message ?: "DAP playback could not start",
                )
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
                runCatching { session().seekPlayback(sourcePositionUs.toULong()) }.onFailure {}
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "DAP seek failed")
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
        val sourcePositionUs = if (anchorPlaying) {
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
                runCatching { session?.close() }.onFailure { }
                session = null
                sessionDeviceId = -1
            }
        }
    }

    private fun session(): DapSession {
        return session ?: run {
            val deviceId = resolveDeviceId(applicationContext)
            sessionDeviceId = deviceId
            DapSession(deviceId).also { created -> session = created }
        }
    }

    private fun refreshSessionDevice() {
        val existing = session ?: return
        val currentDeviceId = resolveDeviceId(applicationContext)
        if (currentDeviceId == sessionDeviceId) return

        runCatching { existing.close() }.onFailure { }
        session = null
        sessionDeviceId = -1
    }

    private fun sessionOrNull(): DapSession? = session

    private suspend fun pollLoop() {
        while (scope.isActive && rendererEnabled) {
            pollOnce()
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pollOnce() {
        val activeSession = sessionOrNull() ?: return
        val status = activeSession.playbackStatus()
        applyStatus(status)
        if (status.state == "Failed") {
            setFailure(
                generation.get(),
                status.errorMessage.ifBlank { "DAP playback failed" },
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
        var attempts = 0
        while (true) {
            val item = currentItem
            if (item == null) {
                setFailure(streamGeneration, "No current media item is available for DAP playback")
                return false
            }
            if (preparedGeneration == streamGeneration && preparedMediaId == item.mediaId) {
                return true
            }
            if (streamGeneration != generation.get()) return false
            refreshSessionDevice()
            val activeSession = session()
            val prepared = try {
                val descriptor = applicationContext.contentResolver.openFileDescriptor(item.uri, "r")
                    ?: throw IllegalStateException("The media source could not be opened")
                val fileDescriptor = descriptor.detachFd()
                val info = activeSession.openSource(fileDescriptor, item.extensionHint)
                PlaybackDecoderStore.setDecoderInfo(
                    DecodedSourceInfo.fromSourceInfoDto(
                        DecoderRegistry.defaultBackendId(),
                        DecoderRegistry.defaultBackendDisplayName(),
                        info,
                    ),
                )

                activeSession.preparePlayback()
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                preparedGeneration = -1L
                preparedMediaId = null
                setFailure(
                    streamGeneration,
                    failure.message ?: "The media source is not supported by DAP",
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
            PlaybackPipelineStore.set(
                PipelineInfo(
                    mode = AudioOutputMode.DAP,
                    dapVerifiedRateHz = prepared.sampleRate.toInt(),
                    dapVerifiedChannels = prepared.channels.toInt(),
                    dapVerifiedBits = prepared.validBits.toInt(),
                ),
            )
            val startSourceUs = pendingStartSourceUs
            if (startSourceUs > 0L) {
                runCatching { activeSession.seekPlayback(startSourceUs.toULong()) }.onFailure {}
                    .onFailure { failure ->
                        setFailure(streamGeneration, failure.message ?: "DAP seek failed")
                        return false
                    }
                anchorTo(startSourceUs, playing = false)
            }
            return true
        }
    }

    private suspend fun stopAndReleaseLocked() {
        startedGeneration = -1L
        preparedGeneration = -1L
        preparedMediaId = null
        PlaybackPipelineStore.clear()
        outputEnded = false
        anchorPlaying = false
        val activeSession = sessionOrNull() ?: return
        runCatching { activeSession.stopPlayback() }.onFailure {}
            .onFailure {  }
        runCatching { activeSession.releaseSource() }.onFailure {}
            .onFailure {  }
    }

    companion object {
        private fun resolveDeviceId(context: Context): Int {
            val manager = context.getSystemService(AudioManager::class.java) ?: return 0
            val usb = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            return usb?.id ?: 0
        }
    }
}
