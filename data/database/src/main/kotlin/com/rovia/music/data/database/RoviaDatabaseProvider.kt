package com.rovia.music.data.database

import android.content.Context
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.playback.PlaybackSettingsRepository
import com.rovia.music.core.playback.RecentPlayRepository
import com.rovia.music.data.database.repository.RoomFolderFilterRepository
import com.rovia.music.data.database.repository.RoomPlaybackSettingsRepository
import com.rovia.music.data.database.repository.RoomRecentPlayRepository
import kotlinx.coroutines.CoroutineScope

object RoviaDatabaseProvider {

    fun createRecentPlayRepository(
        context: Context,
        scope: CoroutineScope,
    ): RecentPlayRepository {
        val database = RoviaDatabase.getInstance(context)

        return RoomRecentPlayRepository(
            dao = database.recentPlayDao(),
            scope = scope,
        )
    }

    fun createFolderFilterRepository(
        context: Context,
        scope: CoroutineScope,
    ): FolderFilterRepository {
        val database = RoviaDatabase.getInstance(context)

        return RoomFolderFilterRepository(
            dao = database.excludedFolderDao(),
            scope = scope,
        )
    }

    fun createPlaybackSettingsRepository(
        context: Context,
        scope: CoroutineScope,
    ): PlaybackSettingsRepository {
        val database = RoviaDatabase.getInstance(context)

        return RoomPlaybackSettingsRepository(
            dao = database.playbackSettingsDao(),
            scope = scope,
        )
    }
}