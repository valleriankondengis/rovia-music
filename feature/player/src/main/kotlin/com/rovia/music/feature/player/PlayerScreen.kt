@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.AlbumArtwork
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

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
        if (isLandscape) {
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
        } else {
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

@Composable
private fun PortraitPlayerContent(
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
        androidx.compose.animation.core.FiniteAnimationSpec<Float>,
    onSeekPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    onLyricSeek: (Long) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                ),
    ) {
        PlayerActionButtonGroup(
            hasLyrics = hasLyrics,
            isLyricsLoading = isLyricsLoading,
            isLyricsVisible = isLyricsVisible,
            onToggleLyrics = onToggleLyrics,
            onClose = onClose,
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        PlayerArtworkOrLyrics(
            track = track,
            playbackState = playbackState,
            lyrics = lyrics,
            hasLyrics = hasLyrics,
            isLyricsLoading = isLyricsLoading,
            isLyricsVisible = isLyricsVisible,
            motionEffectsSpec = motionEffectsSpec,
            onLyricSeek = onLyricSeek,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(300.dp),
        )

        Text(
            text =
                track?.title
                    ?: stringResource(
                        R.string.player_no_track,
                    ),
            style =
                MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = 24.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        repeatDelayMillis = 900,
                        velocity = 35.dp,
                    ),
        )

        Text(
            text =
                track?.artist
                    ?: stringResource(
                        R.string.player_unknown_artist,
                    ),
            style =
                MaterialTheme.typography.bodyLarge,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        repeatDelayMillis = 900,
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
                    .padding(top = 28.dp),
        )

        TrackTechnicalInfo(
            track = track,
        )

        SeekTimeRow(
            positionMs = seekPosition,
            durationMs = duration,
        )

        Spacer(
            modifier = Modifier.height(32.dp),
        )

        PlaybackButtonGroup(
            isPlaying = displayedIsPlaying,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )

        Spacer(
            modifier = Modifier.height(16.dp),
        )

        PlaybackOptionsButtonGroup(
            repeatMode = playbackState.repeatMode,
            shuffleEnabled = playbackState.shuffleEnabled,
            onRepeatModeChange = onRepeatModeChange,
            onShuffleEnabledChange = onShuffleEnabledChange,
        )
    }
}

@Composable
private fun LandscapePlayerContent(
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
        androidx.compose.animation.core.FiniteAnimationSpec<Float>,
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
                    modifier =
                        Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(
                            animationSpec =
                                motionEffectsSpec,
                        ).togetherWith(
                            fadeOut(
                                animationSpec =
                                    motionEffectsSpec,
                            ),
                        ).using(null)
                    },
                    label =
                        "player-landscape-content-transition",
                ) { showLyrics ->
                    if (showLyrics) {
                        LyricsContent(
                            lyrics = lyrics,
                            hasLyrics = hasLyrics,
                            isLyricsLoading =
                                isLyricsLoading,
                            playbackPositionMs =
                                playbackState.positionMs,
                            onSeek = onLyricSeek,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier.fillMaxSize(),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            AlbumArtwork(
                                artworkUri =
                                    track?.artworkUri,
                                fallbackText =
                                    track?.title
                                        ?: "Rovia",
                                contentDescription = null,
                                modifier =
                                    Modifier.size(
                                        artworkSize,
                                    ),
                                size = artworkSize,
                                shape =
                                    MaterialTheme
                                        .shapes
                                        .extraLarge,
                            )
                        }
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

@Composable
private fun PlayerArtworkOrLyrics(
    track: Track?,
    playbackState: PlaybackState,
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    motionEffectsSpec:
        androidx.compose.animation.core.FiniteAnimationSpec<Float>,
    onLyricSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isLyricsVisible,
        modifier = modifier,
        transitionSpec = {
            fadeIn(
                animationSpec = motionEffectsSpec,
            ).togetherWith(
                fadeOut(
                    animationSpec = motionEffectsSpec,
                ),
            ).using(null)
        },
        label = "player-content-transition",
    ) { showLyrics ->
        if (showLyrics) {
            LyricsContent(
                lyrics = lyrics,
                hasLyrics = hasLyrics,
                isLyricsLoading = isLyricsLoading,
                playbackPositionMs =
                    playbackState.positionMs,
                onSeek = onLyricSeek,
                modifier =
                    Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center,
            ) {
                AlbumArtwork(
                    artworkUri =
                        track?.artworkUri,
                    fallbackText =
                        track?.title
                            ?: "Rovia",
                    contentDescription = null,
                    modifier =
                        Modifier.size(300.dp),
                    size = 300.dp,
                    shape =
                        MaterialTheme
                            .shapes
                            .extraLarge,
                )
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: SyncedLyrics?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    playbackPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (
        !isLyricsLoading &&
        hasLyrics &&
        lyrics != null
    ) {
        LyricViewer(
            lyrics = lyrics,
            positionMs = playbackPositionMs,
            onSeek = onSeek,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter =
                    painterResource(
                        CoreUiR.drawable.ic_lyrics,
                    ),
                contentDescription =
                    stringResource(
                        R.string.player_show_lyrics,
                    ),
                modifier =
                    Modifier.size(48.dp),
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerActionButtonGroup(
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
) {
    ButtonGroup(
        horizontalArrangement =
            Arrangement.spacedBy(
                ButtonGroupDefaults
                    .ConnectedSpaceBetween,
            ),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(
                menuState = menuState,
            )
        },
    ) {
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onClose,
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
                        IconButtonDefaults.shapes(
                            shape =
                                ButtonGroupDefaults
                                    .connectedLeadingButtonShape,
                            pressedShape =
                                ButtonGroupDefaults
                                    .connectedLeadingButtonPressShape,
                        ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable
                                    .ic_keyboard_arrow_down,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.player_close,
                            ),
                        modifier =
                            Modifier.size(22.dp),
                    )
                }
            },
            menuContent = {},
        )

        customItem(
            buttonGroupContent = {
                FilledIconToggleButton(
                    checked = isLyricsVisible,
                    onCheckedChange = {
                        onToggleLyrics()
                    },
                    modifier =
                        Modifier.size(48.dp),
                    colors =
                        IconButtonDefaults
                            .filledIconToggleButtonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceContainerHigh,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                                checkedContainerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer,
                                checkedContentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer,
                            ),
                    shapes =
                        IconButtonDefaults.toggleableShapes(
                            shape =
                                ButtonGroupDefaults
                                    .connectedTrailingButtonShape,
                            pressedShape =
                                ButtonGroupDefaults
                                    .connectedTrailingButtonPressShape,
                            checkedShape =
                                ButtonGroupDefaults
                                    .connectedButtonCheckedShape,
                        ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_lyrics,
                            ),
                        contentDescription =
                            stringResource(
                                if (isLyricsVisible) {
                                    R.string.player_hide_lyrics
                                } else {
                                    R.string.player_show_lyrics
                                },
                            ),
                        modifier =
                            Modifier.size(22.dp),
                    )
                }
            },
            menuContent = {},
        )
    }
}

