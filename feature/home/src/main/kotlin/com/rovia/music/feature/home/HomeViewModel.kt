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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
) : ViewModel() {

    private val recentlyAdded =
        flow {
            emit(
                musicRepository.getRecentlyAdded(
                    limit = 10,
                ),
            )
        }

    private val homeContent: Flow<HomeUiState> =
        combine(
            recentlyAdded,
            playbackController.recentPlays,
        ) { recentlyAddedTracks, recentPlays ->
            HomeUiState.Content(
                recentlyAdded = recentlyAddedTracks,
                recentPlays = recentPlays,
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