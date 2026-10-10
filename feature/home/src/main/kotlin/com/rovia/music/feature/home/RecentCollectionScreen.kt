
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.AlbumArtwork
import com.rovia.music.core.ui.component.TrackRow

private val RecentCollectionArtworkSize = 156.dp
private val RecentCollectionHorizontalPadding = 16.dp

/**
 * Identifies the type of local music collection displayed
 * by the shared Recent Collection detail screen.
 */
enum class RecentCollectionType {
    ARTIST,
    ALBUM,
    GENRE,
}

@Composable
fun RecentCollectionScreen(
    collectionType: RecentCollectionType,
    title: String,
    albumArtist: String? = null,
    artworkUri: String?,
    tracks: List<Track>,
    currentTrackId: Long?,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkShape: Shape =
        when (collectionType) {
            RecentCollectionType.ARTIST ->
                MaterialShapes.Circle.toShape()

            RecentCollectionType.ALBUM ->
                MaterialShapes.Square.toShape()

            RecentCollectionType.GENRE ->
                MaterialShapes.Cookie9Sided.toShape()
        }

    val bottomContentPadding =
        if (hasMiniPlayer) {
            200.dp
        } else {
            100.dp
        }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                ),
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
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
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_arrow_back,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.recent_collection_back,
                            ),
                    )
                }
            },
        )

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    top = 16.dp,
                    bottom = bottomContentPadding,
                ),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            item(
                key = "collection_header",
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    RecentCollectionHorizontalPadding,
                            ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    AlbumArtwork(
                        artworkUri = artworkUri,
                        fallbackText = title,
                        contentDescription = title,
                        modifier =
                            Modifier.size(
                                RecentCollectionArtworkSize,
                            ),
                        size = RecentCollectionArtworkSize,
                        shape = artworkShape,
                    )

                    Text(
                        text = title,
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (
                        collectionType ==
                            RecentCollectionType.ALBUM &&
                        !albumArtist.isNullOrBlank()
                    ) {
                        Text(
                            text = albumArtist,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.recent_collection_track_count,
                                tracks.size,
                                tracks.size,
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

                    if (tracks.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onPlayAll,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.recent_collection_play_all,
                                    ),
                            )
                        }
                    }
                }
            }

            item(
                key = "collection_track_heading",
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.recent_collection_tracks,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    modifier =
                        Modifier.padding(
                            start =
                                RecentCollectionHorizontalPadding,
                            top = 12.dp,
                            end =
                                RecentCollectionHorizontalPadding,
                        ),
                )
            }

            if (tracks.isEmpty()) {
                item(
                    key = "collection_empty",
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.recent_collection_empty,
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    RecentCollectionHorizontalPadding,
                                vertical = 12.dp,
                            ),
                    )
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { _, track ->
                        track.id
                    },
                ) { index, track ->
                    val isCurrentTrack =
                        track.id == currentTrackId

                    TrackRow(
                        track = track,
                        onClick = {
                            if (isCurrentTrack) {
                                onRestartCurrentTrack()
                            } else {
                                onTrackClick(
                                    tracks,
                                    index,
                                )
                            }
                        },
                        showAlbum = true,
                        isCurrentTrack = isCurrentTrack,
                    )
                }
            }
        }
    }
}
