package com.rovia.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.lyrics.EmbeddedLyricsReader
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.LyricLine
import com.rovia.music.ui.components.SyncedLyrics
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.floor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    playbackController: PlaybackController,
    onBack: () -> Unit = {},
) {
    val controller by playbackController.mediaController
        .collectAsStateWithLifecycle()
    val queueState by playbackController.queueState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext

    var currentMediaItem by remember(controller) {
        mutableStateOf(controller?.currentMediaItem)
    }

    var currentLyrics by remember(currentMediaItem) {
        mutableStateOf<SyncedLyrics?>(null)
    }

    var isLyricsLoading by remember(currentMediaItem) {
        mutableStateOf(currentMediaItem != null)
    }

    var currentTitle by remember(controller) {
        mutableStateOf(controller?.mediaMetadata?.title?.toString())
    }

    var currentArtist by remember(controller) {
        mutableStateOf(controller?.mediaMetadata?.artist?.toString())
    }

    var currentArtworkUri by remember(controller) {
        mutableStateOf(
            controller?.currentMediaItem?.localConfiguration?.uri?.toString(),
        )
    }

    var currentPosition by remember(controller) {
        mutableLongStateOf(controller?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }

    val lyricLines = remember(currentLyrics) {
        val sourceLines = currentLyrics?.lines.orEmpty()
        val lineTimestampSorted = sourceLines.sortedBy { it.startTimeMs }
        val hasLineTimestampProgression = lineTimestampSorted
            .zipWithNext()
            .any { (current, next) -> next.startTimeMs > current.startTimeMs }

        if (hasLineTimestampProgression) {
            lineTimestampSorted
        } else {
            sourceLines.sortedBy { line ->
                line.words.firstOrNull()?.startTimeMs ?: line.startTimeMs
            }
        }
    }

    val lyricPreviewStartTimes = remember(lyricLines) {
        val lineStartTimes = lyricLines.map { it.startTimeMs }
        val hasLineTimestampProgression = lineStartTimes
            .zipWithNext()
            .any { (current, next) -> next > current }

        if (hasLineTimestampProgression) {
            lineStartTimes
        } else {
            lyricLines.map { line ->
                line.words.firstOrNull()?.startTimeMs ?: line.startTimeMs
            }
        }
    }

    val hasTimedLyricProgression = remember(lyricLines, lyricPreviewStartTimes) {
        val hasLineTimestampProgression = lyricPreviewStartTimes
            .zipWithNext()
            .any { (current, next) -> next > current }
        val hasWordTimestampProgression = lyricLines.any { line ->
            line.words.zipWithNext().any { (current, next) ->
                next.startTimeMs > current.startTimeMs
            }
        }

        hasLineTimestampProgression || hasWordTimestampProgression
    }

    val currentLyricIndex = remember(
        lyricLines,
        lyricPreviewStartTimes,
        currentPosition,
        hasTimedLyricProgression,
    ) {
        when {
            lyricLines.isEmpty() -> -1
            !hasTimedLyricProgression -> 0
            else -> lyricPreviewStartTimes.indexOfLast { it <= currentPosition }
        }
    }

    val currentLyricTarget = if (isLyricsLoading) {
        null
    } else {
        lyricLines.getOrNull(currentLyricIndex)?.let { line ->
            CurrentLyricPreviewTarget(
                trackKey = listOf(
                    currentMediaItem?.mediaId.orEmpty(),
                    currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(),
                ).joinToString("|"),
                lineIndex = currentLyricIndex,
                line = line,
            )
        }
    }

    var durationMs by remember(controller) {
        mutableLongStateOf(
            controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
        )
    }

    var isPlaying by remember(controller) {
        mutableStateOf(controller?.isPlaying == true)
    }

    var showPauseIcon by remember(controller) {
        mutableStateOf(
            controller?.let {
                it.playWhenReady && it.playbackState != Player.STATE_ENDED
            } == true,
        )
    }

    var hasMedia by remember(controller) {
        mutableStateOf(controller?.currentMediaItem != null)
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    var hasNext by remember(controller) {
        mutableStateOf(controller?.hasNextMediaItem() == true)
    }

    var dragPosition by remember(controller) {
        mutableStateOf<Float?>(null)
    }

    var artworkHeight by remember { mutableStateOf(0.dp) }
    val latestDragPosition = rememberUpdatedState(dragPosition)
    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val controlsVerticalOffset = 84.dp
    val lyricsVerticalCompensation = 60.dp

    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    DisposableEffect(controller) {
        val player = controller

        if (player == null) {
            currentTitle = null
            currentArtist = null
            currentArtworkUri = null
            currentMediaItem = null
            currentPosition = 0L
            durationMs = 0L
            isPlaying = false
            showPauseIcon = false
            hasMedia = false
            hasNext = false

            onDispose {}
        } else {
            fun updatePlayerState(activePlayer: Player) {
                currentMediaItem = activePlayer.currentMediaItem
                currentTitle = activePlayer.mediaMetadata.title?.toString()
                currentArtist = activePlayer.mediaMetadata.artist?.toString()
                currentArtworkUri = activePlayer.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
                currentPosition = activePlayer.currentPosition.coerceAtLeast(0L)
                durationMs = activePlayer.duration.takeIf {
                    it != C.TIME_UNSET && it > 0L
                } ?: 0L
                isPlaying = activePlayer.isPlaying
                showPauseIcon = activePlayer.playWhenReady &&
                    activePlayer.playbackState != Player.STATE_ENDED
                hasMedia = activePlayer.currentMediaItem != null
                hasNext = activePlayer.hasNextMediaItem()
            }

            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updatePlayerState(player)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
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

    LaunchedEffect(currentMediaItem) {
        val mediaItem = currentMediaItem

        if (mediaItem == null) {
            currentLyrics = null
            isLyricsLoading = false
        } else {
            currentLyrics = null
            isLyricsLoading = true
            currentLyrics = EmbeddedLyricsReader.read(context, mediaItem)
            isLyricsLoading = false
        }
    }

    LaunchedEffect(controller, isPlaying, hasMedia) {
        val activeController = controller ?: return@LaunchedEffect

        while (isPlaying && hasMedia) {
            if (latestDragPosition.value == null) {
                currentPosition = activeController.currentPosition.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    if (showLyrics) {
        PlayerLyricScreen(
            title = currentTitle,
            artist = currentArtist,
            mediaItem = currentMediaItem,
            positionMs = currentPosition,
            isPlaying = isPlaying,
            onBack = { showLyrics = false },
            onSeek = { positionMs ->
                val seekPosition = positionMs.coerceAtLeast(0L)
                controller?.seekTo(seekPosition)
                currentPosition = seekPosition
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkBackdrop(
                artworkUri = currentArtworkUri,
                foregroundArtworkHeight = artworkHeight,
                modifier = Modifier.fillMaxSize(),
            )

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
                    val contentWidth = (maxWidth - 60.dp)
                        .coerceAtLeast(1.dp)
                        .coerceAtMost(560.dp)

                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(contentWidth)
                            .fillMaxHeight()
                            .padding(top = 8.dp, bottom = 18.dp),
                    ) {
                        val titleGap = 20.dp
                        val titleBlockHeight = 60.dp
                        val availableArtworkHeight = (
                            maxHeight - titleGap - titleBlockHeight
                        ).coerceAtLeast(54.dp)
                        val artworkSize = minOf(maxWidth, availableArtworkHeight)
                            .coerceAtLeast(54.dp)
                        val groupTop = (
                            maxHeight - artworkSize - titleGap - titleBlockHeight
                        ).coerceAtLeast(0.dp) / 2
                        val nextArtworkHeight = (
                            statusBarTopInset +
                                32.dp +
                                8.dp +
                                groupTop +
                                artworkSize +
                                titleGap / 2
                            ).coerceAtLeast(0.dp)

                        SideEffect {
                            if (artworkHeight != nextArtworkHeight) {
                                artworkHeight = nextArtworkHeight
                            }
                        }

                        Column(
                            modifier = Modifier
                                .offset(
                                    y = groupTop + artworkSize + titleGap - 24.dp,
                                )
                                .fillMaxWidth(),
                        ) {
                            Text(
                                text = when {
                                    controller == null -> stringResource(R.string.player_connecting)
                                    !hasMedia -> stringResource(R.string.player_no_track)
                                    else -> currentTitle
                                        ?.takeIf(String::isNotBlank)
                                        ?: stringResource(R.string.player_unknown_track)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )

                            if (hasMedia) {
                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = currentArtist
                                        ?.takeIf(String::isNotBlank)
                                        ?: stringResource(R.string.player_unknown_artist),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )

                            }
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val controlsWidth = (maxWidth - 60.dp)
                        .coerceAtLeast(1.dp)
                        .coerceAtMost(560.dp)

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(controlsWidth)
                            .offset(y = -controlsVerticalOffset),
                    ) {
                        if (hasMedia) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                            ) {
                                PlayerLyricPreview(
                                    target = currentLyricTarget,
                                    positionMs = currentPosition,
                                    isPlaying = isPlaying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = 6.dp),
                                )
                            }
                        }

                        val sliderMaximum = durationMs
                            .takeIf { it > 0L }
                            ?.toFloat()
                            ?: 1f
                        val sliderValue = (
                            dragPosition ?: currentPosition.toFloat()
                        ).coerceIn(0f, sliderMaximum)
                        val sliderState = key(currentTitle, durationMs) {
                            rememberSliderState(
                                value = sliderValue,
                                trackRange = 0f..sliderMaximum,
                            )
                        }

                        LaunchedEffect(sliderValue, sliderMaximum) {
                            sliderState.value = sliderValue
                        }

                        Slider(
                            state = sliderState,
                            onValueChange = { value ->
                                sliderState.value = value
                                dragPosition = value
                            },
                            onValueChangeFinished = {
                                dragPosition?.let { position ->
                                    controller?.seekTo(position.toLong())
                                    currentPosition = position.toLong()
                                }
                                dragPosition = null
                            },
                            enabled = controller != null && hasMedia && durationMs > 0L,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTime(
                                    (dragPosition?.toLong() ?: currentPosition)
                                        .coerceAtLeast(0L),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )

                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val playbackButtonGroupWidth = (
                            controlsWidth * (2f / 3f) + 64.dp
                        ).coerceAtMost(controlsWidth).coerceAtMost(440.dp)
                        val playbackColorScheme = MaterialTheme.colorScheme
                        val idlePlaybackContainerColor =
                            playbackColorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                        val idlePlaybackContentColor = playbackColorScheme.onSurface
                        val activePlaybackContainerColor = playbackColorScheme.primaryContainer
                        val activePlaybackContentColor = playbackColorScheme.onPrimaryContainer

                        val previousInteractionSource = remember {
                            MutableInteractionSource()
                        }
                        val playPauseInteractionSource = remember {
                            MutableInteractionSource()
                        }
                        val nextInteractionSource = remember {
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
                                    if (activeController.playbackState == Player.STATE_ENDED) {
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
                                        FilledIconButton(
                                            enabled = hasMedia,
                                            onClick = {
                                                controller?.seekToPreviousMediaItem()
                                            },
                                            interactionSource = previousInteractionSource,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .animateWidth(
                                                    interactionSource = previousInteractionSource,
                                                    compressionLimit = 16.dp,
                                                ),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = idlePlaybackContainerColor,
                                                contentColor = idlePlaybackContentColor,
                                            ),
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_skip_previous),
                                                contentDescription = stringResource(R.string.player_previous),
                                                modifier = Modifier.size(30.dp),
                                            )
                                        }
                                    },
                                    menuContent = {},
                                )

                                customItem(
                                    buttonGroupContent = {
                                        FilledIconToggleButton(
                                            checked = showPauseIcon,
                                            onCheckedChange = {
                                                togglePlayback()
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
                                                    if (showPauseIcon) R.drawable.ic_pause else R.drawable.ic_play,
                                                ),
                                                contentDescription = stringResource(
                                                    if (showPauseIcon) R.string.player_pause else R.string.player_play,
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
                                            enabled = hasMedia && hasNext,
                                            onClick = {
                                                controller?.seekToNextMediaItem()
                                            },
                                            interactionSource = nextInteractionSource,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .animateWidth(
                                                    interactionSource = nextInteractionSource,
                                                    compressionLimit = 16.dp,
                                                ),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = idlePlaybackContainerColor,
                                                contentColor = idlePlaybackContentColor,
                                            ),
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_skip_next),
                                                contentDescription = stringResource(R.string.player_next),
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
                                .height(64.dp)
                                .offset(y = lyricsVerticalCompensation),
                        ) {
                            FilledIconButton(
                                onClick = { showLyrics = true },
                                enabled = hasMedia,
                                modifier = Modifier.align(Alignment.CenterStart),
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lyrics),
                                    contentDescription = stringResource(R.string.player_lyrics),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            AudioOutputIndicator(
                                enabled = hasMedia,
                                modifier = Modifier.align(Alignment.Center),
                            )

                            FilledIconButton(
                                onClick = { showQueueSheet = true },
                                enabled = hasMedia,
                                modifier = Modifier.align(Alignment.CenterEnd),
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_queue),
                                    contentDescription = stringResource(R.string.player_queue),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (showQueueSheet) {
                QueueSheet(
                    queueState = queueState,
                    onDismissRequest = { showQueueSheet = false },
                    onTrackSelected = { index ->
                        playbackController.playQueueItem(index)
                    },
                )
            }
        }
    }
}


private data class CurrentLyricPreviewTarget(
    val trackKey: String,
    val lineIndex: Int,
    val line: LyricLine,
)

@Composable
private fun PlayerLyricPreview(
    target: CurrentLyricPreviewTarget?,
    positionMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    if (target == null) return

    AnimatedContent(
        targetState = target,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(260)) +
                    slideInVertically(animationSpec = tween(260)) { height ->
                        (height * 0.28f).toInt()
                    }
                ).togetherWith(
                fadeOut(animationSpec = tween(180)) +
                    slideOutVertically(animationSpec = tween(180)) { height ->
                        -(height * 0.28f).toInt()
                    },
            )
        },
        modifier = modifier.height(44.dp),
        contentAlignment = Alignment.CenterStart,
        label = "player-current-lyric-preview",
    ) { preview ->
        val previewPosition = remember(preview.trackKey) {
            mutableLongStateOf(positionMs.coerceAtLeast(0L))
        }
        var clockAnchorPositionMs by remember(preview.trackKey) {
            mutableLongStateOf(positionMs.coerceAtLeast(0L))
        }
        var clockAnchorFrameTimeNanos by remember(preview.trackKey) {
            mutableLongStateOf(0L)
        }
        var hasClockAnchor by remember(preview.trackKey) {
            mutableStateOf(false)
        }

        LaunchedEffect(preview.trackKey, positionMs, isPlaying) {
            val frameTimeNanos = withFrameNanos { it }
            clockAnchorPositionMs = positionMs.coerceAtLeast(0L)
            clockAnchorFrameTimeNanos = frameTimeNanos
            hasClockAnchor = true

            if (!isPlaying) {
                previewPosition.longValue = positionMs.coerceAtLeast(0L)
            }
        }

        LaunchedEffect(preview.trackKey, isPlaying) {
            if (!isPlaying) {
                previewPosition.longValue = positionMs.coerceAtLeast(0L)
                return@LaunchedEffect
            }

            while (true) {
                withFrameNanos { frameTimeNanos ->
                    if (!hasClockAnchor) {
                        previewPosition.longValue = positionMs.coerceAtLeast(0L)
                    } else {
                        val elapsedMs = (
                            frameTimeNanos - clockAnchorFrameTimeNanos
                            ).coerceAtLeast(0L) / 1_000_000L
                        previewPosition.longValue = (
                            clockAnchorPositionMs + elapsedMs
                            ).coerceAtLeast(0L)
                    }
                }
            }
        }

        val line = preview.line
        val lineText = remember(line) {
            line.words.joinToString(separator = " ") { it.text }
        }
        val hasWordTiming = remember(line) {
            isWordSyncedPreviewLine(line)
        }
        val revealedCharacters = remember(
            line,
            lineText,
            previewPosition.longValue,
            hasWordTiming,
        ) {
            if (hasWordTiming) {
                calculatePlayerPreviewRevealedCharacters(
                    line = line,
                    positionMs = previewPosition.longValue,
                )
            } else {
                lineText.length.toFloat()
            }
        }
        val inactiveColor = Color.White.copy(alpha = 0.58f)
        val previewStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        var textLayoutResult by remember(line, lineText) {
            mutableStateOf<TextLayoutResult?>(null)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = lineText,
                modifier = Modifier.fillMaxWidth(),
                color = if (hasWordTiming) inactiveColor else Color.White,
                style = previewStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    val previous = textLayoutResult
                    if (
                        previous == null ||
                        previous.layoutInput != result.layoutInput ||
                        previous.size != result.size ||
                        previous.lineCount != result.lineCount
                    ) {
                        textLayoutResult = result
                    }
                },
            )

            if (hasWordTiming) {
                Text(
                    text = lineText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            val layout = textLayoutResult
                            if (layout == null || revealedCharacters <= 0f) {
                                return@drawWithContent
                            }

                            val revealPath = buildPlayerLyricRevealPath(
                                layout = layout,
                                revealedCharacters = revealedCharacters,
                            )
                            clipPath(revealPath) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    color = Color.White,
                    style = previewStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun isWordSyncedPreviewLine(line: LyricLine): Boolean {
    if (line.words.size < 2) return false

    return line.words.zipWithNext().any { (current, next) ->
        next.startTimeMs > current.startTimeMs
    }
}

private fun calculatePlayerPreviewRevealedCharacters(
    line: LyricLine,
    positionMs: Long,
): Float {
    if (line.words.isEmpty()) return 0f

    val wordOffsets = ArrayList<Int>(line.words.size)
    var offset = 0
    line.words.forEachIndexed { index, word ->
        wordOffsets += offset
        offset += word.text.length
        if (index < line.words.lastIndex) {
            offset += 1
        }
    }

    val lastWordIndex = line.words.lastIndex
    val totalCharacters = wordOffsets[lastWordIndex] + line.words[lastWordIndex].text.length

    for (index in line.words.indices) {
        val word = line.words[index]
        val wordOffset = wordOffsets[index]

        if (positionMs < word.startTimeMs) {
            return wordOffset.toFloat()
        }

        val nextWord = line.words.getOrNull(index + 1)
        if (nextWord != null) {
            val wordDuration = nextWord.startTimeMs - word.startTimeMs
            if (wordDuration > 0L) {
                if (positionMs < nextWord.startTimeMs) {
                    val progress = (
                        (positionMs - word.startTimeMs).toFloat() / wordDuration.toFloat()
                        ).coerceIn(0f, 1f)
                    return wordOffset + word.text.length * progress
                }
                continue
            }
            continue
        }

        val finalWordDuration = estimatePlayerPreviewWordDurationMs(
            line = line,
            wordIndex = index,
        )
        val progress = (
            (positionMs - word.startTimeMs).toFloat() / finalWordDuration.toFloat()
            ).coerceIn(0f, 1f)
        return wordOffset + word.text.length * progress
    }

    return totalCharacters.toFloat()
}

private fun estimatePlayerPreviewWordDurationMs(
    line: LyricLine,
    wordIndex: Int,
): Long {
    val word = line.words.getOrNull(wordIndex) ?: return 500L
    val previousWord = line.words.getOrNull(wordIndex - 1) ?: return 500L
    val interval = word.startTimeMs - previousWord.startTimeMs
    return if (interval > 0L) interval.coerceIn(250L, 900L) else 500L
}

private fun buildPlayerLyricRevealPath(
    layout: TextLayoutResult,
    revealedCharacters: Float,
): Path {
    val path = Path()
    val textLength = layout.layoutInput.text.length
    val safeReveal = revealedCharacters.coerceIn(0f, textLength.toFloat())
    if (safeReveal <= 0f) return path

    for (lineIndex in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex, visibleEnd = true)
        if (lineEnd <= lineStart || safeReveal <= lineStart.toFloat()) continue

        val lineTop = layout.getLineTop(lineIndex)
        val lineBottom = layout.getLineBottom(lineIndex)
        val lineLeft = minOf(
            layout.getLineLeft(lineIndex),
            layout.getLineRight(lineIndex),
        )
        val lineRight = maxOf(
            layout.getLineLeft(lineIndex),
            layout.getLineRight(lineIndex),
        )
        val directionIndex = lineStart.coerceIn(
            0,
            (textLength - 1).coerceAtLeast(0),
        )
        val isRtl = layout.getParagraphDirection(directionIndex) == ResolvedTextDirection.Rtl

        if (safeReveal >= lineEnd.toFloat()) {
            path.addRect(Rect(lineLeft, lineTop, lineRight, lineBottom))
            continue
        }

        val clampedReveal = safeReveal.coerceIn(lineStart.toFloat(), lineEnd.toFloat())
        val lowerOffset = floor(clampedReveal).toInt().coerceIn(lineStart, lineEnd)
        val fraction = (clampedReveal - lowerOffset).coerceIn(0f, 1f)
        val lowerCursor = layout.getCursorRect(lowerOffset)
        val upperCursor = layout.getCursorRect((lowerOffset + 1).coerceAtMost(lineEnd))
        val lowerBoundary = if (isRtl) lowerCursor.right else lowerCursor.left
        val upperBoundary = if (isRtl) upperCursor.right else upperCursor.left
        val boundary = lowerBoundary + (upperBoundary - lowerBoundary) * fraction

        if (isRtl) {
            path.addRect(
                Rect(
                    boundary.coerceIn(lineLeft, lineRight),
                    lineTop,
                    lineRight,
                    lineBottom,
                ),
            )
        } else {
            path.addRect(
                Rect(
                    lineLeft,
                    lineTop,
                    boundary.coerceIn(lineLeft, lineRight),
                    lineBottom,
                ),
            )
        }
    }

    return path
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