package com.rovia.music.data.database.repository

import com.rovia.music.core.model.PlaybackSettings
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.playback.PlaybackSettingsRepository
import com.rovia.music.data.database.dao.PlaybackSettingsDao
import com.rovia.music.data.database.entity.PlaybackSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomPlaybackSettingsRepository(
    private val dao: PlaybackSettingsDao,
    scope: CoroutineScope,
) : PlaybackSettingsRepository {

    override val settings: StateFlow<PlaybackSettings> =
        dao.observeSettings()
            .map { entity ->
                entity?.toPlaybackSettings()
                    ?: PlaybackSettings()
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlaybackSettings(),
            )

    override suspend fun setRepeatMode(
        repeatMode: RepeatMode,
    ) {
        val currentSettings =
            settings.value

        dao.upsert(
            PlaybackSettingsEntity(
                repeatMode =
                    repeatMode.toStorageValue(),
                shuffleEnabled =
                    currentSettings.shuffleEnabled,
            ),
        )
    }

    override suspend fun setShuffleEnabled(
        enabled: Boolean,
    ) {
        val currentSettings =
            settings.value

        dao.upsert(
            PlaybackSettingsEntity(
                repeatMode =
                    currentSettings.repeatMode
                        .toStorageValue(),
                shuffleEnabled =
                    enabled,
            ),
        )
    }
}

private fun PlaybackSettingsEntity.toPlaybackSettings():
    PlaybackSettings {
    return PlaybackSettings(
        repeatMode =
            when (repeatMode) {
                1 ->
                    RepeatMode.ALL

                2 ->
                    RepeatMode.ONE

                else ->
                    RepeatMode.OFF
            },
        shuffleEnabled =
            shuffleEnabled,
    )
}

private fun RepeatMode.toStorageValue(): Int {
    return when (this) {
        RepeatMode.OFF ->
            0

        RepeatMode.ALL ->
            1

        RepeatMode.ONE ->
            2
    }
}