package com.rovia.music.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    isCurrentTrack: Boolean = false,
) {
    var isMenuExpanded by remember {
        mutableStateOf(false)
    }

    var isInfoSheetVisible by remember {
        mutableStateOf(false)
    }

    val supportingText =
        if (showAlbum) {
            listOfNotNull(
                track.artist,
                track.album,
            ).joinToString(" • ")
        } else {
            track.artist.orEmpty()
        }

    ListItem(
        selected =
            isCurrentTrack,
        onClick =
            onClick,
        modifier =
            modifier,
        shapes =
            ListItemDefaults.shapes(
                shape =
                    RectangleShape,
                selectedShape =
                    RectangleShape,
                pressedShape =
                    RectangleShape,
                focusedShape =
                    RectangleShape,
                hoveredShape =
                    RectangleShape,
                draggedShape =
                    RectangleShape,
            ),
        colors =
            ListItemDefaults.colors(
                selectedContainerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerLow,
            ),
        leadingContent = {
            AlbumArtwork(
                artworkUri =
                    track.artworkUri,
                fallbackText =
                    track.title,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        56.dp,
                    ),
                size =
                    56.dp,
                shape =
                    MaterialTheme
                        .shapes
                        .large,
            )
        },
        supportingContent = {
            if (supportingText.isNotBlank()) {
                Text(
                    text =
                        supportingText,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        formatDuration(
                            track.durationMs,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                Box {
                    IconButton(
                        onClick = {
                            isMenuExpanded = true
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    com.rovia.music.core.ui.R.drawable.ic_more_vert,
                                ),
                            contentDescription =
                                stringResource(
                                    com.rovia.music.core.ui.R.string.track_action_more,
                                ),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded =
                            isMenuExpanded,
                        onDismissRequest = {
                            isMenuExpanded = false
                        },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text =
                                        stringResource(
                                            com.rovia.music.core.ui.R.string.track_action_info,
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
        },
    ) {
        Text(
            text =
                track.title,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
        )
    }

    if (isInfoSheetVisible) {
        TrackInfoBottomSheet(
            track = track,
            onDismissRequest = {
                isInfoSheetVisible = false
            },
        )
    }
}

private fun formatDuration(
    durationMs: Long,
): String {
    if (durationMs <= 0L) {
        return "--:--"
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