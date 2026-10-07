@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics

@Composable
fun PlayerScreen(
    playbackState: PlaybackState,
    lyrics: SyncedLyrics?,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playbackState.currentTrack

    var isSeeking by remember(
        playbackState.currentTrack?.id,
    ) {
        mutableStateOf(false)
    }

    var seekPosition by remember(
        playbackState.currentTrack?.id,
    ) {
        mutableFloatStateOf(
            playbackState.positionMs.toFloat(),
        )
    }

    var isPlayingBeforeSeek by remember(
        playbackState.currentTrack?.id,
    ) {
        mutableStateOf(
            playbackState.isPlaying,
        )
    }

    val displayedIsPlaying =
        if (isSeeking) {
            isPlayingBeforeSeek
        } else {
            playbackState.isPlaying
        }

    LaunchedEffect(
        playbackState.positionMs,
        playbackState.currentTrack?.id,
        isSeeking,
    ) {
        if (!isSeeking) {
            seekPosition =
                playbackState.positionMs.toFloat()
        }
    }

    val duration =
        playbackState.durationMs.coerceAtLeast(0L)

    val hasLyrics =
        lyrics != null &&
            lyrics.lines.isNotEmpty()

    val motionScheme =
        MaterialTheme.motionScheme

    val motionEffectsSpec =
        motionScheme.fastEffectsSpec<Float>()

    val configuration =
        LocalConfiguration.current

    val isLandscape =
        configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier =
            modifier.fillMaxSize(),
        color =
            MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        when {
            isLyricsVisible -> {
                PlayerLyricsFullscreenContent(
                    track = track,
                    playbackState = playbackState,
                    lyrics = lyrics,
                    hasLyrics = hasLyrics,
                    isLyricsLoading = isLyricsLoading,
                    isLyricsVisible = isLyricsVisible,
                    onToggleLyrics = onToggleLyrics,
                    onClose = onClose,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onLyricSeek = onSeek,
                )
            }

            isLandscape -> {
                LandscapePlayerContent(
                    track = track,
                    playbackState = playbackState,
                    lyrics = lyrics,
                    hasLyrics = hasLyrics,
                    isLyricsLoading = isLyricsLoading,
                    isLyricsVisible = isLyricsVisible,
                    onToggleLyrics = onToggleLyrics,
                    onClose = onClose,
                    seekPosition = seekPosition,
                    duration = duration,
                    displayedIsPlaying = displayedIsPlaying,
                    motionEffectsSpec = motionEffectsSpec,
                    onSeekPositionChange = { position ->
                        if (!isSeeking) {
                            isPlayingBeforeSeek =
                                playbackState.isPlaying
                        }

                        isSeeking = true
                        seekPosition = position
                    },
                    onSeekFinished = {
                        onSeek(
                            seekPosition
                                .toLong()
                                .coerceIn(
                                    0L,
                                    duration,
                                ),
                        )

                        isSeeking = false
                    },
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onRepeatModeChange = onRepeatModeChange,
                    onShuffleEnabledChange = onShuffleEnabledChange,
                    onLyricSeek = onSeek,
                )
            }

            else -> {
                PortraitPlayerContent(
                    track = track,
                    playbackState = playbackState,
                    lyrics = lyrics,
                    hasLyrics = hasLyrics,
                    isLyricsLoading = isLyricsLoading,
                    isLyricsVisible = isLyricsVisible,
                    onToggleLyrics = onToggleLyrics,
                    onClose = onClose,
                    seekPosition = seekPosition,
                    duration = duration,
                    displayedIsPlaying = displayedIsPlaying,
                    motionEffectsSpec = motionEffectsSpec,
                    onSeekPositionChange = { position ->
                        if (!isSeeking) {
                            isPlayingBeforeSeek =
                                playbackState.isPlaying
                        }

                        isSeeking = true
                        seekPosition = position
                    },
                    onSeekFinished = {
                        onSeek(
                            seekPosition
                                .toLong()
                                .coerceIn(
                                    0L,
                                    duration,
                                ),
                        )

                        isSeeking = false
                    },
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onRepeatModeChange = onRepeatModeChange,
                    onShuffleEnabledChange = onShuffleEnabledChange,
                    onLyricSeek = onSeek,
                )
            }
        }
    }
}