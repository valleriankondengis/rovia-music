
package com.rovia.music.feature.home

import com.rovia.music.core.model.Track

sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Content(
        val recentlyAdded: List<Track>,

        /**
         * The latest 10 unique tracks displayed in the Home section.
         */
        val recentPlays: List<Track>,

        /**
         * The complete Recent Play collection.
         *
         * This list is not limited to 10 items and is used to
         * build Artist, Album, and Genre collections and details.
         */
        val allRecentPlays: List<Track> = recentPlays,

        /**
         * Artist collections derived from the complete Recent Play list.
         */
        val recentArtists: List<RecentArtist> = emptyList(),

        /**
         * Album collections derived from the complete Recent Play list.
         */
        val recentAlbums: List<RecentAlbum> = emptyList(),

        /**
         * Genre collections derived from the complete Recent Play list.
         */
        val recentGenres: List<RecentGenre> = emptyList(),
    ) : HomeUiState

    data class Error(
        val throwable: Throwable,
    ) : HomeUiState
}
