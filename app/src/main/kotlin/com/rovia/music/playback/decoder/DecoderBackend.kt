package com.rovia.music.playback.decoder

import com.rovia.music.uniffi.SourceInfoDto

/**
 * The only contract playback code ever talks to for decoding.
 *
 * A decoder backend turns a seekable file descriptor into decoded PCM
 * blocks. It knows nothing about ExoPlayer, MediaSession, AudioTrack, USB
 * or the UI: coordinators own the output route, this interface owns the
 * decode. Adding a decoder means implementing this interface and
 * registering it in [DecoderRegistry] — nothing else in playback, the
 * queue or the UI changes.
 *
 * The current backend is Symphonia ([SymphoniaDecoderBackend]).
 * Backend ids follow the <decoder>-<language/tech> rule (locked
 * 2026-11-21): the Symphonia backend's canonical id is
 * "symphonia-rust", a future FFmpeg backend would be "ffmpeg-c".
 * The interface deliberately carries no Symphonia, platform-decoder or
 * MediaCodec concept, so Symphonia can be replaced wholesale by a
 * different backend without migrating the project.
 */

/** A failure raised by a decoder backend. The message is user-visible. */
class DecoderFailure(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Whether a backend can decode a given container/codec pair. */
sealed class DecoderSupport {
    /** The backend can decode this pair. */
    object Supported : DecoderSupport()

    /** The backend cannot decode this pair, with a human-readable reason. */
    data class Unsupported(val codec: String, val reason: String) : DecoderSupport()
}

/**
 * What a decoder reports about a source it opened. This is the single
 * source of truth for the audio pipeline UI ([PlaybackDecoderStore]):
 * sample rate, bit depth and codec are never taken from a platform
 * metadata scan.
 */
data class DecodedSourceInfo(
    val backendId: String,
    val backendDisplayName: String,
    val container: String,
    val codec: String,
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val durationUs: Long,
    val totalFrames: Long,
    /** True for FLAC / ALAC / PCM: the decoded PCM is sample-exact. */
    val lossless: Boolean,
    /**
     * True only when the decoded PCM can be delivered sample-exact to a
     * verified route. Lossy sources decode to exact decoded PCM, but that
     * PCM is not the pre-encode source, so they are never bit-perfect.
     */
    val bitPerfectEligible: Boolean,
) {
    /** Label for the pipeline view, e.g. "Symphonia 0.6.1 (Rust) · AAC-LC". */
    fun decoderLabel(): String = "$backendDisplayName · $codec"

    companion object {
        fun fromSourceInfoDto(
            backendId: String,
            backendDisplayName: String,
            dto: SourceInfoDto,
        ): DecodedSourceInfo {
            val codec = dto.codec
            val lossless = codec.contains("FLAC", ignoreCase = true) ||
                codec.contains("ALAC", ignoreCase = true) ||
                codec.contains("PCM", ignoreCase = true) ||
                dto.container.contains("WAV", ignoreCase = true) ||
                dto.container.contains("AIFF", ignoreCase = true)
            return DecodedSourceInfo(
                backendId = backendId,
                backendDisplayName = backendDisplayName,
                container = dto.container,
                codec = codec,
                sampleRateHz = dto.sampleRate.toInt(),
                channels = dto.channels.toInt(),
                bitsPerSample = dto.bitsPerSample.toInt(),
                durationUs = dto.durationUs.toLong(),
                totalFrames = dto.totalFrames.toLong(),
                lossless = lossless,
                bitPerfectEligible = lossless,
            )
        }
    }
}

/** One decoded PCM block from a backend. */
data class DecodedBlock(
    val data: ByteArray,
    val frames: Long,
    val eos: Boolean,
)

interface DecoderBackend : AutoCloseable {
    val id: String
    val displayName: String

    /** Returns [DecoderSupport.Supported] or an explicit unsupported reason. */
    fun supports(container: String?, codec: String?): DecoderSupport

    /**
     * Opens [fileDescriptor] (a duplicated, seekable fd; the backend owns
     * it after this call) for the file whose name ends in [extensionHint].
     * Throws [DecoderFailure] when the source cannot be decoded.
     */
    fun openSource(fileDescriptor: Int, extensionHint: String): DecodedSourceInfo

    /** Decodes the next block, at most [maxBytes] of PCM. */
    fun readBlock(maxBytes: UInt): DecodedBlock

    /** Seeks the decoder; returns the actual position in microseconds. */
    fun seek(positionUs: ULong): ULong

    /** Releases the current source, if any, without closing the backend. */
    fun releaseSource()
}
