
package com.rovia.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rovia.music.core.library.LyricsRepository
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.playback.PlaybackController

@Composable
fun PlayerRoute(
    playbackController: PlaybackController,
    lyricsRepository: LyricsRepository,
    musicRepository: MusicRepository,
    onClose: () -> Unit,
    onCloseActionAvailable: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerViewModel =
        viewModel(
            factory =
                PlayerViewModelFactory(
                    playbackController =
                        playbackController,
                    lyricsRepository =
                        lyricsRepository,
                    musicRepository =
                        musicRepository,
                ),
        )

    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    val playbackState =
        uiState.playbackState

    /*
     * Keep one shared close action for both the Player UI
     * and the parent sheet's completed predictive Back gesture.
     *
     * The lyrics visibility is cleared before the sheet closes.
     */
    val closePlayer: () -> Unit =
        remember(
            viewModel,
            onClose,
        ) {
            {
                viewModel.hideLyrics()
                onClose()
            }
        }

    /*
     * Register the close action with the parent while this
     * PlayerRoute is composed. The parent can invoke the same
     * action after a successful predictive Back gesture.
     *
     * A cancelled gesture does not invoke this action.
     */
    DisposableEffect(closePlayer) {
        onCloseActionAvailable(
            closePlayer,
        )

        onDispose {
            onCloseActionAvailable(null)
        }
    }

    PlayerScreen(
        playbackState = playbackState,
        lyrics = uiState.lyrics,
        isLyricsLoading =
            uiState.isLyricsLoading,
        isLyricsVisible =
            uiState.isLyricsVisible,
        onToggleLyrics =
            viewModel::toggleLyrics,
        onPrevious =
            playbackController::skipToPrevious,
        onPlayPause = {
            if (playbackState.isPlaying) {
                playbackController.pause()
            } else {
                playbackController.resume()
            }
        },
        onNext =
            playbackController::skipToNext,
        onRepeatModeChange = { repeatMode ->
            val nextMode =
                when (repeatMode) {
                    RepeatMode.OFF ->
                        RepeatMode.ALL

                    RepeatMode.ALL ->
                        RepeatMode.ONE

                    RepeatMode.ONE ->
                        RepeatMode.OFF
                }

            playbackController.setRepeatMode(
                nextMode,
            )
        },
        onShuffleEnabledChange = { enabled ->
            playbackController.setShuffleEnabled(
                enabled,
            )
        },
        onSeek =
            playbackController::seekTo,
        onClose = closePlayer,
        modifier = modifier,
    )
}
