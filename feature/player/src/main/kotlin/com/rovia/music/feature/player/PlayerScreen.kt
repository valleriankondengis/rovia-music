
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.model.SyncedLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    audioOutputIndicator: (@Composable () -> Unit)? = null,
) {
    val track =
        playbackState.currentTrack

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

    // ---------------------------------------------------------
    // Artwork color extraction using WallpaperColors
    // ---------------------------------------------------------

    val context =
        LocalContext.current

    val artworkUri =
        track?.artworkUri

    /*
     * Keep the existing palette while the next thumbnail loads.
     * This prevents a brief flash to the default background
     * during track transitions.
     */
    var artworkPalette by remember {
        mutableStateOf<PlayerArtworkPalette?>(null)
    }

    LaunchedEffect(
        artworkUri,
        context,
    ) {
        if (artworkUri.isNullOrBlank()) {
            artworkPalette = null
            return@LaunchedEffect
        }

        val sampledPalette =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap =
                        context.applicationContext
                            .contentResolver
                            .loadThumbnail(
                                Uri.parse(artworkUri),
                                Size(64, 64),
                                null,
                            )

                    try {
                        /*
                         * This is the same public Android API
                         * used by AOSP System UI to obtain
                         * representative artwork colors.
                         */
                        val wallpaperColors =
                            WallpaperColors.fromBitmap(bitmap)

                        playerArtworkPaletteFromWallpaperColors(
                            wallpaperColors,
                        )
                    } finally {
                        bitmap.recycle()
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    /*
                     * Missing, unreadable, or neutral artwork
                     * falls back to the Material 3 theme.
                     */
                    null
                }
            }

        artworkPalette = sampledPalette
    }

    /*
     * Keep the original representative seed available for
     * existing components, while passing the shared palette
     * to components that use tonal artwork color roles.
     */
    val artworkColor =
        artworkPalette?.seedColor

    // ---------------------------------------------------------
    // Brighter artwork-derived background
    // ---------------------------------------------------------

    val isDarkTheme =
        isSystemInDarkTheme()

    /*
     * Preserve the established background appearance.
     * Only this background variant is adjusted; the shared
     * palette remains unchanged for player controls and lyrics.
     */
    val artworkBackgroundTint =
        remember(
            artworkColor,
            isDarkTheme,
        ) {
            artworkColor?.let { color ->
                val hsv =
                    FloatArray(3)

                AndroidColor.colorToHSV(
                    color.toArgb(),
                    hsv,
                )

                if (hsv[1] < 0.12f) {
                    null
                } else {
                    hsv[1] =
                        (hsv[1] * 0.82f)
                            .coerceIn(
                                0.16f,
                                0.68f,
                            )

                    hsv[2] =
                        maxOf(
                            hsv[2],
                            if (isDarkTheme) {
                                0.84f
                            } else {
                                0.90f
                            },
                        )

                    Color.hsv(
                        hue = hsv[0],
                        saturation = hsv[1],
                        value = hsv[2],
                        alpha = 1f,
                    )
                }
            }
        }

    // ---------------------------------------------------------
    // Material 3 Expressive artwork background
    // ---------------------------------------------------------

    val baseBackgroundColor =
        MaterialTheme.colorScheme.background

    val artworkSurfaceBaseColor =
        MaterialTheme.colorScheme.surfaceContainerLow

    val topTintStrength =
        if (isDarkTheme) {
            0.46f
        } else {
            0.24f
        }

    val bottomTintStrength =
        if (isDarkTheme) {
            0.28f
        } else {
            0.15f
        }

    val targetTopColor =
        artworkBackgroundTint?.let { color ->
            lerp(
                start = artworkSurfaceBaseColor,
                stop = color,
                fraction = topTintStrength,
            )
        } ?: baseBackgroundColor

    val targetBottomColor =
        artworkBackgroundTint?.let { color ->
            lerp(
                start = baseBackgroundColor,
                stop = color,
                fraction = bottomTintStrength,
            )
        } ?: baseBackgroundColor

    val backgroundTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec =
            motionScheme.defaultEffectsSpec<Color>(),
        label = "playerArtworkBackgroundTop",
    )

    val backgroundBottomColor by animateColorAsState(
        targetValue = targetBottomColor,
        animationSpec =
            motionScheme.defaultEffectsSpec<Color>(),
        label = "playerArtworkBackgroundBottom",
    )

    // ---------------------------------------------------------
    // Orientation
    // ---------------------------------------------------------

    val configuration =
        LocalConfiguration.current

    val isLandscape =
        configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // ---------------------------------------------------------
    // Player content
    // ---------------------------------------------------------

    Surface(
        modifier =
            modifier.fillMaxSize(),
        color =
            baseBackgroundColor,
        tonalElevation = 0.dp,
    ) {
        /*
         * Expose both color sources:
         *
         * LocalPlayerArtworkColor:
         *   Compatibility source for components that consume
         *   the original representative artwork color.
         *
         * LocalPlayerArtworkPalette:
         *   Shared source for vivid and softer tonal accents.
         *
         * Both values come from the same WallpaperColors result.
         */
        CompositionLocalProvider(
            LocalPlayerArtworkColor provides artworkColor,
            LocalPlayerArtworkPalette provides artworkPalette,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            backgroundTopColor,
                                            backgroundBottomColor,
                                        ),
                                ),
                        ),
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
                            seekPosition = seekPosition,
                            duration = duration,
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
                            onLyricSeek = onSeek,
                            artworkColor = artworkColor,
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
                            onShuffleEnabledChange =
                                onShuffleEnabledChange,
                            onLyricSeek = onSeek,
                            audioOutputIndicator = audioOutputIndicator,
                            artworkColor = artworkColor,
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
                            onShuffleEnabledChange =
                                onShuffleEnabledChange,
                            onLyricSeek = onSeek,
                            audioOutputIndicator = audioOutputIndicator,
                            artworkColor = artworkColor,
                        )
                    }
                }
            }
        }
    }
}
