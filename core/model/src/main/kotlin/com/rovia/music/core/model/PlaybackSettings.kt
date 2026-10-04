package com.rovia.music.core.model

data class PlaybackSettings(
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
)