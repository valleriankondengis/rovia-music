package com.rovia.music

import android.content.Context
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.data.mediastore.MediaStoreFolderRepository
import com.rovia.music.data.repository.FolderFilterRepository
import com.rovia.music.data.repository.SharedPreferencesFolderFilterRepository
import com.rovia.music.playback.controller.PlaybackController

class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val appName: String = "Rovia"

    val folderFilterRepository: FolderFilterRepository =
        SharedPreferencesFolderFilterRepository(
            applicationContext,
        )

    val mediaStoreFolderRepository =
        MediaStoreFolderRepository(
            applicationContext.contentResolver,
        )

    val mediaStoreAudioRepository =
        MediaStoreAudioRepository(
            contentResolver = applicationContext.contentResolver,
            folderFilterRepository = folderFilterRepository,
        )

    val playbackController =
        PlaybackController(applicationContext)
}