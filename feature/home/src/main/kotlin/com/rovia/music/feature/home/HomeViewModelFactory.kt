package com.rovia.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController

class HomeViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                musicRepository = musicRepository,
                playbackController = playbackController,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}