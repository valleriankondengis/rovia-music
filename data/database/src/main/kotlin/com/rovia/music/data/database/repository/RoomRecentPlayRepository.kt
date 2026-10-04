package com.rovia.music.data.database.repository

import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.RecentPlayRepository
import com.rovia.music.data.database.dao.RecentPlayDao
import com.rovia.music.data.database.mapper.toRecentPlayEntity
import com.rovia.music.data.database.mapper.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomRecentPlayRepository(
    private val dao: RecentPlayDao,
    scope: CoroutineScope,
) : RecentPlayRepository {

    override val recentPlays: StateFlow<List<Track>> =
        dao.observeRecentPlays()
            .map { entities ->
                entities.map { entity ->
                    entity.toTrack()
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    override suspend fun record(
        track: Track,
    ) {
        dao.record(
            track.toRecentPlayEntity(
                lastPlayedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}