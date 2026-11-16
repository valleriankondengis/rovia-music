package com.rovia.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object PlaybackDecoderStore {
    private val mutableDecoderName = MutableStateFlow<String?>(null)
    val decoderName: StateFlow<String?> = mutableDecoderName.asStateFlow()

    fun setDecoderName(name: String?) {
        mutableDecoderName.value = name
    }
}