@Composable
private fun TrackTechnicalInfo(
    track: Track?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text =
                formatSampleRate(
                    track?.sampleRateHz,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text = "•",
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                ),
        )

        Text(
            text =
                formatBitrate(
                    track?.bitrateBps,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text = "•",
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                ),
        )

        Text(
            text =
                formatAudioFormat(
                    track?.mimeType,
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
private fun SeekTimeRow(
    positionMs: Float,
    durationMs: Long,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                formatDuration(
                    positionMs.toLong(),
                ),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )

        Text(
            text =
                formatDuration(durationMs),
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackButtonGroup(
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

    Box(
        modifier =
            Modifier.fillMaxWidth(),
        contentAlignment =
            Alignment.Center,
    ) {
        ButtonGroup(
            horizontalArrangement =
                Arrangement.spacedBy(16.dp),
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(
                    menuState = menuState,
                )
            },
        ) {
            customItem(
                buttonGroupContent = {
                    FilledIconButton(
                        onClick = onPrevious,
                        interactionSource =
                            previousSource,
                        modifier =
                            Modifier
                                .size(64.dp)
                                .animateWidth(
                                    interactionSource =
                                        previousSource,
                                    compressionLimit =
                                        16.dp,
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
                                Modifier.size(30.dp),
                        )
                    }
                },
                menuContent = {},
            )

            customItem(
                buttonGroupContent = {
                    FilledIconToggleButton(
                        checked = isPlaying,
                        onCheckedChange = {
                            onPlayPause()
                        },
                        interactionSource =
                            playPauseSource,
                        modifier =
                            Modifier
                                .size(64.dp)
                                .animateWidth(
                                    interactionSource =
                                        playPauseSource,
                                    compressionLimit =
                                        16.dp,
                                ),
                        colors =
                            IconButtonDefaults
                                .filledIconToggleButtonColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceContainerHigh,
                                    contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                    checkedContainerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .primaryContainer,
                                    checkedContentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onPrimaryContainer,
                                ),
                        shapes =
                            IconButtonDefaults
                                .toggleableShapes(),
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
                                Modifier.size(32.dp),
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
                                .size(64.dp)
                                .animateWidth(
                                    interactionSource =
                                        nextSource,
                                    compressionLimit =
                                        16.dp,
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
                                Modifier.size(30.dp),
                        )
                    }
                },
                menuContent = {},
            )
        }
    }
}

@Composable
private fun PlaybackOptionsButtonGroup(
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
) {
    val repeatSource =
        remember {
            MutableInteractionSource()
        }

    val shuffleSource =
        remember {
            MutableInteractionSource()
        }

    val nextRepeatMode =
        when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

    val leadingNormalShape =
        RoundedCornerShape(
            topStart = 32.dp,
            bottomStart = 32.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
        )

    val leadingPressedShape =
        RoundedCornerShape(
            topStart = 32.dp,
            bottomStart = 32.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
        )

    val trailingNormalShape =
        RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = 32.dp,
            bottomEnd = 32.dp,
        )

    val trailingPressedShape =
        RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = 32.dp,
            bottomEnd = 32.dp,
        )

    Box(
        modifier =
            Modifier.fillMaxWidth(),
        contentAlignment =
            Alignment.Center,
    ) {
        ButtonGroup(
            horizontalArrangement =
                Arrangement.spacedBy(0.dp),
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(
                    menuState = menuState,
                )
            },
        ) {
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = repeatMode != RepeatMode.OFF,
                        onCheckedChange = {
                            onRepeatModeChange(
                                nextRepeatMode,
                            )
                        },
                        interactionSource = repeatSource,
                        modifier =
                            Modifier
                                .width(112.dp)
                                .height(64.dp)
                                .animateWidth(
                                    interactionSource =
                                        repeatSource,
                                    compressionLimit =
                                        16.dp,
                                ),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceContainerHigh,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                                checkedContainerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .secondaryContainer,
                                checkedContentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSecondaryContainer,
                            ),
                        shapes =
                            ButtonGroupDefaults
                                .connectedLeadingButtonShapes(
                                    shape =
                                        leadingNormalShape,
                                    pressedShape =
                                        leadingPressedShape,
                                    checkedShape =
                                        leadingNormalShape,
                                ),
                        contentPadding =
                            PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (
                                            repeatMode ==
                                                RepeatMode.ONE
                                        ) {
                                            CoreUiR.drawable
                                                .ic_repeat_one
                                        } else {
                                            CoreUiR.drawable
                                                .ic_repeat
                                        },
                                    ),
                                contentDescription =
                                    when (repeatMode) {
                                        RepeatMode.OFF ->
                                            stringResource(
                                                R.string
                                                    .player_repeat_off,
                                            )

                                        RepeatMode.ALL ->
                                            stringResource(
                                                R.string
                                                    .player_repeat_all,
                                            )

                                        RepeatMode.ONE ->
                                            stringResource(
                                                R.string
                                                    .player_repeat_one,
                                            )
                                    },
                                modifier =
                                    Modifier.size(28.dp),
                            )
                        }
                    }
                },
                menuContent = {},
            )

            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = shuffleEnabled,
                        onCheckedChange =
                            onShuffleEnabledChange,
                        interactionSource = shuffleSource,
                        modifier =
                            Modifier
                                .width(112.dp)
                                .height(64.dp)
                                .animateWidth(
                                    interactionSource =
                                        shuffleSource,
                                    compressionLimit =
                                        16.dp,
                                ),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceContainerHigh,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                                checkedContainerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .secondaryContainer,
                                checkedContentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSecondaryContainer,
                            ),
                        shapes =
                            ButtonGroupDefaults
                                .connectedTrailingButtonShapes(
                                    shape =
                                        trailingNormalShape,
                                    pressedShape =
                                        trailingPressedShape,
                                    checkedShape =
                                        trailingNormalShape,
                                ),
                        contentPadding =
                            PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        CoreUiR.drawable
                                            .ic_shuffle,
                                    ),
                                contentDescription =
                                    if (shuffleEnabled) {
                                        stringResource(
                                            R.string
                                                .player_shuffle_on,
                                        )
                                    } else {
                                        stringResource(
                                            R.string
                                                .player_shuffle_off,
                                        )
                                    },
                                modifier =
                                    Modifier.size(28.dp),
                            )
                        }
                    }
                },
                menuContent = {},
            )
        }
    }
}

