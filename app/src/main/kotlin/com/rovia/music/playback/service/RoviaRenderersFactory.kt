package com.rovia.music.playback.service

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Rovia's renderers factory.
 *
 * It ONLY adds the audio renderer the caller passes in — the
 * Standard, Direct USB, DAP or Native A14 renderer — each driven by a
 * coordinator that decodes with the registered decoder backend
 * (Symphonia, id "symphonia-rust").
 *
 * It NEVER calls `super.buildAudioRenderers`: that is the call that
 * used to build the platform MediaCodec audio renderer, and there is
 * no platform decoder in Rovia any more. If no renderer is passed in,
 * no audio renderer is added at all — audio simply has no renderer,
 * rather than silently falling back to the platform.
 *
 * The class still extends [DefaultRenderersFactory] only so ExoPlayer
 * keeps its non-audio renderers (video/text/metadata, which Rovia does
 * not use for audio playback). Audio renderers are never built by the
 * platform here.
 */
internal class RoviaRenderersFactory(
    context: Context,
    private val exclusiveAudioRenderer: Renderer? = null,
) : DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        // Deliberately no super.buildAudioRenderers() call: the platform
        // audio renderer (and its MediaCodec decoders) must never be
        // built. The exclusive renderer is the only audio renderer.
        exclusiveAudioRenderer?.let { out.add(it) }
    }
}
