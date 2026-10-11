@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
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
import com.rovia.music.core.ui.component.ArtworkBitmapLoader
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

    val trackUri =
        track?.uri

    val artworkUri =
        track?.artworkUri

    /*
     * Keep the existing palette while the next artwork loads.
     * This prevents a brief flash to the default background
     * during track transitions.
     */
    var artworkPalette by remember {
        mutableStateOf<PlayerArtworkPalette?>(null)
    }

    LaunchedEffect(
        trackUri,
        artworkUri,
        context,
    ) {
        if (
            trackUri.isNullOrBlank() &&
            artworkUri.isNullOrBlank()
        ) {
            artworkPalette = null
            return@LaunchedEffect
        }

        val sampledPalette =
            withContext(Dispatchers.IO) {
                try {
                    val appContext =
                        context.applicationContext

                    /*
                     * Use the audio URI as the primary source
                     * for embedded artwork in both playback
                     * contexts.
                     *
                     * Regular playback:
                     *   track.uri is the MediaStore audio URI.
                     *
                     * External playback:
                     *   track.uri is the supplied content URI.
                     *
                     * This avoids relying on two different
                     * thumbnail sources to derive the palette.
                     */
                    val embeddedBitmap =
                        trackUri?.let { audioUri ->
                            loadEmbeddedArtworkBitmap(
                                context = appContext,
                                audioUri = audioUri,
                                maxDimension = ARTWORK_PALETTE_MAX_DIMENSION,
                            )
                        }

                    /*
                     * Retain the existing thumbnail and cached-file
                     * loading paths when embedded artwork cannot
                     * be read from the audio URI.
                     */
                    val bitmap =
                        embeddedBitmap
                            ?: loadArtworkFallbackBitmap(
                                context = appContext,
                                artworkUri = artworkUri,
                                maxDimension = ARTWORK_PALETTE_MAX_DIMENSION,
                            )
                            ?: return@withContext null

                    try {
                        /*
                         * Continue using Android's public API for
                         * representative artwork colors.
                         */
                        val wallpaperColors =
                            WallpaperColors.fromBitmap(bitmap)

                        playerArtworkPaletteFromWallpaperColors(
                            wallpaperColors,
                        )
                    } finally {
                        /*
                         * Release only the temporary bitmap
                         * created for palette extraction.
                         *
                         * Artwork displayed by the UI is loaded
                         * separately and is not recycled here.
                         */
                        bitmap.recycle()
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    /*
                     * Unavailable or unusable artwork falls back
                     * to the existing Material 3 theme behavior.
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

/**
 * Extracts the embedded artwork directly from the audio source.
 *
 * The same extraction and bitmap-decoding path is used for
 * regular MediaStore playback and external content-URI playback.
 *
 * This function must run off the main thread.
 */
private fun loadEmbeddedArtworkBitmap(
    context: Context,
    audioUri: String,
    maxDimension: Int,
): Bitmap? {
    val uri =
        runCatching {
            Uri.parse(audioUri)
        }.getOrNull()
            ?: return null

    val retriever =
        MediaMetadataRetriever()

    return try {
        retriever.setDataSource(
            context,
            uri,
        )

        val embeddedPicture =
            retriever.embeddedPicture
                ?: return null

        decodeEmbeddedArtwork(
            bytes = embeddedPicture,
            maxDimension = maxDimension,
        )
    } catch (
        cancelled: CancellationException,
    ) {
        throw cancelled
    } catch (_: Exception) {
        null
    } finally {
        runCatching {
            retriever.release()
        }
    }
}

/**
 * Loads artwork from the existing artwork URI if direct embedded
 * artwork extraction is unavailable.
 *
 * Preserve the original ContentResolver thumbnail path first for
 * non-file URIs, then fall back to ArtworkBitmapLoader.
 */
private suspend fun loadArtworkFallbackBitmap(
    context: Context,
    artworkUri: String?,
    maxDimension: Int,
): Bitmap? {
    if (artworkUri.isNullOrBlank()) {
        return null
    }

    val uri =
        runCatching {
            Uri.parse(artworkUri)
        }.getOrNull()
            ?: return null

    if (uri.scheme == FILE_SCHEME) {
        return ArtworkBitmapLoader.load(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
        )
    }

    val thumbnail =
        try {
            context.contentResolver.loadThumbnail(
                uri,
                Size(
                    maxDimension,
                    maxDimension,
                ),
                null,
            )
        } catch (
            cancelled: CancellationException,
        ) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    return thumbnail
        ?: ArtworkBitmapLoader.load(
            context = context,
            artworkUri = artworkUri,
            maxDimension = maxDimension,
        )
}

/**
 * Decodes embedded image bytes using the same power-of-two
 * sampling strategy used by ArtworkBitmapLoader.
 *
 * This keeps memory use bounded and makes palette input consistent
 * when both playback contexts expose the same embedded image bytes.
 */
private fun decodeEmbeddedArtwork(
    bytes: ByteArray,
    maxDimension: Int,
): Bitmap? {
    if (bytes.isEmpty()) {
        return null
    }

    val safeMaxDimension =
        maxDimension.coerceAtLeast(1)

    val boundsOptions =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        boundsOptions,
    )

    if (
        boundsOptions.outWidth <= 0 ||
        boundsOptions.outHeight <= 0
    ) {
        return null
    }

    var sampleSize = 1

    while (
        boundsOptions.outWidth / sampleSize > safeMaxDimension ||
        boundsOptions.outHeight / sampleSize > safeMaxDimension
    ) {
        sampleSize *= 2
    }

    val decodeOptions =
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inJustDecodeBounds = false
        }

    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        decodeOptions,
    )
}

private const val ARTWORK_PALETTE_MAX_DIMENSION = 64
private const val FILE_SCHEME = "file"