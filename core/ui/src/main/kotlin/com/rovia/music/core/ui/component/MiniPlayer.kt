@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.core.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.ui.R

private val MiniPlayerTopRadius =
    32.dp

private val MiniPlayerBottomRadius =
    20.dp

private val MiniPlayerShape =
    RoundedCornerShape(
        topStart =
            MiniPlayerTopRadius,
        topEnd =
            MiniPlayerTopRadius,
        bottomStart =
            MiniPlayerBottomRadius,
        bottomEnd =
            MiniPlayerBottomRadius,
    )

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack
            ?: return

    Card(
        modifier = modifier,
        shape = MiniPlayerShape,
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
        MiniPlayerContent(
            playbackState =
                playbackState,
            onPrevious =
                onPrevious,
            onPlayPause =
                onPlayPause,
            onNext =
                onNext,
            showArtwork = true,
            modifier =
                Modifier,
        )
    }
}

@Composable
fun MiniPlayerContent(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    showArtwork: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack
            ?: return

    Row(
        modifier =
            modifier.padding(
                horizontal = 12.dp,
                vertical = 12.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        if (showArtwork) {
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
        } else {
            Spacer(
                modifier =
                    Modifier.size(56.dp),
            )
        }

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

        MiniPlayerButtonGroup(
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

@Composable
private fun MiniPlayerButtonGroup(
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
                    onClick =
                        onPrevious,
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
                        IconButtonDefaults.shapes(),
                    interactionSource =
                        previousSource,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                R.drawable
                                    .ic_skip_previous,
                            ),
                        contentDescription =
                            "Previous",
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
                    onClick =
                        onPlayPause,
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
                        IconButtonDefaults.shapes(),
                    interactionSource =
                        playPauseSource,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isPlaying) {
                                    R.drawable
                                        .ic_pause
                                } else {
                                    R.drawable
                                        .ic_play_arrow
                                },
                            ),
                        contentDescription =
                            if (isPlaying) {
                                "Pause"
                            } else {
                                "Play"
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
                    onClick =
                        onNext,
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
                        IconButtonDefaults.shapes(),
                    interactionSource =
                        nextSource,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                R.drawable
                                    .ic_skip_next,
                            ),
                        contentDescription =
                            "Next",
                        modifier =
                            Modifier.size(24.dp),
                    )
                }
            },
            menuContent = {},
        )
    }
}