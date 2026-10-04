package com.rovia.music.feature.player

import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.SyncedLyrics

data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState(),
    val lyrics: SyncedLyrics? = null,
    val isLyricsLoading: Boolean = false,
    val isLyricsVisible: Boolean = false,
)