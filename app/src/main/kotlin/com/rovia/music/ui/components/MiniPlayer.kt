package com.rovia.music.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackController

@Composable
fun MiniPlayer(
    playbackController: PlaybackController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var currentTitle by remember(controller) {
        mutableStateOf(
            controller?.mediaMetadata?.title?.toString(),
        )
    }

    var currentArtist by remember(controller) {
        mutableStateOf(
            controller?.mediaMetadata?.artist?.toString(),
        )
    }

    var currentArtworkUri by remember(controller) {
        mutableStateOf(
            controller?.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.toString(),
        )
    }

    var showPauseIcon by remember(controller) {
        mutableStateOf(
            controller?.let {
                it.playWhenReady &&
                    it.playbackState != Player.STATE_ENDED
            } == true,
        )
    }

    var hasMedia by remember(controller) {
        mutableStateOf(controller?.currentMediaItem != null)
    }

    val latestOnOpenPlayer = rememberUpdatedState(onOpenPlayer)
    val density = LocalDensity.current

    DisposableEffect(controller) {
        val player = controller

        if (player == null) {
            currentTitle = null
            currentArtist = null
            currentArtworkUri = null
            showPauseIcon = false
            hasMedia = false

            onDispose {}
        } else {
            fun updateState(activePlayer: Player) {
                currentTitle =
                    activePlayer.mediaMetadata.title?.toString()

                currentArtist =
                    activePlayer.mediaMetadata.artist?.toString()

                currentArtworkUri = activePlayer.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()

                showPauseIcon =
                    activePlayer.playWhenReady &&
                        activePlayer.playbackState != Player.STATE_ENDED

                hasMedia = activePlayer.currentMediaItem != null
            }

            val listener = object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    updateState(player)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    updateState(player)
                }
            }

            player.addListener(listener)
            updateState(player)

            onDispose {
                player.removeListener(listener)
            }
        }
    }

    if (hasMedia) {
        val displayTitle = currentTitle
            ?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.player_unknown_track)

        val displayArtist = currentArtist
            ?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.player_unknown_artist)

        Surface(
            onClick = {
                latestOnOpenPlayer.value()
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 4.dp,
                )
                .pointerInput(density) {
                    var totalVerticalDrag = 0f
                    val dragThreshold = with(density) {
                        36.dp.toPx()
                    }

                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (totalVerticalDrag < -dragThreshold) {
                                latestOnOpenPlayer.value()
                            }

                            totalVerticalDrag = 0f
                        },
                        onDragCancel = {
                            totalVerticalDrag = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            totalVerticalDrag += dragAmount
                            change.consume()
                        },
                    )
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtwork(
                    artworkUri = currentArtworkUri,
                    fallbackText = displayTitle,
                    contentDescription = displayTitle,
                    modifier = Modifier.size(48.dp),
                    size = 48.dp,
                    shape = MaterialTheme.shapes.medium,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    enabled = hasMedia,
                    onClick = {
                        controller?.let { activeController ->
                            if (
                                activeController.playWhenReady &&
                                activeController.playbackState !=
                                Player.STATE_ENDED
                            ) {
                                activeController.pause()
                            } else {
                                if (
                                    activeController.playbackState ==
                                    Player.STATE_ENDED
                                ) {
                                    activeController.seekToDefaultPosition()
                                }

                                activeController.play()
                            }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (showPauseIcon) {
                                R.drawable.ic_pause
                            } else {
                                R.drawable.ic_play
                            },
                        ),
                        contentDescription = stringResource(
                            if (showPauseIcon) {
                                R.string.player_pause
                            } else {
                                R.string.player_play
                            },
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}