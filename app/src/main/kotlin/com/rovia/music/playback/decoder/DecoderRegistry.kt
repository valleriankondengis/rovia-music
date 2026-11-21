package com.rovia.music.playback.decoder

object DecoderRegistry {

    data class BackendFactory(
        val id: String,
        val displayName: String,
        val create: () -> DecoderBackend,
        val supports: (container: String?, codec: String?) -> DecoderSupport,
    )

    private val factories = mutableListOf<BackendFactory>()
    private var defaultFactory: BackendFactory? = null

    init {
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

    fun defaultBackend(): DecoderBackend =
        (defaultFactory ?: error("No decoder backend is registered")).create()

    fun defaultBackendId(): String =
        defaultFactory?.id ?: error("No decoder backend is registered")

    fun defaultBackendDisplayName(): String =
        defaultFactory?.displayName ?: error("No decoder backend is registered")

    fun backendIds(): List<String> = factories.map { it.id }

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
