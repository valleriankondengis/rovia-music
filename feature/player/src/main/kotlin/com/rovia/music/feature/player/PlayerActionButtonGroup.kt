@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.TrackInfoBottomSheet

@Composable
internal fun PlayerActionButtonGroup(
    track: Track?,
    hasLyrics: Boolean,
    isLyricsLoading: Boolean,
    isLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onClose: () -> Unit,
) {
    var isMenuExpanded by remember {
        mutableStateOf(false)
    }

    var isInfoSheetVisible by remember {
        mutableStateOf(false)
    }

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween,
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

        Box {
            FilledIconButton(
                onClick = {
                    isMenuExpanded = true
                },
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
                    IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter =
                        painterResource(
                            CoreUiR.drawable.ic_more_vert,
                        ),
                    contentDescription =
                        stringResource(
                            CoreUiR.string.track_action_more,
                        ),
                    modifier =
                        Modifier.size(24.dp),
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = {
                    isMenuExpanded = false
                },
            ) {
                DropdownMenuItem(
                    enabled = track != null,
                    text = {
                        Text(
                            text =
                                stringResource(
                                    CoreUiR.string.track_action_info,
                                ),
                        )
                    },
                    onClick = {
                        isMenuExpanded = false
                        isInfoSheetVisible = true
                    },
                )
            }
        }
    }

    if (isInfoSheetVisible) {
        track?.let { currentTrack ->
            TrackInfoBottomSheet(
                track = currentTrack,
                onDismissRequest = {
                    isInfoSheetVisible = false
                },
            )
        }
    }
}

