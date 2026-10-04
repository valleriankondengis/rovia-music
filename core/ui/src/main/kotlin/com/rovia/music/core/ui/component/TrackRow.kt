package com.rovia.music.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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