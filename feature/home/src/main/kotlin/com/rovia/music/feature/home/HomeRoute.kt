package com.rovia.music.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController

@Composable
fun HomeRoute(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(
            factory =
                HomeViewModelFactory(
                    musicRepository = musicRepository,
                    playbackController = playbackController,
                ),
        )

    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    val hasMiniPlayer =
        playbackState.currentTrack != null

    val currentTrackId =
        playbackState.currentTrack?.id

    HomeScreen(
        uiState = uiState,
        onTrackClick = { tracks, startIndex ->
            playbackController.playQueue(
                tracks = tracks,
                startIndex = startIndex,
            )
        },
        onRestartCurrentTrack = {
            playbackController.restartCurrentTrack()
        },
        currentTrackId = currentTrackId,
        onOpenSettings = onOpenSettings,
        hasMiniPlayer = hasMiniPlayer,
    )
}