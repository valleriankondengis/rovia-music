package com.rovia.music.playback.decoder

import com.rovia.music.uniffi.NativePcmSource

/**
 * The Symphonia decoder backend: pure-Rust decoding via the UniFFI
 * [NativePcmSource] (Symphonia 0.6.1, pinned by Git revision).
 *
 * Supported containers: WAV, FLAC, AIFF, CAF, ISO/MP4 (M4A/MP4), OGG,
 * Matroska/WebM. Supported codecs: PCM, FLAC, ALAC, AAC-LC, MP1/MP2/MP3,
 * Vorbis, ADPCM. Container support and codec support are separate facts:
 * a container can carry a codec this backend cannot decode.
 *
 * Explicitly NOT supported (sacrificed 2026-11-21, see
 * docs/media3/DECODER_POLICY.md): HE-AAC and HE-AACv2 — Symphonia has no
 * SBR decoder, so it can at best decode the AAC-LC core at half the
 * sample rate, which this backend does not pretend is full playback.
 * Also unsupported: AAC-ELD, xHE-AAC, Opus in MP4, DSD, WavPack, APE
 * audio, WMA and AC-3/E-AC-3. Those fail with an explicit reason instead
 * of silently skipping or falling back to a platform decoder.
 */
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
        // The Rust side already rejected unsupported codecs at open; a
        // successful open is a supported codec by construction.
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
        // Canonical backend id, following the <decoder>-<language/tech>
        // naming rule (locked 2026-11-21): "symphonia-rust", never the
        // bare "symphonia". A future FFmpeg backend would be "ffmpeg-c".
        // The UI label ("Symphonia (Rust)") is the spelled-out form of
        // the same id, not a second identifier.
        const val BACKEND_ID: String = "symphonia-rust"
        const val DISPLAY_NAME: String = "Symphonia 0.6.1 (Rust)"

        private val SUPPORTED_CODECS: Set<String> = setOf(
            "PCM", "FLAC", "ALAC", "AAC", "AAC-LC", "MP3", "MP1", "MP2",
            "VORBIS", "ADPCM",
        )

        /** Codec -> reason it is unsupported by Symphonia. */
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
                // No codec claimed yet: let the probe decide at open.
                return DecoderSupport.Supported
            }
            unsupportedReason(key)?.let { reason ->
                return DecoderSupport.Unsupported(codec = codec!!, reason = reason)
            }
            if (key in SUPPORTED_CODECS || SUPPORTED_CODECS.any { key.contains(it) }) {
                return DecoderSupport.Supported
            }
            // Unknown codec: do not claim support; the probe at open is
            // the authority and will fail explicitly if it cannot decode.
            return DecoderSupport.Unsupported(
                codec = codec!!,
                reason = "$codec is not supported by Symphonia",
            )
        }
    }
}
