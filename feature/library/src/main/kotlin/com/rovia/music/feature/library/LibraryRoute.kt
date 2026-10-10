
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

    val contentState =
        uiState as? LibraryUiState.Content

    val isInsideFolder =
        contentState?.currentFolderPath != null

    val isInsideCollection =
        contentState?.selectedCollection != null

    /*
     * Navigation priority:
     *
     * 1. Return from an Artist, Album, or Genre collection.
     * 2. Otherwise, navigate to the parent folder.
     *
     * Do not intercept Back while the Full Player is open.
     */
    BackHandler(
        enabled =
            (isInsideCollection || isInsideFolder) &&
                !isPlayerOpen,
    ) {
        when {
            isInsideCollection -> {
                viewModel.clearCollectionSelection()
            }

            isInsideFolder -> {
                viewModel.goToParentFolder()
            }
        }
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
        onSortOptionChange = { option ->
            viewModel.setSortOption(
                option = option,
            )
        },
        onToggleSortOrder = {
            viewModel.toggleSortOrder()
        },
        hasMiniPlayer = hasMiniPlayer,

        /*
         * Library browsing mode callbacks.
         */
        onBrowseModeChange = { mode ->
            viewModel.setBrowseMode(
                mode = mode,
            )
        },
        onOpenArtist = { name ->
            viewModel.openArtist(
                name = name,
            )
        },
        onOpenAlbum = { title, artist ->
            viewModel.openAlbum(
                title = title,
                artist = artist,
            )
        },
        onOpenGenre = { name ->
            viewModel.openGenre(
                name = name,
            )
        },
        onBackFromCollection = {
            viewModel.clearCollectionSelection()
        },
    )
}
