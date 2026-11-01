package com.rovia.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack

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
    headerContent: (LazyListScope.() -> Unit)? = null,
    sectionKey: ((LocalAudioTrack) -> String)? = null,
    sectionHeaderContent: (@Composable (String) -> Unit)? = null,
    showDuration: Boolean = true,
    footerContent: (LazyListScope.() -> Unit)? = null,
    supportingContentOverride: (@Composable (LocalAudioTrack) -> Unit)? = null,
    onTrackInfoClick: ((LocalAudioTrack) -> Unit)? = null,
    trackInfoEnabled: (LocalAudioTrack) -> Boolean = { true },
) {
    val layoutDirection = LocalLayoutDirection.current

    val hasSections = sectionKey != null && sectionHeaderContent != null
    val sectionKeys = remember(tracks, sectionKey, hasSections) {
        if (hasSections) tracks.map { track -> sectionKey.invoke(track) } else emptyList()
    }
    val sectionPositions = remember(tracks, sectionKeys, hasSections) {
        if (hasSections) buildTrackListSectionPositions(sectionKeys) else emptyList()
    }

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

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = listContentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        userScrollEnabled = userScrollEnabled,
    ) {
        headerContent?.invoke(this)
        itemsIndexed(
            items = tracks,
            key = { index, track -> "${track.uri}#${track.id}#$index" },
        ) { index, track ->
            val sectionPosition = sectionPositions.getOrNull(index)
            val sectionIndex = sectionPosition?.indexInSection ?: index
            val sectionSize = sectionPosition?.sectionSize ?: tracks.size
            val title = track.title.orEmpty()
                .trim()
                .ifBlank { stringResource(R.string.player_unknown_track) }
            val supportingText = track.artist.orEmpty()
                .trim()
                .ifBlank { track.album.orEmpty().trim() }
                .ifBlank { stringResource(R.string.player_unknown_artist) }

            Column {
                if (
                    sectionHeaderContent != null &&
                    sectionPosition != null &&
                    sectionPosition.key != null &&
                    (index == 0 || sectionPosition.key != sectionPositions[index - 1].key)
                ) {
                    sectionHeaderContent(sectionPosition.key)
                }

                TrackListItem(
                    title = title,
                    supportingText = supportingText,
                    artworkUri = track.uri.toString(),
                    durationMs = track.durationMs,
                    selected = currentTrackId == track.id.toString(),
                    enabled = itemsEnabled,
                    onClick = { onTrackClick(track, index) },
                    index = sectionIndex,
                    count = sectionSize,
                    horizontalPadding = horizontalPadding,
                    showDuration = showDuration,
                    supportingContent = if (supportingContentOverride != null) {
                        { supportingContentOverride(track) }
                    } else {
                        null
                    },
                    onTrackInfoClick = onTrackInfoClick
                        ?.takeIf { trackInfoEnabled(track) }
                        ?.let { callback -> { callback(track) } },
                )
            }
        }
        footerContent?.invoke(this)
    }
}


@Composable
fun TrackListCard(
    track: LocalAudioTrack,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    artworkSize: Dp,
    modifier: Modifier = Modifier,
) {
    val title = track.title
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_track)
    val artist = track.artist
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_artist)

    Column(
        modifier = modifier
            .width(width)
            .height(height)
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlbumArtwork(
            artworkUri = track.uri.toString(),
            fallbackText = title,
            contentDescription = title,
            modifier = Modifier.size(artworkSize),
            size = artworkSize,
            shape = MaterialTheme.shapes.large,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackListItem(
    title: String,
    supportingText: String,
    artworkUri: String?,
    durationMs: Long?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    artworkSize: Dp = 56.dp,
    artworkShape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    showDuration: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onTrackInfoClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackColors = ListItemDefaults.segmentedColors(
        containerColor = colorScheme.secondaryContainer,
        contentColor = colorScheme.onSurface,
        leadingContentColor = colorScheme.onSurface,
        trailingContentColor = colorScheme.onSurfaceVariant,
        supportingContentColor = colorScheme.onSurfaceVariant,
        selectedContainerColor = colorScheme.primaryContainer,
        selectedContentColor = colorScheme.onSurface,
        selectedLeadingContentColor = colorScheme.onSurface,
        selectedTrailingContentColor = colorScheme.onSurfaceVariant,
        selectedSupportingContentColor = colorScheme.onSurfaceVariant,
    )
    val artworkFallback = title.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "♪"
    val durationText = if (showDuration) {
        durationMs?.let(::formatTrackDuration)
    } else {
        null
    }
    val defaultTrailingContent: (@Composable () -> Unit)? =
        if (durationText == null) {
            null
        } else {
            {
                Text(
                    text = durationText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    val baseTrailingContent = trailingContent ?: defaultTrailingContent
    val resolvedTrailingContent: (@Composable () -> Unit)? =
        if (onTrackInfoClick == null) {
            baseTrailingContent
        } else {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    baseTrailingContent?.invoke()

                    TrackMenu(
                        onTrackInfoClick = onTrackInfoClick,
                    )
                }
            }
        }

    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(horizontal = horizontalPadding),
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        ),
        colors = trackColors,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = contentPadding,
        leadingContent = {
            AlbumArtwork(
                artworkUri = artworkUri,
                fallbackText = artworkFallback,
                contentDescription = title,
                modifier = Modifier.size(artworkSize),
                size = artworkSize,
                shape = artworkShape,
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
            if (supportingContent != null) {
                supportingContent()
            } else {
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = resolvedTrailingContent,
    )
}

private data class TrackListSectionPosition(
    val key: String?,
    val indexInSection: Int,
    val sectionSize: Int,
)

private fun buildTrackListSectionPositions(
    sectionKeys: List<String?>,
): List<TrackListSectionPosition> {
    if (sectionKeys.isEmpty()) return emptyList()

    val positions = MutableList(sectionKeys.size) {
        TrackListSectionPosition(
            key = null,
            indexInSection = 0,
            sectionSize = 0,
        )
    }
    var sectionStart = 0

    while (sectionStart < sectionKeys.size) {
        var sectionEnd = sectionStart + 1
        while (
            sectionEnd < sectionKeys.size &&
            sectionKeys[sectionEnd] == sectionKeys[sectionStart]
        ) {
            sectionEnd++
        }

        val sectionSize = sectionEnd - sectionStart
        for (index in sectionStart until sectionEnd) {
            positions[index] = TrackListSectionPosition(
                key = sectionKeys[index],
                indexInSection = index - sectionStart,
                sectionSize = sectionSize,
            )
        }
        sectionStart = sectionEnd
    }

    return positions
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        append(minutes)
        append(':')
        if (seconds < 10L) append('0')
        append(seconds)
    }
}