@Composable
private fun WavySeekBar(
    positionMs: Float,
    durationMs: Long,
    onPositionChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val wavelength = 48.dp

    val wavelengthPx =
        with(density) {
            wavelength.toPx()
        }

    var waveOffset by remember {
        mutableFloatStateOf(0f)
    }

    var widthPx by remember {
        mutableFloatStateOf(0f)
    }

    val primaryColor =
        MaterialTheme.colorScheme.primary

    val trackColor =
        MaterialTheme.colorScheme.outlineVariant

    val progress =
        if (durationMs > 0L) {
            (
                positionMs /
                    durationMs.toFloat()
            ).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun positionFromX(
        x: Float,
    ): Float {
        if (
            widthPx <= 0f ||
            durationMs <= 0L
        ) {
            return 0f
        }

        return (
            x.coerceIn(
                0f,
                widthPx,
            ) / widthPx
        ) * durationMs
    }

    LaunchedEffect(wavelengthPx) {
        while (true) {
            waveOffset =
                (
                    waveOffset +
                        wavelengthPx / 60f
                ) % wavelengthPx

            delay(16L)
        }
    }

    Canvas(
        modifier =
            modifier
                .height(32.dp)
                .onSizeChanged { size ->
                    widthPx =
                        size.width.toFloat()
                }
                .pointerInput(
                    widthPx,
                    durationMs,
                ) {
                    detectTapGestures { offset ->
                        onPositionChange(
                            positionFromX(
                                offset.x,
                            ),
                        )
                        onSeekFinished()
                    }
                }
                .pointerInput(
                    widthPx,
                    durationMs,
                ) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onPositionChange(
                                positionFromX(
                                    offset.x,
                                ),
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()

                            onPositionChange(
                                positionFromX(
                                    change.position.x,
                                ),
                            )
                        },
                        onDragEnd = {
                            onSeekFinished()
                        },
                    )
                },
    ) {
        if (widthPx <= 0f) {
            return@Canvas
        }

        val centerY =
            size.height / 2f

        val amplitude =
            3.5.dp.toPx()

        val strokeWidth =
            4.dp.toPx()

        val dotRadius =
            8.dp.toPx()

        val trackGap =
            10.dp.toPx()

        val progressX =
            size.width * progress

        val trackStart =
            progressX +
                dotRadius +
                trackGap

        if (trackStart < size.width) {
            drawLine(
                color = trackColor,
                start =
                    Offset(
                        x = trackStart,
                        y = centerY,
                    ),
                end =
                    Offset(
                        x = size.width,
                        y = centerY,
                    ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        if (progressX > 0f) {
            val path = Path()

            path.moveTo(
                x = 0f,
                y = centerY,
            )

            var x = 1.5f

            while (x <= progressX) {
                val phase =
                    (
                        (x - waveOffset) /
                            wavelengthPx
                    ) *
                        (
                            2f *
                                PI.toFloat()
                        )

                val wave =
                    amplitude *
                        sin(
                            phase.toDouble(),
                        ).toFloat()

                val ramp =
                    (
                        x /
                            wavelengthPx
                    ).coerceIn(
                        0f,
                        1f,
                    )

                path.lineTo(
                    x = x,
                    y =
                        centerY +
                            wave * ramp,
                )

                x += 1.5f
            }

            drawPath(
                path = path,
                color = primaryColor,
                style =
                    Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                    ),
            )
        }

        drawCircle(
            color = primaryColor,
            radius = dotRadius,
            center =
                Offset(
                    x = progressX,
                    y = centerY,
                ),
        )
    }
}

private fun formatSampleRate(
    sampleRateHz: Int?,
): String {
    if (
        sampleRateHz == null ||
        sampleRateHz <= 0
    ) {
        return "— kHz"
    }

    val sampleRateKhz =
        sampleRateHz / 1_000.0

    val formatted =
        if (
            sampleRateKhz ==
                sampleRateKhz
                    .toInt()
                    .toDouble()
        ) {
            sampleRateKhz
                .toInt()
                .toString()
        } else {
            String.format(
                Locale.US,
                "%.1f",
                sampleRateKhz,
            )
        }

    return "$formatted kHz"
}

private fun formatBitrate(
    bitrateBps: Int?,
): String {
    if (
        bitrateBps == null ||
        bitrateBps <= 0
    ) {
        return "— kbps"
    }

    return "${bitrateBps / 1_000} kbps"
}

private fun formatAudioFormat(
    mimeType: String?,
): String {
    return when (
        mimeType?.lowercase(Locale.US)
    ) {
        "audio/mpeg" ->
            "MP3"

        "audio/flac",
        "audio/x-flac",
        ->
            "FLAC"

        "audio/wav",
        "audio/x-wav",
        "audio/vnd.wave",
        ->
            "WAV"

        "audio/aac" ->
            "AAC"

        "audio/ogg" ->
            "OGG"

        "audio/opus" ->
            "OPUS"

        "audio/mp4",
        "audio/x-m4a",
        ->
            "M4A"

        "audio/x-ms-wma" ->
            "WMA"

        "audio/3gpp" ->
            "3GP"

        else ->
            mimeType
                ?.substringAfter(
                    '/',
                    missingDelimiterValue = "",
                )
                ?.takeUnless {
                    it.isBlank()
                }
                ?.uppercase(Locale.US)
                ?: "—"
    }
}

private fun formatDuration(
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