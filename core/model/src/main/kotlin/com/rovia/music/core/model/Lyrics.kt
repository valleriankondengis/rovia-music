package com.rovia.music.core.model

data class SyncedLyrics(
    val lines: List<LyricLine>,
)

data class LyricLine(
    val startTimeMs: Long,
    val words: List<LyricWord>,
)

data class LyricWord(
    val startTimeMs: Long,
    val text: String,
)