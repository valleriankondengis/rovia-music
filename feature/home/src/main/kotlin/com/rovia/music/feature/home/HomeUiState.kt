package com.rovia.music.feature.home

import com.rovia.music.core.model.Track

sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Content(
        val recentlyAdded: List<Track>,
        val recentPlays: List<Track>,
    ) : HomeUiState

    data class Error(
        val throwable: Throwable,
    ) : HomeUiState
}