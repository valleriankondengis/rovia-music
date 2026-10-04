package com.rovia.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rovia.music.core.library.MusicRepository

class SearchViewModelFactory(
    private val musicRepository: MusicRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(
                musicRepository = musicRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}