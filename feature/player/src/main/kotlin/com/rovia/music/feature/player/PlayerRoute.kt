
package com.rovia.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        onClose = {
            viewModel.hideLyrics()
            onClose()
        },
        modifier = modifier,
    )
}
