@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.core.ui.component

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MiniPlayerShape =
    RoundedCornerShape(percent = 50)

private data class MiniPlayerArtworkPalette(
    val accent: Color? = null,
)

private val LocalMiniPlayerArtworkPalette =
    compositionLocalOf {
        MiniPlayerArtworkPalette()
    }

/**
 * Main mini-player.
 *
 * The artwork accent is extracted from the embedded image in the
 * audio source, using the same sampling strategy, WallpaperColors
 * API, representative-color selection, and HSV accent formula
 * as PlayerScreen.
 *
 * The card surface remains Material 3. Only its controls are
 * tinted by the artwork accent.
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track =
        playbackState.currentTrack ?: return

    val artworkAccent =
        rememberMiniPlayerArtworkAccent(
            trackUri = track.uri,
            artworkUri = track.artworkUri,
        )

    val artworkPalette =
        rememberMiniPlayerArtworkPalette(
            artworkAccent = artworkAccent,
        )

    Card(
        modifier = modifier,
        shape = MiniPlayerShape,
        colors =
            CardDefaults.cardColors(),
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
        CompositionLocalProvider(
            LocalMiniPlayerArtworkPalette provides artworkPalette,
        ) {
            MiniPlayerContentRow(
                playbackState = playbackState,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                showArtwork = true,
                modifier = Modifier,
            )
        }
    }
}

/**
 * Reusable mini-player content for callers that provide
 * their own outer container.
 */
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
        playbackState.currentTrack ?: return

    val artworkAccent =
        rememberMiniPlayerArtworkAccent(
            trackUri = track.uri,
            artworkUri = track.artworkUri,
        )

    val artworkPalette =
        rememberMiniPlayerArtworkPalette(
            artworkAccent = artworkAccent,
        )

    CompositionLocalProvider(
        LocalMiniPlayerArtworkPalette provides artworkPalette,
    ) {
        MiniPlayerContentRow(
            playbackState = playbackState,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            showArtwork = showArtwork,
            modifier = modifier,
        )
    }
}

@Composable
private fun MiniPlayerContentRow(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    showArtwork: Boolean,
    modifier: Modifier,
) {
    val track =
        playbackState.currentTrack ?: return

    Row(
        modifier =
            modifier.padding(
                horizontal = 12.dp,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showArtwork) {
            AlbumArtwork(
                artworkUri = track.artworkUri,
                fallbackText = track.title,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                size = 56.dp,
                shape = MaterialTheme.shapes.large,
            )
        } else {
            Spacer(
                modifier = Modifier.size(56.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            track.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        MiniPlayerButtonGroup(
            isPlaying = playbackState.isPlaying,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
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
    val colorScheme =
        MaterialTheme.colorScheme

    val artworkAccent =
        LocalMiniPlayerArtworkPalette.current.accent

    /*
     * Previous / Next:
     * - A 12% blend between the Material surface and artwork.
     * - Vivid artwork color for their icons.
     *
     * Play / Pause:
     * - The vivid artwork color for the container.
     * - Contrasting black or white icon.
     *
     * Without a usable artwork color, Material 3 is used.
     */
    val idleContainerColor =
        artworkAccent?.let { accent ->
            lerp(
                start = colorScheme.surfaceContainerHigh,
                stop = accent,
                fraction = 0.12f,
            )
        } ?: colorScheme.surfaceContainerHigh

    val idleContentColor =
        artworkAccent ?: colorScheme.onSurface

    val playPauseContainerColor =
        artworkAccent ?: colorScheme.primaryContainer

    val playPauseContentColor =
        artworkAccent?.let { accent ->
            if (accent.luminance() > 0.45f) {
                Color.Black
            } else {
                Color.White
            }
        } ?: colorScheme.onPrimaryContainer

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
            ButtonGroupDefaults.OverflowIndicator(
                menuState = menuState,
            )
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPrevious,
                    interactionSource = previousSource,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .animateWidth(
                                interactionSource = previousSource,
                            ),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = idleContainerColor,
                            contentColor = idleContentColor,
                        ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                com.rovia.music.core.ui.R.drawable.ic_skip_previous,
                            ),
                        contentDescription = "Previous",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            menuContent = {},
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPlayPause,
                    interactionSource = playPauseSource,
                    modifier =
                        Modifier
                            .size(52.dp)
                            .animateWidth(
                                interactionSource = playPauseSource,
                            ),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = playPauseContainerColor,
                            contentColor = playPauseContentColor,
                        ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isPlaying) {
                                    com.rovia.music.core.ui.R.drawable.ic_pause
                                } else {
                                    com.rovia.music.core.ui.R.drawable.ic_play_arrow
                                },
                            ),
                        contentDescription =
                            if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            menuContent = {},
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onNext,
                    interactionSource = nextSource,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .animateWidth(
                                interactionSource = nextSource,
                            ),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = idleContainerColor,
                            contentColor = idleContentColor,
                        ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                com.rovia.music.core.ui.R.drawable.ic_skip_next,
                            ),
                        contentDescription = "Next",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            menuContent = {},
        )
    }
}

