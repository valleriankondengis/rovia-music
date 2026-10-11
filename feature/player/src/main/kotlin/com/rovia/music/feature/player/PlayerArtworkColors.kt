package com.rovia.music.feature.player

import android.app.WallpaperColors
import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Color roles derived from the current track's artwork.
 *
 * WallpaperColors supplies representative source colors. The
 * derived accents are lightweight approximations of System UI's
 * artwork-driven tonal roles; they do not expose or call the
 * internal System UI ColorScheme implementation.
 */
data class PlayerArtworkPalette(
    val seedColor: Color,
    val secondaryColor: Color?,
    val tertiaryColor: Color?,
    val vividAccentColor: Color,
    val controlAccentColor: Color,
)

/**
 * Shared artwork palette for PlayerScreen descendants.
 *
 * Null means artwork colors have not been extracted or no usable
 * chromatic color is available.
 */
val LocalPlayerArtworkPalette =
    compositionLocalOf<PlayerArtworkPalette?> {
        null
    }

/**
 * Original representative artwork color.
 *
 * Kept for existing components that still use the raw color,
 * including WavySeekBar and compatibility call sites.
 */
val LocalPlayerArtworkColor =
    compositionLocalOf<Color?> {
        null
    }

/**
 * Creates a shared palette from Android's public WallpaperColors
 * API. Call WallpaperColors.fromBitmap(bitmap) on a background
 * dispatcher before passing its result here.
 *
 * The selection prefers the first chromatic representative color
 * in primary, secondary, tertiary order. Neutral artwork returns
 * null so components can keep their Material 3 fallback.
 */
fun playerArtworkPaletteFromWallpaperColors(
    wallpaperColors: WallpaperColors?,
): PlayerArtworkPalette? {
    wallpaperColors ?: return null

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

    /*
     * Keep the seed hue. The vivid accent is reserved for elements
     * such as active lyrics and the main seekbar.
     *
     * Unlike a full Monet HCT palette, this native-only conversion
     * cannot reproduce every tonal value used internally by AOSP.
     */
    val vividSaturation =
        (seedHsv[1] + 0.08f)
            .coerceAtLeast(0.58f)
            .coerceAtMost(1f)

    val vividAccentColor =
        Color.hsv(
            hue = seedHsv[0],
            saturation = vividSaturation,
            value = 1f,
            alpha = 1f,
        )

    /*
     * System UI's media controls use a lighter tonal role for
     * Play/Pause. Approximate that role with a bright, softened
     * accent while preserving the artwork hue.
     *
     * This is intentionally different from the vivid accent.
     */
    val controlSaturation =
        (seedHsv[1] * 0.48f)
            .coerceIn(0.22f, 0.42f)

    val controlAccentColor =
        Color.hsv(
            hue = seedHsv[0],
            saturation = controlSaturation,
            value = 0.95f,
            alpha = 1f,
        )

    return PlayerArtworkPalette(
        seedColor = seedColor,
        secondaryColor =
            wallpaperColors.secondaryColor?.let { color ->
                Color(color.toArgb())
            },
        tertiaryColor =
            wallpaperColors.tertiaryColor?.let { color ->
                Color(color.toArgb())
            },
        vividAccentColor = vividAccentColor,
        controlAccentColor = controlAccentColor,
    )
}

/**
 * Vivid accent for active lyrics and WavySeekBar.
 *
 * Prefer the shared WallpaperColors-derived palette. The legacy
 * local color is retained as a fallback until all call sites have
 * been connected to the shared palette.
 */
@Composable
fun playerArtworkAccentColor(): Color? {
    val artworkPalette =
        LocalPlayerArtworkPalette.current

    val artworkColor =
        LocalPlayerArtworkColor.current

    return remember(
        artworkPalette,
        artworkColor,
    ) {
        artworkPalette?.vividAccentColor
            ?: artworkColor?.let { color ->
                vividArtworkAccentFromColor(color)
            }
    }
}

/**
 * Softer, brighter artwork accent for ordinary player controls.
 *
 * The shared palette takes precedence. The fallback behavior
 * preserves compatibility with components not yet connected to
 * LocalPlayerArtworkPalette.
 */
@Composable
fun playerArtworkControlAccentColor(): Color? {
    val artworkPalette =
        LocalPlayerArtworkPalette.current

    val artworkColor =
        LocalPlayerArtworkColor.current

    return remember(
        artworkPalette,
        artworkColor,
    ) {
        artworkPalette?.controlAccentColor
            ?: artworkColor?.let { color ->
                controlArtworkAccentFromColor(color)
            }
    }
}

/**
 * Applies a restrained artwork tint to a Material 3 surface.
 *
 * Without a usable artwork accent, the original Material 3
 * surface color is returned unchanged.
 */
@Composable
fun playerArtworkControlContainerColor(
    baseColor: Color,
    tintFraction: Float,
): Color {
    val accent =
        playerArtworkControlAccentColor()
            ?: return baseColor

    return lerp(
        start = baseColor,
        stop = accent,
        fraction = tintFraction.coerceIn(0f, 1f),
    )
}

/**
 * Chooses a contrasting foreground for an artwork-tinted control.
 * The supplied Material 3 content color is used when no accent is
 * available.
 */
@Composable
fun playerArtworkControlContentColor(
    containerColor: Color,
    fallbackColor: Color,
): Color {
    val accent =
        playerArtworkControlAccentColor()
            ?: return fallbackColor

    /*
     * Choose contrast from the final blended container instead of
     * judging it from the original artwork seed.
     */
    return when {
        containerColor.luminance() > 0.45f ->
            Color(0xFF202124)

        accent.alpha > 0f ->
            Color.White

        else ->
            fallbackColor
    }
}

/**
 * Returns a contrasting foreground for the vivid artwork accent.
 */
@Composable
fun playerArtworkOnAccentColor(): Color {
    val accent =
        playerArtworkAccentColor()

    return when {
        accent == null ->
            MaterialTheme.colorScheme.onPrimaryContainer

        accent.luminance() > 0.45f ->
            Color.Black

        else ->
            Color.White
    }
}

private fun vividArtworkAccentFromColor(
    color: Color,
): Color? {
    val hsv =
        FloatArray(3)

    AndroidColor.colorToHSV(
        color.toArgb(),
        hsv,
    )

    if (
        hsv[1] < MIN_ARTWORK_SATURATION ||
        hsv[2] < MIN_ARTWORK_VALUE
    ) {
        return null
    }

    val strongerSaturation =
        (hsv[1] + 0.08f)
            .coerceAtLeast(0.58f)
            .coerceAtMost(1f)

    return Color.hsv(
        hue = hsv[0],
        saturation = strongerSaturation,
        value = 1f,
        alpha = 1f,
    )
}

private fun controlArtworkAccentFromColor(
    color: Color,
): Color? {
    val hsv =
        FloatArray(3)

    AndroidColor.colorToHSV(
        color.toArgb(),
        hsv,
    )

    if (
        hsv[1] < MIN_ARTWORK_SATURATION ||
        hsv[2] < MIN_ARTWORK_VALUE
    ) {
        return null
    }

    val softerSaturation =
        (hsv[1] * 0.48f)
            .coerceIn(0.22f, 0.42f)

    return Color.hsv(
        hue = hsv[0],
        saturation = softerSaturation,
        value = 0.95f,
        alpha = 1f,
    )
}

private const val MIN_ARTWORK_SATURATION = 0.12f
private const val MIN_ARTWORK_VALUE = 0.08f