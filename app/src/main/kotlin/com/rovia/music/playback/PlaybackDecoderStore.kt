package com.rovia.music.playback

import com.rovia.music.playback.decoder.DecodedSourceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
