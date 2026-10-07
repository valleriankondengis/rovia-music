@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track

@Composable
internal fun LandscapePlayerContent(
    track: Track?,
    playbackState: PlaybackState,
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
    seekPosition: Float,
    duration: Long,
    displayedIsPlaying: Boolean,
    motionEffectsSpec:
        FiniteAnimationSpec<Float>,
    onSeekPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onLyricSeek: (Long) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                ),
        horizontalArrangement =
            Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            PlayerActionButtonGroup(
                track = track,
                hasLyrics = hasLyrics,
                isLyricsLoading = isLyricsLoading,
                isLyricsVisible = isLyricsVisible,
                onToggleLyrics = onToggleLyrics,
                onClose = onClose,
            )

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment =
                    Alignment.Center,
            ) {
                val artworkSize =
                    minOf(
                        maxWidth * 0.82f,
                        maxHeight * 0.82f,
                    )

                AnimatedContent(
                    targetState = isLyricsVisible,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(
                            animationSpec = motionEffectsSpec,
                        ).togetherWith(
                            fadeOut(
                                animationSpec = motionEffectsSpec,
                            ),
                        ).using(null)
                    },
                    label = "player-landscape-content-transition",
                ) { showLyrics ->
                    if (showLyrics) {
                        PlayerLyric(
                            lyrics = lyrics,
                            hasLyrics = hasLyrics,
                            isLyricsLoading = isLyricsLoading,
                            playbackPositionMs = playbackState.positionMs,
                            onSeek = onLyricSeek,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PlayerArtwork(
                            track = track,
                            size = artworkSize,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        horizontal = 8.dp,
                    ),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxSize(),
            ) {
                val compactLandscape =
                    maxHeight < 520.dp

                Column(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        if (compactLandscape) {
                            Arrangement.Top
                        } else {
                            Arrangement.Center
                        },
                ) {
                    Text(
                        text =
                            track?.title
                                ?: stringResource(
                                    R.string.player_no_track,
                                ),
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations =
                                        Int.MAX_VALUE,
                                    initialDelayMillis =
                                        900,
                                    repeatDelayMillis =
                                        900,
                                    velocity = 35.dp,
                                ),
                    )

                    Text(
                        text =
                            track?.artist
                                ?: stringResource(
                                    R.string
                                        .player_unknown_artist,
                                ),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .basicMarquee(
                                    iterations =
                                        Int.MAX_VALUE,
                                    initialDelayMillis =
                                        900,
                                    repeatDelayMillis =
                                        900,
                                    velocity = 35.dp,
                                ),
                    )

                    WavySeekBar(
                        positionMs = seekPosition,
                        durationMs = duration,
                        onPositionChange =
                            onSeekPositionChange,
                        onSeekFinished =
                            onSeekFinished,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top =
                                        if (
                                            compactLandscape
                                        ) {
                                            16.dp
                                        } else {
                                            24.dp
                                        },
                                ),
                    )

                    TrackTechnicalInfo(
                        track = track,
                    )

                    SeekTimeRow(
                        positionMs = seekPosition,
                        durationMs = duration,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                if (
                                    compactLandscape
                                ) {
                                    8.dp
                                } else {
                                    24.dp
                                },
                            ),
                    )

                    PlaybackButtonGroup(
                        isPlaying = displayedIsPlaying,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                if (
                                    compactLandscape
                                ) {
                                    8.dp
                                } else {
                                    16.dp
                                },
                            ),
                    )

                    PlaybackOptionsButtonGroup(
                        repeatMode =
                            playbackState.repeatMode,
                        shuffleEnabled =
                            playbackState.shuffleEnabled,
                        onRepeatModeChange =
                            onRepeatModeChange,
                        onShuffleEnabledChange =
                            onShuffleEnabledChange,
                    )
                }
            }
        }
    }
}
