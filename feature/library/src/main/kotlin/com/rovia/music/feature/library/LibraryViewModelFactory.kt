package com.rovia.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.MusicRepository

class LibraryViewModelFactory(
    private val musicRepository: MusicRepository,
    private val folderBrowserRepository: FolderBrowserRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                LibraryViewModel::class.java,
            )
        ) {
            return LibraryViewModel(
                musicRepository =
                    musicRepository,
                folderBrowserRepository =
                    folderBrowserRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}