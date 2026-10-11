@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.core.ui.component

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import com.rovia.music.core.ui.R
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
 * Artwork colors use WallpaperColors, matching the source
 * and vivid accent formula used by PlayerScreen.
 *
 * The card surface remains Material 3. Only the controls
 * are tinted by the artwork accent.
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
     * Match PlayerLyricsMiniPlayer.
     *
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
                                R.drawable.ic_skip_previous,
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
                                    R.drawable.ic_pause
                                } else {
                                    R.drawable.ic_play_arrow
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
                                R.drawable.ic_skip_next,
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
 * Loads the artwork thumbnail and derives an accent from
 * Android's public WallpaperColors API on a background dispatcher.
 *
 * The resulting vivid accent uses the same representative-color
 * selection and HSV formula as PlayerArtworkColors.kt.
 */
@Composable
private fun rememberMiniPlayerArtworkAccent(
    artworkUri: String?,
): Color? {
    val context =
        LocalContext.current

    var artworkAccent by remember(artworkUri) {
        mutableStateOf<Color?>(null)
    }

    LaunchedEffect(
        artworkUri,
        context,
    ) {
        if (artworkUri.isNullOrBlank()) {
            artworkAccent = null
            return@LaunchedEffect
        }

        val sampledAccent =
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
                     * Unavailable or unsupported artwork is
                     * not a playback error. Use Material 3.
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
 * Selects the first sufficiently chromatic representative
 * color returned by WallpaperColors, in primary, secondary,
 * tertiary order.
 *
 * The vivid accent formula matches playerArtworkPaletteFromWallpaperColors()
 * and playerArtworkAccentColor() in PlayerArtworkColors.kt.
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

private const val MIN_ARTWORK_SATURATION = 0.12f
private const val MIN_ARTWORK_VALUE = 0.08f
private const val ARTWORK_SATURATION_BOOST = 0.08f
private const val MIN_ACCENT_SATURATION = 0.58f