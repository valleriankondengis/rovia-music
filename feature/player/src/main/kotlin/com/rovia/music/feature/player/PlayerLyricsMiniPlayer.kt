@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    positionMs: Float,
    durationMs: Long,
    onPositionChange: (Float) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack
            ?: return

    var latestSeekPosition by
        remember(
            playbackState.currentTrack?.id,
        ) {
            mutableFloatStateOf(
                positionMs,
            )
        }

    /*
     * Synchronize the mini seekbar with the real playback
     * position whenever we are not actively moving it.
     *
     * During drag, onPositionChange updates this same state.
     */
    LaunchedEffect(positionMs) {
        latestSeekPosition = positionMs
    }

    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        /*
         * Dedicated seekbar surface.
         *
         * The visual WavySeekBar is kept at 32dp height.
         * A transparent 32dp gesture layer handles both:
         *
         * - tap
         * - horizontal drag
         *
         * The gesture layer cannot cover the lyrics area.
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
                    Arrangement.spacedBy(8.dp),
            ) {
                /*
                 * Fixed-width duration container.
                 *
                 * This prevents the seekbar from moving when
                 * the displayed time changes from:
                 *
                 * 0:59 -> 1:00
                 * 9:59 -> 10:00
                 *
                 * The width stays exactly 40dp.
                 */
                Box(
                    modifier =
                        Modifier.width(40.dp),
                    contentAlignment =
                        Alignment.CenterStart,
                ) {
                    Text(
                        text =
                            formatLyricsPlaybackTime(
                                latestSeekPosition
                                    .toLong()
                                    .coerceAtLeast(0L),
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Clip,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(32.dp),
                ) {
                    /*
                     * Visual layer only.
                     *
                     * Disable its own gestures because the
                     * dedicated gesture layer below owns input.
                     */
                    WavySeekBar(
                        positionMs = latestSeekPosition,
                        durationMs = durationMs,
                        onPositionChange = {},
                        onSeekFinished = {},
                        modifier =
                            Modifier.fillMaxSize(),
                    )

                    /*
                     * Dedicated gesture layer.
                     *
                     * Exactly 32dp high, matching the visual
                     * seekbar. This is the important part that
                     * prevents the fullscreen sheet gesture from
                     * stealing the horizontal seek gesture.
                     */
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    durationMs,
                                ) {
                                    fun positionFromX(
                                        x: Float,
                                    ): Float {
                                        if (
                                            durationMs <= 0L ||
                                            size.width <= 0
                                        ) {
                                            return 0f
                                        }

                                        return (
                                            x.coerceIn(
                                                0f,
                                                size.width.toFloat(),
                                            ) /
                                                size.width.toFloat()
                                        ) * durationMs
                                    }

                                    /*
                                     * Tap behavior.
                                     *
                                     * Same idea as the main PlayerSeekBar:
                                     * tap X -> convert to position -> seek.
                                     */
                                    detectTapGestures { offset ->
                                        val position =
                                            positionFromX(
                                                offset.x,
                                            )

                                        latestSeekPosition =
                                            position

                                        onPositionChange(
                                            position,
                                        )

                                        onSeekFinished(
                                            position
                                                .toLong()
                                                .coerceIn(
                                                    0L,
                                                    durationMs,
                                                ),
                                        )
                                    }
                                }
                                .pointerInput(
                                    durationMs,
                                ) {
                                    fun positionFromX(
                                        x: Float,
                                    ): Float {
                                        if (
                                            durationMs <= 0L ||
                                            size.width <= 0
                                        ) {
                                            return 0f
                                        }

                                        return (
                                            x.coerceIn(
                                                0f,
                                                size.width.toFloat(),
                                            ) /
                                                size.width.toFloat()
                                        ) * durationMs
                                    }

                                    var latestPosition =
                                        latestSeekPosition

                                    /*
                                     * Horizontal drag behavior.
                                     *
                                     * This follows the same gesture
                                     * structure used by the working
                                     * main WavySeekBar.
                                     */
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            latestPosition =
                                                positionFromX(
                                                    offset.x,
                                                )

                                            latestSeekPosition =
                                                latestPosition

                                            onPositionChange(
                                                latestPosition,
                                            )
                                        },
                                        onHorizontalDrag = {
                                                change,
                                                _,
                                            ->
                                            change.consume()

                                            latestPosition =
                                                positionFromX(
                                                    change.position.x,
                                                )

                                            latestSeekPosition =
                                                latestPosition

                                            onPositionChange(
                                                latestPosition,
                                            )
                                        },
                                        onDragEnd = {
                                            val position =
                                                latestPosition
                                                    .toLong()
                                                    .coerceIn(
                                                        0L,
                                                        durationMs,
                                                    )

                                            onSeekFinished(
                                                position,
                                            )
                                        },
                                        onDragCancel = {
                                            val position =
                                                latestPosition
                                                    .toLong()
                                                    .coerceIn(
                                                        0L,
                                                        durationMs,
                                                    )

                                            onSeekFinished(
                                                position,
                                            )
                                        },
                                    )
                                },
                    )
                }
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

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults
                .OverflowIndicator(
                    menuState = menuState,
                )
        },
        horizontalArrangement =
            Arrangement.spacedBy(6.dp),
    ) {
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPrevious,
                    interactionSource =
                        previousSource,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .animateWidth(
                                interactionSource =
                                    previousSource,
                            ),
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
                        IconButtonDefaults
                            .shapes(),
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
            },
            menuContent = {},
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPlayPause,
                    interactionSource =
                        playPauseSource,
                    modifier =
                        Modifier
                            .size(52.dp)
                            .animateWidth(
                                interactionSource =
                                    playPauseSource,
                            ),
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
                        IconButtonDefaults
                            .shapes(),
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
            },
            menuContent = {},
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onNext,
                    interactionSource =
                        nextSource,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .animateWidth(
                                interactionSource =
                                    nextSource,
                            ),
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
                        IconButtonDefaults
                            .shapes(),
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
            },
            menuContent = {},
        )
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