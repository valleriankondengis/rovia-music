package com.rovia.music.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    isCurrentTrack: Boolean = false,
    outerPadding: PaddingValues = PaddingValues(
        horizontal = 12.dp,
        vertical = 6.dp,
    ),
    contentHorizontalPadding: Dp = 12.dp,
    artworkTextSpacing: Dp = 12.dp,
    trailingContentOffset: Dp = 0.dp,
    containerColorOverride: Color? = null,
    currentTrackContainerColorOverride: Color? = null,
) {
    var isMenuExpanded by remember {
        mutableStateOf(false)
    }

    var isInfoSheetVisible by remember {
        mutableStateOf(false)
    }

    val supportingText =
        remember(
            track.artist,
            track.album,
            showAlbum,
        ) {
            if (showAlbum) {
                listOfNotNull(
                    track.artist,
                    track.album,
                ).joinToString(" • ")
            } else {
                track.artist.orEmpty()
            }
        }

    val formattedDuration =
        remember(track.durationMs) {
            formatDuration(track.durationMs)
        }

    Surface(
        onClick = onClick,
        modifier = modifier.padding(outerPadding),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (isCurrentTrack) {
                currentTrackContainerColorOverride
                    ?: MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                containerColorOverride
                    ?: MaterialTheme.colorScheme.surfaceContainerLow
            },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(
                        horizontal = contentHorizontalPadding,
                        vertical = 8.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtwork(
                artworkUri = track.artworkUri,
                fallbackText = track.title,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                size = 56.dp,
                shape = MaterialTheme.shapes.large,
            )

            Spacer(modifier = Modifier.width(artworkTextSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Box(
                    modifier = Modifier.offset(x = trailingContentOffset),
                ) {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
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
        }
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

    val totalSeconds = durationMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return if (seconds < 10L) {
        "$minutes:0$seconds"
    } else {
        "$minutes:$seconds"
    }
}
