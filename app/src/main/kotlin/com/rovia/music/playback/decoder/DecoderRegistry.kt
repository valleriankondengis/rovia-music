package com.rovia.music.playback.decoder

/**
 * The single registration point for decoder backends.
 *
 * This is the ONLY place a backend is added to the app. Playback code
 * (coordinators, [PlaybackService], the UI) never names Symphonia or any
 * other backend directly: it asks the registry for the default backend
 * or resolves a container/codec pair here.
 *
 * To add a decoder:
 *  1. Implement [DecoderBackend] (see [SymphoniaDecoderBackend]).
 *  2. Call [registerBackend] with a factory for it, once, at startup.
 *  3. Optionally pass `makeDefault = true` to replace Symphonia as the
 *     default. No coordinator, service or UI file changes.
 *
 * To replace Symphonia entirely: register a different backend as the
 * default and delete [SymphoniaDecoderBackend]; the registry default is
 * the only other place Symphonia is named.
 *
 * Backend ids follow the <decoder>-<language/tech> rule (locked
 * 2026-11-21): "symphonia-rust" for this backend, "ffmpeg-c" for a
 * future FFmpeg backend. Never register a bare decoder name such as
 * "symphonia" — the technology suffix is what keeps multi-decoder
 * ids unambiguous.
 *
 * Every [defaultBackend] call returns a FRESH backend instance, because
 * each coordinator owns its own decode state (one NativePcmSource per
 * coordinator). Backends are never shared between coordinators.
 */
object DecoderRegistry {

    /** A registered backend: how to create it and how to ask it. */
    data class BackendFactory(
        val id: String,
        val displayName: String,
        val create: () -> DecoderBackend,
        val supports: (container: String?, codec: String?) -> DecoderSupport,
    )

    private val factories = mutableListOf<BackendFactory>()
    private var defaultFactory: BackendFactory? = null

    init {
        // The built-in default. This is one of the two places in the app
        // (with SymphoniaDecoderBackend itself) that names Symphonia.
        registerBackend(
            BackendFactory(
                id = SymphoniaDecoderBackend.BACKEND_ID,
                displayName = SymphoniaDecoderBackend.DISPLAY_NAME,
                create = { SymphoniaDecoderBackend() },
                supports = { container, codec ->
                    SymphoniaDecoderBackend.supportsCodec(container, codec)
                },
            ),
            makeDefault = true,
        )
    }

    fun registerBackend(factory: BackendFactory, makeDefault: Boolean = false) {
        factories.removeAll { it.id == factory.id }
        factories.add(factory)
        if (makeDefault || defaultFactory == null) {
            defaultFactory = factory
        }
    }

    /** A fresh instance of the default backend. Callers own it. */
    fun defaultBackend(): DecoderBackend =
        (defaultFactory ?: error("No decoder backend is registered")).create()

    fun defaultBackendId(): String =
        defaultFactory?.id ?: error("No decoder backend is registered")

    fun defaultBackendDisplayName(): String =
        defaultFactory?.displayName ?: error("No decoder backend is registered")

    /** All registered backend ids, in registration order. */
    fun backendIds(): List<String> = factories.map { it.id }

    /**
     * The first registered backend that supports this pair, or the
     * unsupported reason of the default backend when none does.
     */
    fun resolve(container: String?, codec: String?): DecoderSupport {
        for (factory in factories) {
            val support = factory.supports(container, codec)
            if (support is DecoderSupport.Supported) return support
        }
        val fallback = defaultFactory?.supports?.invoke(container, codec)
        return fallback ?: DecoderSupport.Unsupported(
            codec = codec.orEmpty(),
            reason = "No decoder backend is registered for $codec",
        )
    }

    fun supportedCodecs(): List<String> =
        SymphoniaDecoderBackend.supportedCodecs()

    fun unsupportedReason(codec: String?): String? =
        SymphoniaDecoderBackend.unsupportedReason(codec)

    /**
     * Probes a source with the default backend and releases it. Returns
     * the decoded source info, or null when the source cannot be decoded.
     * Used for format inspection only; playback opens its own backend.
     */
    fun probe(fileDescriptor: Int, extensionHint: String): DecodedSourceInfo? {
        val backend = defaultBackend()
        return try {
            backend.openSource(fileDescriptor, extensionHint)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { backend.close() }.onFailure { }
        }
    }
}
