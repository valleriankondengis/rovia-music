package com.rovia.music.feature.library

import com.rovia.music.core.model.MusicFolder
import com.rovia.music.core.model.Track

sealed interface LibraryUiState {

    data object Loading : LibraryUiState

    data class Content(
        val tracks: List<Track>,
        val folders: List<MusicFolder> = emptyList(),
        val folderTracks: List<Track> = emptyList(),
        val currentFolderPath: String? = null,
    ) : LibraryUiState

    data class Error(
        val throwable: Throwable,
    ) : LibraryUiState
}