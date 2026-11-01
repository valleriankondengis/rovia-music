package com.rovia.music

import android.content.Context
import com.rovia.music.data.local.database.RoviaDatabase
import com.rovia.music.data.local.database.RoviaDatabaseFactory
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.data.mediastore.MediaStoreCatalogSynchronizer
import com.rovia.music.data.mediastore.MediaStoreFolderRepository
import com.rovia.music.data.repository.DatabaseSearchRepository
import com.rovia.music.data.repository.FolderFilterRepository
import com.rovia.music.data.repository.LibraryRepository
import com.rovia.music.data.repository.PlaybackHistoryRepository
import com.rovia.music.data.repository.RoomLibraryRepository
import com.rovia.music.data.repository.SharedPreferencesFolderFilterRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.statistics.PlaybackStatisticsRepository

class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val appName: String = "Rovia"

    val database: RoviaDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoviaDatabaseFactory.create(applicationContext)
    }

    val databaseSearchRepository: DatabaseSearchRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        DatabaseSearchRepository(database)
    }

    val folderFilterRepository: FolderFilterRepository =
        SharedPreferencesFolderFilterRepository(
            applicationContext,
        )

    val mediaStoreFolderRepository =
        MediaStoreFolderRepository(
            applicationContext.contentResolver,
        )

    private val mediaStoreCatalogSynchronizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MediaStoreCatalogSynchronizer(
            context = applicationContext,
            database = database,
        )
    }

    val mediaStoreAudioRepository =
        MediaStoreAudioRepository(
            contentResolver = applicationContext.contentResolver,
            folderFilterRepository = folderFilterRepository,
            catalogSynchronizer = mediaStoreCatalogSynchronizer,
            database = database,
        )

    val libraryRepository: LibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLibraryRepository(
            database = database,
            folderFilterRepository = folderFilterRepository,
            mediaStoreAudioRepository = mediaStoreAudioRepository,
        )
    }

    val playbackHistoryRepository: PlaybackHistoryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PlaybackHistoryRepository(database)
    }

    val playbackStatisticsRepository: PlaybackStatisticsRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        PlaybackStatisticsRepository(database)
    }

    val playbackController: PlaybackController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PlaybackController(
            context = applicationContext,
        )
    }
}