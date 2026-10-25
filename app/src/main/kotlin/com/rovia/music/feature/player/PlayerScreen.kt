package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    playbackController: PlaybackController,
    onBack: () -> Unit,
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

    var currentPosition by remember(controller) {
        mutableLongStateOf(
            controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
        )
    }

    var durationMs by remember(controller) {
        mutableLongStateOf(
            controller?.duration?.takeIf {
                it != C.TIME_UNSET && it > 0L
            } ?: 0L,
        )
    }

    var isPlaying by remember(controller) {
        mutableStateOf(controller?.isPlaying == true)
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

    var hasNext by remember(controller) {
        mutableStateOf(controller?.hasNextMediaItem() == true)
    }

    var isBuffering by remember(controller) {
        mutableStateOf(
            controller?.playbackState == Player.STATE_BUFFERING,
        )
    }

    var showBufferingIndicator by remember(controller) {
        mutableStateOf(false)
    }

    var dragPosition by remember(controller) {
        mutableStateOf<Float?>(null)
    }

    val latestDragPosition = rememberUpdatedState(dragPosition)

    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    DisposableEffect(controller) {
        val player = controller

        if (player == null) {
            currentTitle = null
            currentArtist = null
            currentArtworkUri = null
            currentPosition = 0L
            durationMs = 0L
            isPlaying = false
            showPauseIcon = false
            hasMedia = false
            hasNext = false
            isBuffering = false
            showBufferingIndicator = false

            onDispose {}
        } else {
            fun updatePlayerState(activePlayer: Player) {
                currentTitle = activePlayer.mediaMetadata.title
                    ?.toString()

                currentArtist = activePlayer.mediaMetadata.artist
                    ?.toString()

                currentArtworkUri = activePlayer.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()

                currentPosition = activePlayer.currentPosition
                    .coerceAtLeast(0L)

                durationMs = activePlayer.duration.takeIf {
                    it != C.TIME_UNSET && it > 0L
                } ?: 0L

                isPlaying = activePlayer.isPlaying

                showPauseIcon =
                    activePlayer.playWhenReady &&
                        activePlayer.playbackState != Player.STATE_ENDED

                hasMedia = activePlayer.currentMediaItem != null
                hasNext = activePlayer.hasNextMediaItem()
                isBuffering =
                    activePlayer.playbackState == Player.STATE_BUFFERING
            }

            val listener = object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    updatePlayerState(player)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    updatePlayerState(player)
                }
            }

            player.addListener(listener)
            updatePlayerState(player)

            onDispose {
                player.removeListener(listener)
            }
        }
    }

    LaunchedEffect(
        controller,
        isBuffering,
        dragPosition,
        hasMedia,
    ) {
        if (!isBuffering || dragPosition != null || !hasMedia) {
            showBufferingIndicator = false
            return@LaunchedEffect
        }

        delay(500)

        if (
            isBuffering &&
            dragPosition == null &&
            hasMedia
        ) {
            showBufferingIndicator = true
        }
    }

    LaunchedEffect(controller, isPlaying, hasMedia) {
        val activeController = controller
            ?: return@LaunchedEffect

        while (isPlaying && hasMedia) {
            if (latestDragPosition.value == null) {
                currentPosition = activeController.currentPosition
                    .coerceAtLeast(0L)
            }

            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                bottom = systemNavigationBarBottomInset + 24.dp,
            ),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.player_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_arrow_back,
                        ),
                        contentDescription = stringResource(
                            R.string.player_back,
                        ),
                    )
                }
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 10.dp),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            AlbumArtwork(
                artworkUri = currentArtworkUri,
                fallbackText = currentTitle ?: currentArtist.orEmpty(),
                contentDescription = currentTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                size = 320.dp,
                shape = MaterialTheme.shapes.extraLarge,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when {
                    controller == null -> stringResource(
                        R.string.player_connecting,
                    )

                    !hasMedia -> stringResource(
                        R.string.player_no_track,
                    )

                    else -> currentTitle
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(
                            R.string.player_unknown_track,
                        )
                },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (hasMedia) {
                Text(
                    text = currentArtist
                        ?.takeIf(String::isNotBlank)
                        ?: stringResource(
                            R.string.player_unknown_artist,
                        ),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (showBufferingIndicator) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val sliderMaximum = durationMs
                .takeIf { it > 0L }
                ?.toFloat()
                ?: 1f

            val sliderValue = (
                dragPosition ?: currentPosition.toFloat()
            ).coerceIn(0f, sliderMaximum)

            Slider(
                value = sliderValue,
                onValueChange = {
                    dragPosition = it
                },
                onValueChangeFinished = {
                    dragPosition?.let { position ->
                        controller?.seekTo(position.toLong())
                        currentPosition = position.toLong()
                    }

                    dragPosition = null
                },
                enabled = controller != null &&
                    hasMedia &&
                    durationMs > 0L,
                valueRange = 0f..sliderMaximum,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(
                        (dragPosition?.toLong() ?: currentPosition)
                            .coerceAtLeast(0L),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )

                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(
                    enabled = hasMedia,
                    onClick = {
                        controller?.seekToPreviousMediaItem()
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_skip_previous,
                        ),
                        contentDescription = stringResource(
                            R.string.player_previous,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))

                FilledIconButton(
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
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
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
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))

                androidx.compose.material3.IconButton(
                    enabled = hasMedia && hasNext,
                    onClick = {
                        controller?.seekToNextMediaItem()
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_skip_next,
                        ),
                        contentDescription = stringResource(
                            R.string.player_next,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L

    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L,
    )
}