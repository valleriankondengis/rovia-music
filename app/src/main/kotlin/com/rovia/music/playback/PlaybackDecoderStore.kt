package com.rovia.music.playback

import com.rovia.music.playback.decoder.DecodedSourceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The decoded-source state for the audio pipeline UI.
 *
 * This store is fed ONLY by decoder backends (via the coordinators):
 * [setDecoderInfo] on a successful open/prepare, [setFailure] when a
 * source cannot be decoded. It never holds a platform decoder name —
 * there is no platform decoder in Rovia any more; every mode decodes
 * with the registered decoder backend (Symphonia by default).
 */
internal object PlaybackDecoderStore {
    private val mutableDecoderInfo = MutableStateFlow<DecodedSourceInfo?>(null)
    val decoderInfo: StateFlow<DecodedSourceInfo?> = mutableDecoderInfo.asStateFlow()

    private val mutableLastFailure = MutableStateFlow<String?>(null)
    val lastFailure: StateFlow<String?> = mutableLastFailure.asStateFlow()

    fun setDecoderInfo(info: DecodedSourceInfo?) {
        mutableDecoderInfo.value = info
        if (info != null) {
            mutableLastFailure.value = null
        }
    }

    fun setFailure(message: String?) {
        mutableLastFailure.value = message?.takeIf(String::isNotBlank)
    }

    fun clear() {
        mutableDecoderInfo.value = null
        mutableLastFailure.value = null
    }
}
