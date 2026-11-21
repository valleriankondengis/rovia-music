package com.rovia.music.playback.decoder

import com.rovia.music.uniffi.SourceInfoDto


class DecoderFailure(message: String, cause: Throwable? = null) :
    Exception(message, cause)

sealed class DecoderSupport {
    object Supported : DecoderSupport()

    data class Unsupported(val codec: String, val reason: String) : DecoderSupport()
}

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
    val lossless: Boolean,
    val bitPerfectEligible: Boolean,
) {
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

data class DecodedBlock(
    val data: ByteArray,
    val frames: Long,
    val eos: Boolean,
)

interface DecoderBackend : AutoCloseable {
    val id: String
    val displayName: String

    fun supports(container: String?, codec: String?): DecoderSupport

    fun openSource(fileDescriptor: Int, extensionHint: String): DecodedSourceInfo

    fun readBlock(maxBytes: UInt): DecodedBlock

    fun seek(positionUs: ULong): ULong

    fun releaseSource()
}
