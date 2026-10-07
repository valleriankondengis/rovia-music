@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.AlbumArtwork

private val PlayerLyricsPillShape =
    RoundedCornerShape(
        percent = 50,
    )

@Composable
internal fun PlayerLyricsMiniPlayer(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack
            ?: return

    val duration =
        playbackState.durationMs.coerceAtLeast(0L)

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

    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        /*
         * Dedicated seekbar surface.
         */
        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                PlayerLyricsPillShape,
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    draggedElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text =
                        formatLyricsPlaybackTime(
                            seekPosition
                                .toLong(),
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                WavySeekBar(
                    positionMs = seekPosition,
                    durationMs = duration,
                    onPositionChange = { position ->
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
                    modifier =
                        Modifier
                            .weight(1f),
                )
            }
        }

        /*
         * Dedicated MiniPlayer surface.
         */
        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                PlayerLyricsPillShape,
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    draggedElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 12.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                AlbumArtwork(
                    artworkUri =
                        track.artworkUri,
                    fallbackText =
                        track.title,
                    contentDescription = null,
                    modifier =
                        Modifier.size(56.dp),
                    size = 56.dp,
                    shape =
                        MaterialTheme
                            .shapes
                            .large,
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = track.title,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    track.artist?.let { artist ->
                        Text(
                            text = artist,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }

                PlayerLyricsMiniPlayerButtons(
                    isPlaying =
                        playbackState.isPlaying,
                    onPrevious =
                        onPrevious,
                    onPlayPause =
                        onPlayPause,
                    onNext =
                        onNext,
                )
            }
        }
    }
}

@Composable
private fun PlayerLyricsMiniPlayerButtons(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val previousSource =
        remember {
            MutableInteractionSource()
        }

    val playPauseSource =
        remember {
            MutableInteractionSource()
        }

    val nextSource =
        remember {
            MutableInteractionSource()
        }

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(6.dp),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onPrevious,
            interactionSource =
                previousSource,
            modifier =
                Modifier.size(48.dp),
            colors =
                IconButtonDefaults
                    .filledIconButtonColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceContainerHigh,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    ),
            shapes =
                IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter =
                    painterResource(
                        CoreUiR.drawable
                            .ic_skip_previous,
                    ),
                contentDescription =
                    stringResource(
                        R.string.player_previous,
                    ),
                modifier =
                    Modifier.size(24.dp),
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            interactionSource =
                playPauseSource,
            modifier =
                Modifier.size(52.dp),
            colors =
                IconButtonDefaults
                    .filledIconButtonColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .primaryContainer,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                    ),
            shapes =
                IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (isPlaying) {
                            CoreUiR.drawable
                                .ic_pause
                        } else {
                            CoreUiR.drawable
                                .ic_play_arrow
                        },
                    ),
                contentDescription =
                    if (isPlaying) {
                        stringResource(
                            R.string.player_pause,
                        )
                    } else {
                        stringResource(
                            R.string.player_play,
                        )
                    },
                modifier =
                    Modifier.size(28.dp),
            )
        }

        FilledIconButton(
            onClick = onNext,
            interactionSource =
                nextSource,
            modifier =
                Modifier.size(48.dp),
            colors =
                IconButtonDefaults
                    .filledIconButtonColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceContainerHigh,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    ),
            shapes =
                IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter =
                    painterResource(
                        CoreUiR.drawable
                            .ic_skip_next,
                    ),
                contentDescription =
                    stringResource(
                        R.string.player_next,
                    ),
                modifier =
                    Modifier.size(24.dp),
            )
        }
    }
}

private fun formatLyricsPlaybackTime(
    durationMs: Long,
): String {
    if (durationMs <= 0L) {
        return "0:00"
    }

    val totalSeconds =
        durationMs / 1_000

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds,
    )
}