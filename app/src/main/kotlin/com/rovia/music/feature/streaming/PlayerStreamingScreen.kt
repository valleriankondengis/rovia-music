package com.rovia.music.feature.streaming

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.feature.player.AudioOutputIndicator
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.theme.ArtworkContentTheme
import java.util.Locale
import kotlinx.coroutines.delay

private val StreamingHorizontalContentInset = 18.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerStreamingScreen(
    streamingController: PlaybackController,
    onDismiss: () -> Unit,
) {
    val controller by streamingController.mediaController.collectAsStateWithLifecycle()
    val queueState by streamingController.queueState.collectAsStateWithLifecycle()
    val currentTrack = queueState.items.getOrNull(queueState.currentIndex)?.track
    val hasMedia = currentTrack != null

    var isPlaying by remember(controller) {
        mutableStateOf(controller?.isPlaying == true)
    }
    var playWhenReady by remember(controller) {
        mutableStateOf(controller?.playWhenReady == true)
    }
    var playbackState by remember(controller) {
        mutableIntStateOf(controller?.playbackState ?: Player.STATE_IDLE)
    }
    var playerError by remember(controller) {
        mutableStateOf<PlaybackException?>(null)
    }
    var liveTitle by remember(controller) { mutableStateOf<String?>(null) }
    var positionMs by remember(controller) { mutableLongStateOf(0L) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(
                playWhenReadyChanged: Boolean,
                reason: Int,
            ) {
                playWhenReady = playWhenReadyChanged
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaMetadata.title
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { liveTitle = it }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    val showPauseIcon = isPlaying ||
        (playWhenReady &&
            playbackState != Player.STATE_ENDED &&
            playbackState != Player.STATE_IDLE)
    val isBuffering = playbackState == Player.STATE_BUFFERING

    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val controlsSection: @Composable (Dp) -> Unit = { sectionWidth ->
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = when {
                    controller == null -> stringResource(R.string.player_connecting)
                    !hasMedia -> stringResource(R.string.streaming_idle_message)
                    else -> liveTitle ?: currentTrack.title
                        ?: stringResource(R.string.streaming_title)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )

            if (hasMedia) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (liveTitle != null) {
                        currentTrack.title
                            ?: stringResource(R.string.streaming_title)
                    } else {
                        currentTrack.uri.toString()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatStreamTime(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (isBuffering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.streaming_buffering),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val playbackButtonGroupWidth = sectionWidth
        val playbackColorScheme = MaterialTheme.colorScheme
        val idlePlaybackContainerColor =
            playbackColorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
        val idlePlaybackContentColor = playbackColorScheme.onSurface
        val activePlaybackContainerColor = playbackColorScheme.primaryContainer
        val activePlaybackContentColor = playbackColorScheme.onPrimaryContainer

        val playPauseInteractionSource = remember {
            MutableInteractionSource()
        }
        val stopInteractionSource = remember {
            MutableInteractionSource()
        }

        val togglePlayback = {
            controller?.let { activeController ->
                if (
                    activeController.playWhenReady &&
                    activeController.playbackState != Player.STATE_ENDED
                ) {
                    activeController.pause()
                } else {
                    if (
                        activeController.playbackState == Player.STATE_ENDED
                    ) {
                        activeController.seekToDefaultPosition()
                    }
                    activeController.play()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            ButtonGroup(
                expandedRatio = 0.4f,
                modifier = Modifier.width(playbackButtonGroupWidth),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(
                        menuState = menuState,
                    )
                },
            ) {
                customItem(
                    buttonGroupContent = {
                        FilledIconToggleButton(
                            checked = showPauseIcon,
                            onCheckedChange = {
                                togglePlayback().also {}
                            },
                            enabled = hasMedia,
                            interactionSource = playPauseInteractionSource,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .animateWidth(
                                    interactionSource = playPauseInteractionSource,
                                    compressionLimit = 16.dp,
                                ),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = idlePlaybackContainerColor,
                                contentColor = idlePlaybackContentColor,
                                checkedContainerColor = activePlaybackContainerColor,
                                checkedContentColor = activePlaybackContentColor,
                            ),
                            shapes = IconButtonDefaults.toggleableShapes(),
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
                    },
                    menuContent = {},
                )

                customItem(
                    buttonGroupContent = {
                        FilledIconButton(
                            enabled = hasMedia,
                            onClick = {
                                streamingController.stopPlayback()
                                onDismiss()
                            },
                            interactionSource = stopInteractionSource,
                            modifier = Modifier
                                .size(64.dp)
                                .animateWidth(
                                    interactionSource = stopInteractionSource,
                                    compressionLimit = 16.dp,
                                ),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = idlePlaybackContainerColor,
                                contentColor = idlePlaybackContentColor,
                            ),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stop),
                                contentDescription = stringResource(
                                    R.string.streaming_stop,
                                ),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    },
                    menuContent = {},
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            AudioOutputIndicator(
                enabled = hasMedia,
                externalMode = true,
                onArtwork = false,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
        }

        if (playerError != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.streaming_error_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    ArtworkContentTheme(seedColor = null) {
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkBackdrop(
                artworkUri = null,
                foregroundArtworkHeight = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )

            val isLandscape =
                LocalConfiguration.current.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
                val layoutDirection = LocalLayoutDirection.current
                val landscapeHorizontalInset = StreamingHorizontalContentInset + maxOf(
                    safeDrawingPadding.calculateLeftPadding(layoutDirection),
                    safeDrawingPadding.calculateRightPadding(layoutDirection),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = landscapeHorizontalInset,
                            end = landscapeHorizontalInset,
                            top = safeDrawingPadding.calculateTopPadding(),
                            bottom = safeDrawingPadding.calculateBottomPadding(),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    ) {
                        val artworkSize = minOf(maxWidth, maxHeight)
                            .coerceAtLeast(54.dp)
                        StreamingArtworkPlaceholder(
                            artworkSize = artworkSize,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    ) {
                        val sectionWidth = maxWidth.coerceAtMost(560.dp)
                        val viewportHeight = maxHeight
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(sectionWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = viewportHeight)
                                    .padding(
                                        start = 4.dp,
                                        end = 4.dp,
                                        top = 8.dp,
                                        bottom = 8.dp,
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                controlsSection(sectionWidth)
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = systemNavigationBarBottomInset + 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        val contentWidth = (
                            maxWidth - StreamingHorizontalContentInset -
                                StreamingHorizontalContentInset
                            )
                            .coerceAtLeast(1.dp)
                            .coerceAtMost(560.dp)

                        BoxWithConstraints(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(contentWidth)
                                .fillMaxHeight()
                                .padding(top = 8.dp, bottom = 18.dp),
                        ) {
                            val artworkSize = minOf(maxWidth, maxHeight)
                                .coerceAtLeast(54.dp)
                            StreamingArtworkPlaceholder(
                                artworkSize = artworkSize,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val controlsWidth = (
                            maxWidth - StreamingHorizontalContentInset -
                                StreamingHorizontalContentInset
                            )
                            .coerceAtLeast(1.dp)
                            .coerceAtMost(560.dp)

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(controlsWidth)
                                .offset(y = -50.dp),
                        ) {
                            controlsSection(controlsWidth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingArtworkPlaceholder(
    artworkSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(artworkSize)
            .clip(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_stream),
            contentDescription = null,
            modifier = Modifier.size(artworkSize * 0.27f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatStreamTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L

    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L,
    )
}
