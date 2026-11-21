package com.rovia.music.playback.decoder

import com.rovia.music.uniffi.NativePcmSource

internal class SymphoniaDecoderBackend(
    private val source: NativePcmSource = NativePcmSource(),
) : DecoderBackend {

    override val id: String = BACKEND_ID
    override val displayName: String = DISPLAY_NAME

    override fun supports(container: String?, codec: String?): DecoderSupport =
        supportsCodec(container, codec)

    override fun openSource(
        fileDescriptor: Int,
        extensionHint: String,
    ): DecodedSourceInfo {
        val dto = try {
            source.openSource(fileDescriptor, extensionHint)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            throw DecoderFailure(
                "source_open: ${failure.message ?: "source is not supported by Symphonia"}",
                failure,
            )
        }
        return DecodedSourceInfo.fromSourceInfoDto(BACKEND_ID, DISPLAY_NAME, dto)
    }

    override fun readBlock(maxBytes: UInt): DecodedBlock {
        val block = try {
            source.readBlock(maxBytes)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            throw DecoderFailure(
                "source_decode: ${failure.message ?: "Symphonia decode failed"}",
                failure,
            )
        }
        return DecodedBlock(data = block.data, frames = block.frames.toLong(), eos = block.eos)
    }

    override fun seek(positionUs: ULong): ULong {
        return try {
            source.seek(positionUs)
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
            throw DecoderFailure(
                "source_seek: ${failure.message ?: "Symphonia seek failed"}",
                failure,
            )
        }
    }

    override fun releaseSource() {
        runCatching { source.releaseSource() }.onFailure { }
    }

    override fun close() {
        runCatching { source.releaseSource() }.onFailure { }
        runCatching { source.close() }.onFailure { }
    }

    companion object {
        const val BACKEND_ID: String = "symphonia-rust"
        const val DISPLAY_NAME: String = "Symphonia 0.6.1 (Rust)"

        private val SUPPORTED_CODECS: Set<String> = setOf(
            "PCM", "FLAC", "ALAC", "AAC", "AAC-LC", "MP3", "MP1", "MP2",
            "VORBIS", "ADPCM",
        )

        private val UNSUPPORTED_CODECS: Map<String, String> = mapOf(
            "HE-AAC" to "HE-AAC (SBR) is not supported by Symphonia",
            "HE-AACV1" to "HE-AAC (SBR) is not supported by Symphonia",
            "HE-AACV2" to "HE-AACv2 (SBR + parametric stereo) is not supported by Symphonia",
            "AAC-ELD" to "AAC-ELD is not supported by Symphonia",
            "XHE-AAC" to "xHE-AAC is not supported by Symphonia",
            "OPUS" to "Opus decoding is not supported by Symphonia in this build",
            "DSD" to "DSD is not supported by Symphonia",
            "DSF" to "DSD is not supported by Symphonia",
            "DFF" to "DSD is not supported by Symphonia",
            "WAVPACK" to "WavPack is not supported by Symphonia",
            "APE" to "APE audio decoding is not supported by Symphonia",
            "WMA" to "WMA is not supported by Symphonia",
            "AC-3" to "AC-3 is not supported by Symphonia",
            "AC3" to "AC-3 is not supported by Symphonia",
            "E-AC-3" to "E-AC-3 is not supported by Symphonia",
            "EAC3" to "E-AC-3 is not supported by Symphonia",
        )

        fun supportedCodecs(): List<String> = SUPPORTED_CODECS.sorted()

        fun unsupportedReason(codec: String?): String? {
            val key = codec?.trim()?.uppercase().orEmpty()
            if (key.isEmpty()) return null
            return UNSUPPORTED_CODECS[key]
                ?: UNSUPPORTED_CODECS.entries.firstOrNull { key.contains(it.key) }?.value
        }

        fun supportsCodec(container: String?, codec: String?): DecoderSupport {
            val key = codec?.trim()?.uppercase().orEmpty()
            if (key.isEmpty()) {
                return DecoderSupport.Supported
            }
            unsupportedReason(key)?.let { reason ->
                return DecoderSupport.Unsupported(codec = codec!!, reason = reason)
            }
            if (key in SUPPORTED_CODECS || SUPPORTED_CODECS.any { key.contains(it) }) {
                return DecoderSupport.Supported
            }
            return DecoderSupport.Unsupported(
                codec = codec!!,
                reason = "$codec is not supported by Symphonia",
            )
        }
    }
}
