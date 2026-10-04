package com.rovia.music.core.playback

import com.rovia.music.core.model.Track
import kotlinx.coroutines.flow.StateFlow

interface RecentPlayRepository {

    val recentPlays: StateFlow<List<Track>>

    suspend fun record(
        track: Track,
    )
}