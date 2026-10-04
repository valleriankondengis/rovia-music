package com.rovia.music.core.library

import com.rovia.music.core.model.Track

interface MusicRepository {

    suspend fun getRecentlyAdded(
        limit: Int,
    ): List<Track>

    suspend fun getAllTracks(): List<Track>
}