
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.ui.R as CoreUiR

private const val IDLE_CONTROL_TINT_FRACTION = 0.12f
private const val ACTIVE_CONTROL_TINT_FRACTION = 0.46f

@Composable
internal fun PlaybackButtonGroup(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    artworkColor: Color? = null,
) {
    val colorScheme =
        MaterialTheme.colorScheme

    val artworkAccent =
        resolvePlayerControlArtworkAccent(
            artworkColor = artworkColor,
        )

    val idleContainerColor =
        artworkAccent?.let { accent ->
            lerp(
                start = colorScheme.surfaceContainerHigh,
                stop = accent,
                fraction = IDLE_CONTROL_TINT_FRACTION,
            )
        } ?: colorScheme.surfaceContainerHigh

    val activePlayContainerColor =
        artworkAccent?.let { accent ->
            lerp(
                start = colorScheme.primaryContainer,
                stop = accent,
                fraction = ACTIVE_CONTROL_TINT_FRACTION,
            )
        } ?: colorScheme.primaryContainer

    val idleContentColor =
        colorScheme.onSurface

    val activePlayContentColor =
        if (artworkAccent != null) {
            contrastingControlContentColor(
                activePlayContainerColor,
            )
        } else {
            colorScheme.onPrimaryContainer
        }

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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(
                menuState = menuState,
            )
        },
    ) {
        // Play/Pause expands into the remaining available width.
        customItem(
            buttonGroupContent = {
                FilledIconToggleButton(
                    checked = isPlaying,
                    onCheckedChange = {
                        onPlayPause()
                    },
                    interactionSource = playPauseSource,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .animateWidth(
                                interactionSource = playPauseSource,
                                compressionLimit = 16.dp,
                            ),
                    colors =
                        IconButtonDefaults
                            .filledIconToggleButtonColors(
                                containerColor =
                                    idleContainerColor,
                                contentColor =
                                    idleContentColor,
                                checkedContainerColor =
                                    activePlayContainerColor,
                                checkedContentColor =
                                    activePlayContentColor,
                            ),
                    shapes =
                        IconButtonDefaults
                            .toggleableShapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isPlaying) {
                                    CoreUiR.drawable.ic_pause
                                } else {
                                    CoreUiR.drawable.ic_play_arrow
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
                        modifier = Modifier.size(32.dp),
                    )
                }
            },
            menuContent = {},
        )

        // Previous retains its original size.
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPrevious,
                    interactionSource = previousSource,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .animateWidth(
                                interactionSource = previousSource,
                                compressionLimit = 16.dp,
                            ),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
                                containerColor = idleContainerColor,
                                contentColor = idleContentColor,
                            ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_skip_previous,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.player_previous,
                            ),
                        modifier = Modifier.size(30.dp),
                    )
                }
            },
            menuContent = {},
        )

        // Next retains its original size.
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onNext,
                    interactionSource = nextSource,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .animateWidth(
                                interactionSource = nextSource,
                                compressionLimit = 16.dp,
                            ),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
                                containerColor = idleContainerColor,
                                contentColor = idleContentColor,
                            ),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_skip_next,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.player_next,
                            ),
                        modifier = Modifier.size(30.dp),
                    )
                }
            },
            menuContent = {},
        )
    }
}

