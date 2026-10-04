package com.rovia.music.core.library

import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track

interface LyricsRepository {

    suspend fun getEmbeddedLyrics(
        track: Track,
    ): SyncedLyrics?
}