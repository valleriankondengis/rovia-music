package com.rovia.music.core.playback

import com.rovia.music.core.model.PlaybackSettings
import com.rovia.music.core.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

interface PlaybackSettingsRepository {

    val settings: StateFlow<PlaybackSettings>

    suspend fun setRepeatMode(
        repeatMode: RepeatMode,
    )

    suspend fun setShuffleEnabled(
        enabled: Boolean,
    )
}