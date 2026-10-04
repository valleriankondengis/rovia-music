package com.rovia.music.feature.search

import com.rovia.music.core.model.Track

sealed interface SearchUiState {

    data object Loading : SearchUiState

    data class Content(
        val query: String,
        val results: List<Track>,
    ) : SearchUiState

    data class Error(
        val throwable: Throwable,
    ) : SearchUiState
}