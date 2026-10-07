@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.RepeatMode
import com.rovia.music.core.ui.R as CoreUiR

@Composable
internal fun PlaybackButtonGroup(
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
internal fun PlaybackOptionsButtonGroup(
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