@Composable
internal fun PlaybackOptionsButtonGroup(
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onShuffleEnabledChange: (Boolean) -> Unit,
    compact: Boolean = false,
    artworkColor: Color? = null,
) {
    val colorScheme =
        MaterialTheme.colorScheme

    val artworkAccent =
        resolvePlayerControlArtworkAccent(
            artworkColor = artworkColor,
        )

    val idleContainerColor =
        artworkAccent?.let { accent ->
            lerp(
                start = colorScheme.surfaceContainerHigh,
                stop = accent,
                fraction = IDLE_CONTROL_TINT_FRACTION,
            )
        } ?: colorScheme.surfaceContainerHigh

    val activeRepeatContainerColor =
        artworkAccent?.let { accent ->
            lerp(
                start = colorScheme.secondaryContainer,
                stop = accent,
                fraction = ACTIVE_CONTROL_TINT_FRACTION,
            )
        } ?: colorScheme.secondaryContainer

    val activeRepeatContentColor =
        if (artworkAccent != null) {
            contrastingControlContentColor(
                activeRepeatContainerColor,
            )
        } else {
            colorScheme.onSecondaryContainer
        }

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

    /*
     * Default mode preserves full-width end alignment.
     * Compact mode wraps the two buttons at their natural width,
     * allowing landscape to place them near the output control.
     */
    Box(
        modifier =
            if (compact) {
                Modifier
            } else {
                Modifier.fillMaxWidth()
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        ButtonGroup(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
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
                                    interactionSource = repeatSource,
                                    compressionLimit = 16.dp,
                                ),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor =
                                    idleContainerColor,
                                contentColor =
                                    colorScheme.onSurface,
                                checkedContainerColor =
                                    activeRepeatContainerColor,
                                checkedContentColor =
                                    activeRepeatContentColor,
                            ),
                        shapes =
                            ButtonGroupDefaults
                                .connectedLeadingButtonShapes(
                                    shape = leadingNormalShape,
                                    pressedShape = leadingPressedShape,
                                    checkedShape = leadingNormalShape,
                                ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (repeatMode == RepeatMode.ONE) {
                                            CoreUiR.drawable.ic_repeat_one
                                        } else {
                                            CoreUiR.drawable.ic_repeat
                                        },
                                    ),
                                contentDescription =
                                    when (repeatMode) {
                                        RepeatMode.OFF ->
                                            stringResource(
                                                R.string.player_repeat_off,
                                            )

                                        RepeatMode.ALL ->
                                            stringResource(
                                                R.string.player_repeat_all,
                                            )

                                        RepeatMode.ONE ->
                                            stringResource(
                                                R.string.player_repeat_one,
                                            )
                                    },
                                modifier = Modifier.size(28.dp),
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
                        onCheckedChange = onShuffleEnabledChange,
                        interactionSource = shuffleSource,
                        modifier =
                            Modifier
                                .width(112.dp)
                                .height(64.dp)
                                .animateWidth(
                                    interactionSource = shuffleSource,
                                    compressionLimit = 16.dp,
                                ),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor =
                                    idleContainerColor,
                                contentColor =
                                    colorScheme.onSurface,
                                checkedContainerColor =
                                    activeRepeatContainerColor,
                                checkedContentColor =
                                    activeRepeatContentColor,
                            ),
                        shapes =
                            ButtonGroupDefaults
                                .connectedTrailingButtonShapes(
                                    shape = trailingNormalShape,
                                    pressedShape = trailingPressedShape,
                                    checkedShape = trailingNormalShape,
                                ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        CoreUiR.drawable.ic_shuffle,
                                    ),
                                contentDescription =
                                    if (shuffleEnabled) {
                                        stringResource(
                                            R.string.player_shuffle_on,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.player_shuffle_off,
                                        )
                                    },
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                },
                menuContent = {},
            )
        }
    }
}

/**
 * Returns the shared artwork control accent whenever PlayerScreen
 * has provided one. The optional artworkColor remains a fallback
 * for callers rendered outside LocalPlayerArtworkPalette.
 */
@Composable
private fun resolvePlayerControlArtworkAccent(
    artworkColor: Color?,
): Color? {
    val colorScheme =
        MaterialTheme.colorScheme

    val isDarkSurface =
        colorScheme.surface.luminance() < 0.5f

    val sharedControlAccent =
        playerArtworkControlAccentColor()

    val fallbackAccent =
        remember(
            artworkColor,
            isDarkSurface,
        ) {
            resolvePastelArtworkAccent(
                artworkColor = artworkColor,
                isDarkSurface = isDarkSurface,
            )
        }

    return sharedControlAccent ?: fallbackAccent
}

/**
 * Compatibility fallback for components outside the shared
 * artwork palette. Controls use a lower saturation than the
 * vivid active-lyrics and seekbar accent.
 */
private fun resolvePastelArtworkAccent(
    artworkColor: Color?,
    isDarkSurface: Boolean,
): Color? {
    artworkColor ?: return null

    val hsv =
        FloatArray(3)

    AndroidColor.colorToHSV(
        artworkColor.toArgb(),
        hsv,
    )

    if (
        hsv[1] < 0.12f ||
        hsv[2] < 0.08f
    ) {
        return null
    }

    val softerSaturation =
        (hsv[1] * 0.48f)
            .coerceIn(0.22f, 0.42f)

    val controlBrightness =
        if (isDarkSurface) {
            0.92f
        } else {
            0.68f
        }

    return Color.hsv(
        hue = hsv[0],
        saturation = softerSaturation,
        value = controlBrightness,
        alpha = 1f,
    )
}

/**
 * Select a readable foreground based on the final blended
 * container color, not the original artwork seed color.
 */
private fun contrastingControlContentColor(
    containerColor: Color,
): Color {
    return if (containerColor.luminance() > 0.45f) {
        Color(0xFF202124)
    } else {
        Color.White
    }
}