/**
 * Loads the artwork accent using the same primary extraction
 * path as PlayerScreen.
 *
 * Embedded artwork is extracted directly from trackUri, so
 * regular and external playback do not derive their accents
 * from different thumbnail sources.
 *
 * The existing artworkUri path remains a fallback when embedded
 * artwork cannot be read.
 */
@Composable
private fun rememberMiniPlayerArtworkAccent(
    trackUri: String?,
    artworkUri: String?,
): Color? {
    val context =
        LocalContext.current

    var artworkAccent by remember(
        trackUri,
        artworkUri,
    ) {
        mutableStateOf<Color?>(null)
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
            artworkAccent = null
            return@LaunchedEffect
        }

        val sampledAccent =
            withContext(Dispatchers.IO) {
                try {
                    val appContext =
                        context.applicationContext

                    val bitmap =
                        trackUri
                            ?.takeIf(String::isNotBlank)
                            ?.let { audioUri ->
                                loadEmbeddedArtworkBitmap(
                                    context = appContext,
                                    audioUri = audioUri,
                                    maxDimension = ARTWORK_PALETTE_MAX_DIMENSION,
                                )
                            }
                            ?: loadArtworkFallbackBitmap(
                                context = appContext,
                                artworkUri = artworkUri,
                                maxDimension = ARTWORK_PALETTE_MAX_DIMENSION,
                            )
                            ?: return@withContext null

                    try {
                        extractMiniPlayerArtworkAccent(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    /*
                     * Artwork extraction failure is not a
                     * playback error. Material 3 is used.
                     */
                    null
                }
            }

        artworkAccent = sampledAccent
    }

    return artworkAccent
}

/**
 * The palette wrapper keeps the existing CompositionLocal
 * structure and allows all controls to share one accent.
 */
@Composable
private fun rememberMiniPlayerArtworkPalette(
    artworkAccent: Color?,
): MiniPlayerArtworkPalette {
    return remember(artworkAccent) {
        MiniPlayerArtworkPalette(
            accent = artworkAccent,
        )
    }
}

/**
 * Extracts artwork bytes directly from the audio source.
 *
 * Uses the same MediaMetadataRetriever strategy as PlayerScreen.
 * This function must run on a background dispatcher.
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
 * Loads a fallback artwork bitmap if direct extraction from the
 * audio source is unavailable.
 *
 * For content URIs, the original loadThumbnail() path is retained.
 * For file URIs, ArtworkBitmapLoader reads the cached artwork file.
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
 * Decodes the embedded image using the same power-of-two sample
 * strategy as PlayerScreen. The resulting bitmap is bounded to
 * the requested maximum dimension while preserving aspect ratio.
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

/**
 * Selects the first sufficiently chromatic representative color
 * returned by WallpaperColors, in primary, secondary, tertiary
 * order.
 *
 * The selection criteria and vivid HSV accent formula match
 * PlayerArtworkColors.kt.
 */
private fun extractMiniPlayerArtworkAccent(
    bitmap: Bitmap,
): Color? {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return null
    }

    val wallpaperColors =
        WallpaperColors.fromBitmap(bitmap)

    val representativeColors =
        listOfNotNull(
            wallpaperColors.primaryColor,
            wallpaperColors.secondaryColor,
            wallpaperColors.tertiaryColor,
        )

    val seedAndroidColor =
        representativeColors.firstOrNull { androidColor ->
            val hsv =
                FloatArray(3)

            AndroidColor.colorToHSV(
                androidColor.toArgb(),
                hsv,
            )

            hsv[1] >= MIN_ARTWORK_SATURATION &&
                hsv[2] >= MIN_ARTWORK_VALUE
        } ?: return null

    val seedColor =
        Color(seedAndroidColor.toArgb())

    val seedHsv =
        FloatArray(3)

    AndroidColor.colorToHSV(
        seedColor.toArgb(),
        seedHsv,
    )

    val strongerSaturation =
        (seedHsv[1] + ARTWORK_SATURATION_BOOST)
            .coerceAtLeast(MIN_ACCENT_SATURATION)
            .coerceAtMost(1f)

    return Color.hsv(
        hue = seedHsv[0],
        saturation = strongerSaturation,
        value = 1f,
        alpha = 1f,
    )
}

private const val ARTWORK_PALETTE_MAX_DIMENSION = 64
private const val FILE_SCHEME = "file"

private const val MIN_ARTWORK_SATURATION = 0.12f
private const val MIN_ARTWORK_VALUE = 0.08f
private const val ARTWORK_SATURATION_BOOST = 0.08f
private const val MIN_ACCENT_SATURATION = 0.58f