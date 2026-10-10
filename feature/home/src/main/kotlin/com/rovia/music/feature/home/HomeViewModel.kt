
package com.rovia.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
) : ViewModel() {

    private val recentlyAdded =
        musicRepository.observeRecentlyAdded(
            limit = 10,
        )

    private val homeContent: Flow<HomeUiState> =
        combine(
            recentlyAdded,
            playbackController.recentPlays,
        ) { recentlyAddedTracks, allRecentPlays ->

            /*
             * Build Artist, Album, and Genre collections from
             * the complete Recent Play source.
             *
             * The source is expected to be ordered by most recently
             * played first. The collection builders preserve that
             * order and use the latest matching track's artwork.
             */
            val recentArtists =
                buildRecentArtists(
                    recentPlays = allRecentPlays,
                )

            val recentAlbums =
                buildRecentAlbums(
                    recentPlays = allRecentPlays,
                )

            val recentGenres =
                buildRecentGenres(
                    recentPlays = allRecentPlays,
                )

            HomeUiState.Content(
                recentlyAdded = recentlyAddedTracks,

                /*
                 * Keep the Home Recent Play section compact.
                 */
                recentPlays =
                    allRecentPlays.take(10),

                /*
                 * Preserve the complete Recent Play list for
                 * collection browsing and future detail pages.
                 */
                allRecentPlays =
                    allRecentPlays,

                recentArtists =
                    recentArtists,

                recentAlbums =
                    recentAlbums,

                recentGenres =
                    recentGenres,
            )
        }

    val uiState: StateFlow<HomeUiState> =
        homeContent
            .catch { throwable ->
                emit(
                    HomeUiState.Error(
                        throwable = throwable,
                    ),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState.Loading,
            )
}
