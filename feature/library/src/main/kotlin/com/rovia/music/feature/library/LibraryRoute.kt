package com.rovia.music.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rovia.music.core.library.FolderBrowserRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.playback.PlaybackController

@Composable
fun LibraryRoute(
    musicRepository: MusicRepository,
    folderBrowserRepository: FolderBrowserRepository,
    playbackController: PlaybackController,
    onOpenSettings: () -> Unit,
    isPlayerOpen: Boolean = false,
) {
    val viewModel: LibraryViewModel =
        viewModel(
            factory =
                LibraryViewModelFactory(
                    musicRepository =
                        musicRepository,
                    folderBrowserRepository =
                        folderBrowserRepository,
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

    val isInsideFolder =
        (uiState as? LibraryUiState.Content)
            ?.currentFolderPath != null

    BackHandler(
        enabled =
            isInsideFolder &&
                !isPlayerOpen,
    ) {
        viewModel.goToParentFolder()
    }

    LibraryScreen(
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
        onFolderClick = { relativePath ->
            viewModel.openFolder(
                relativePath = relativePath,
            )
        },
        onBackFromFolder = {
            viewModel.goToParentFolder()
        },
        onOpenSettings = onOpenSettings,
        onShowAllSongs = {
            viewModel.showAllSongs()
        },
        onShowRootFolders = {
            viewModel.showRootFolders()
        },
        hasMiniPlayer = hasMiniPlayer,
    )
}