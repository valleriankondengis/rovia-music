package com.rovia.music

import android.content.Context
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.FolderFilterRepository
import com.rovia.music.core.library.FolderScannerRepository
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.playback.PlaybackSettingsRepository
import com.rovia.music.core.playback.RecentPlayRepository
import com.rovia.music.data.database.RoviaDatabaseProvider
import com.rovia.music.data.media.store.EmbeddedLyricsRepository
import com.rovia.music.data.media.store.MediaStoreFolderBrowserRepository
import com.rovia.music.data.media.store.MediaStoreFolderScannerRepository
import com.rovia.music.data.media.store.MediaStoreMusicRepository
import com.rovia.music.playback.media3.Media3PlaybackController
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    context: Context,
    applicationScope: CoroutineScope,
) {
    private val recentPlayRepository:
        RecentPlayRepository =
        RoviaDatabaseProvider
            .createRecentPlayRepository(
                context = context,
                scope = applicationScope,
            )

    private val playbackSettingsRepository:
        PlaybackSettingsRepository =
        RoviaDatabaseProvider
            .createPlaybackSettingsRepository(
                context = context,
                scope = applicationScope,
            )

    val folderFilterRepository:
        FolderFilterRepository =
        RoviaDatabaseProvider
            .createFolderFilterRepository(
                context = context,
                scope = applicationScope,
            )

    val folderScannerRepository:
        FolderScannerRepository =
        MediaStoreFolderScannerRepository(
            context = context,
        )

    val folderBrowserRepository:
        FolderBrowserRepository =
        MediaStoreFolderBrowserRepository(
            context = context,
            folderFilterRepository =
                folderFilterRepository,
        )

    val musicRepository:
        MusicRepository =
        MediaStoreMusicRepository(
            context = context,
            folderFilterRepository =
                folderFilterRepository,
        )

    val lyricsRepository:
        LyricsRepository =
        EmbeddedLyricsRepository(
            context,
        )

    val playbackController:
        PlaybackController =
        Media3PlaybackController(
            context = context,
            recentPlayRepository =
                recentPlayRepository,
            playbackSettingsRepository =
                playbackSettingsRepository,
        )
}