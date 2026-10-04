package com.rovia.music.feature.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchRoute(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
    onOpenSettings: () -> Unit,
) {
    val viewModel: SearchViewModel =
        viewModel(
            factory =
                SearchViewModelFactory(
                    musicRepository = musicRepository,
                ),
        )

    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    val textFieldState =
        rememberTextFieldState()

    val hasMiniPlayer =
        playbackState.currentTrack != null

    LaunchedEffect(textFieldState) {
        snapshotFlow {
            textFieldState.text.toString()
        }
            .distinctUntilChanged()
            .collect(viewModel::setQuery)
    }

    SearchScreen(
        uiState = uiState,
        textFieldState = textFieldState,
        onTrackClick = { tracks, startIndex ->
            playbackController.playQueue(
                tracks = tracks,
                startIndex = startIndex,
            )
        },
        onOpenSettings = onOpenSettings,
        hasMiniPlayer = hasMiniPlayer,
    )
}