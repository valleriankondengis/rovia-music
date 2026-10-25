package com.rovia.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(
    tracks: List<LocalAudioTrack>,
    onTrackClick: (LocalAudioTrack, Int) -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(
        horizontal = 0.dp,
        vertical = 1.dp,
    ),
    contentInsetPadding: PaddingValues = PaddingValues(),
    horizontalPadding: Dp = 18.dp,
    verticalSpacing: Dp = 1.dp,
    currentTrackId: String? = null,
    itemsEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    val layoutDirection = LocalLayoutDirection.current

    val leftPadding =
        outerPadding.calculateLeftPadding(layoutDirection) +
            contentInsetPadding.calculateLeftPadding(layoutDirection)
    val rightPadding =
        outerPadding.calculateRightPadding(layoutDirection) +
            contentInsetPadding.calculateRightPadding(layoutDirection)

    val listContentPadding = PaddingValues(
        start = if (layoutDirection == LayoutDirection.Ltr) leftPadding else rightPadding,
        top = outerPadding.calculateTopPadding() + contentInsetPadding.calculateTopPadding(),
        end = if (layoutDirection == LayoutDirection.Ltr) rightPadding else leftPadding,
        bottom = outerPadding.calculateBottomPadding() + contentInsetPadding.calculateBottomPadding(),
    )

    val trackColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurface,
        trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedLeadingContentColor = MaterialTheme.colorScheme.onSurface,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = listContentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        userScrollEnabled = userScrollEnabled,
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.uri.toString() },
        ) { index, track ->
            val title = track.title.orEmpty()
                .trim()
                .ifBlank { stringResource(R.string.player_unknown_track) }
            val supportingText = track.artist.orEmpty()
                .trim()
                .ifBlank { track.album.orEmpty().trim() }
                .ifBlank { stringResource(R.string.player_unknown_artist) }
            val isCurrentTrack = currentTrackId == track.id.toString()
            val artworkFallback = title.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "♪"

            SegmentedListItem(
                selected = isCurrentTrack,
                onClick = { onTrackClick(track, index) },
                enabled = itemsEnabled,
                modifier = Modifier.padding(horizontal = horizontalPadding),
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = tracks.size,
                ),
                colors = trackColors,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                leadingContent = {
                    AlbumArtwork(
                        artworkUri = track.uri.toString(),
                        fallbackText = artworkFallback,
                        contentDescription = title,
                        modifier = Modifier.size(56.dp),
                        size = 56.dp,
                        shape = MaterialTheme.shapes.medium,
                    )
                },
                content = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Text(
                        text = formatTrackDuration(track.durationMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L,
    )
}