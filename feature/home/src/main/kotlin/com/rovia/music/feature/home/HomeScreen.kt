@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.AlbumArtwork
import com.rovia.music.core.ui.component.TrackRow

private val HomeArtworkSize = 156.dp
private val HomeArtworkRowHeight = 204.dp
private val HomeSectionHorizontalPadding = 16.dp
private val HomeArtworkSpacing = 12.dp
private val HomeSectionBottomSpacing = 28.dp

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
    onOpenSettings: () -> Unit,
    hasMiniPlayer: Boolean = false,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String, String?) -> Unit = { _, _ -> },
    onOpenGenre: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> {
            LoadingContent(
                modifier = modifier,
            )
        }

        is HomeUiState.Content -> {
            HomeContent(
                recentlyAdded =
                    uiState.recentlyAdded,
                recentPlays =
                    uiState.recentPlays,
                recentArtists =
                    uiState.recentArtists,
                recentAlbums =
                    uiState.recentAlbums,
                recentGenres =
                    uiState.recentGenres,
                onTrackClick =
                    onTrackClick,
                onRestartCurrentTrack =
                    onRestartCurrentTrack,
                currentTrackId =
                    currentTrackId,
                onOpenSettings =
                    onOpenSettings,
                hasMiniPlayer =
                    hasMiniPlayer,
                onOpenArtist =
                    onOpenArtist,
                onOpenAlbum =
                    onOpenAlbum,
                onOpenGenre =
                    onOpenGenre,
                modifier =
                    modifier,
            )
        }

        is HomeUiState.Error -> {
            ErrorContent(
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun HomeContent(
    recentlyAdded: List<Track>,
    recentPlays: List<Track>,
    recentArtists: List<RecentArtist>,
    recentAlbums: List<RecentAlbum>,
    recentGenres: List<RecentGenre>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
    onOpenSettings: () -> Unit,
    hasMiniPlayer: Boolean,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * NavigationBar is an overlay in MainNavigation.
     * Reserve enough bottom space for the player and navigation.
     */
    val bottomContentPadding =
        if (hasMiniPlayer) {
            200.dp
        } else {
            100.dp
        }

    Column(
        modifier =
            modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                ),
            title = {},
            actions = {
                FilledIconButton(
                    onClick = onOpenSettings,
                    shapes =
                        IconButtonDefaults.shapes(
                            shape =
                                CircleShape,
                            pressedShape =
                                MaterialTheme.shapes.medium,
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
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_settings,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.action_settings,
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
                    top = 8.dp,
                    bottom = bottomContentPadding,
                ),
        ) {
            /*
             * Home shows at most ten Recent Play tracks.
             * The stored collection remains unrestricted.
             */
            if (recentPlays.isNotEmpty()) {
                item(
                    key = "recent_play_section",
                ) {
                    RecentPlaySection(
                        tracks =
                            recentPlays.take(10),
                        onTrackClick =
                            onTrackClick,
                        modifier =
                            Modifier.padding(
                                start = HomeSectionHorizontalPadding,
                                end = HomeSectionHorizontalPadding,
                                bottom = HomeSectionBottomSpacing,
                            ),
                    )
                }
            }

            if (recentArtists.isNotEmpty()) {
                item(
                    key = "recent_artists_section",
                ) {
                    RecentArtistsSection(
                        artists =
                            recentArtists,
                        onArtistClick =
                            onOpenArtist,
                        modifier =
                            Modifier.padding(
                                start = HomeSectionHorizontalPadding,
                                end = HomeSectionHorizontalPadding,
                                bottom = HomeSectionBottomSpacing,
                            ),
                    )
                }
            }

            if (recentAlbums.isNotEmpty()) {
                item(
                    key = "recent_albums_section",
                ) {
                    RecentAlbumsSection(
                        albums =
                            recentAlbums,
                        onAlbumClick =
                            onOpenAlbum,
                        modifier =
                            Modifier.padding(
                                start = HomeSectionHorizontalPadding,
                                end = HomeSectionHorizontalPadding,
                                bottom = HomeSectionBottomSpacing,
                            ),
                    )
                }
            }

            if (recentGenres.isNotEmpty()) {
                item(
                    key = "recent_genres_section",
                ) {
                    RecentGenresSection(
                        genres =
                            recentGenres,
                        onGenreClick =
                            onOpenGenre,
                        modifier =
                            Modifier.padding(
                                start = HomeSectionHorizontalPadding,
                                end = HomeSectionHorizontalPadding,
                                bottom = HomeSectionBottomSpacing,
                            ),
                    )
                }
            }

            item(
                key = "recently_added_section",
            ) {
                RecentlyAddedSection(
                    tracks =
                        recentlyAdded,
                    onTrackClick =
                        onTrackClick,
                    onRestartCurrentTrack =
                        onRestartCurrentTrack,
                    currentTrackId =
                        currentTrackId,
                )
            }
        }
    }
}

/**
 * Morphs between predefined Material 3 Expressive polygons.
 * No crossfade or scale animation is applied.
 */
@Composable
private fun ExpressiveMorphArtwork(
    artworkUri: String?,
    fallbackText: String,
    contentDescription: String,
    sizeDp: androidx.compose.ui.unit.Dp,
    isPressed: Boolean,
    startShape: RoundedPolygon,
    endShape: RoundedPolygon,
    label: String,
    modifier: Modifier = Modifier,
) {
    val morph = remember(startShape, endShape) {
        Morph(
            start = startShape,
            end = endShape,
        )
    }

    val morphProgress by
        animateFloatAsState(
            targetValue =
                if (isPressed) {
                    1f
                } else {
                    0f
                },
            animationSpec =
                tween(
                    durationMillis = 140,
                    easing = FastOutSlowInEasing,
                ),
            label = "${label}_progress",
        )

    Box(
        modifier = modifier,
    ) {
        AlbumArtwork(
            artworkUri = artworkUri,
            fallbackText = fallbackText,
            contentDescription = contentDescription,
            modifier =
                Modifier.fillMaxSize(),
            size = sizeDp,
            shape =
                MorphPolygonShape(
                    morph = morph,
                    percentage = morphProgress,
                ),
        )
    }
}

/**
 * Compose Shape adapter for the result of AndroidX graphics-shapes Morph.
 * The source geometries are MaterialShapes; this adapter maps the
 * generated path to the artwork bounds.
 */
private class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val matrix =
            Matrix().apply {
                scale(
                    x = size.width,
                    y = size.height,
                )
            }

        val path =
            morph.toPath(
                progress = percentage,
            )

        path.transform(matrix)

        val bounds =
            path.getBounds()

        val offsetX =
            size.width / 2f -
                bounds.center.x

        val offsetY =
            size.height / 2f -
                bounds.center.y

        path.translate(
            Offset(
                x = offsetX,
                y = offsetY,
            ),
        )

        return Outline.Generic(path)
    }
}

@Composable
private fun RecentPlaySection(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkSize = HomeArtworkSize
    val itemHeight = HomeArtworkRowHeight
    val visibleTracks = tracks.take(10)

    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(HomeArtworkSpacing),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recent_play,
                ),
            style =
                MaterialTheme.typography.titleLarge,
        )

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
            contentPadding =
                PaddingValues(
                    end = HomeArtworkSpacing,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(HomeArtworkSpacing),
        ) {
            itemsIndexed(
                items = visibleTracks,
                key = { _, track ->
                    track.id
                },
            ) { index, track ->
                val artist = track.artist

                val interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

                val isPressed by
                    interactionSource
                        .collectIsPressedAsState()

                Column(
                    modifier =
                        Modifier.width(
                            artworkSize,
                        ),
                ) {
                    ExpressiveMorphArtwork(
                        artworkUri =
                            track.artworkUri,
                        fallbackText =
                            track.title,
                        contentDescription =
                            stringResource(
                                R.string.action_play_track,
                                track.title,
                            ),
                        sizeDp =
                            artworkSize,
                        isPressed =
                            isPressed,
                        startShape =
                            MaterialShapes.Cookie4Sided,
                        endShape =
                            MaterialShapes.Circle,
                        label =
                            "recent_play_morph",
                        modifier =
                            Modifier
                                .width(artworkSize)
                                .aspectRatio(1f)
                                .clickable(
                                    interactionSource =
                                        interactionSource,
                                    indication = null,
                                ) {
                                    onTrackClick(
                                        visibleTracks,
                                        index,
                                    )
                                },
                    )

                    Text(
                        text = track.title,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.padding(
                                top = 6.dp,
                            ),
                    )

                    if (!artist.isNullOrBlank()) {
                        Text(
                            text = artist,
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentArtistsSection(
    artists: List<RecentArtist>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(HomeArtworkSpacing),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recent_artists,
                ),
            style =
                MaterialTheme.typography.titleLarge,
        )

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HomeArtworkRowHeight),
            contentPadding =
                PaddingValues(
                    end = HomeArtworkSpacing,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(HomeArtworkSpacing),
        ) {
            items(
                items = artists,
                key = { artist ->
                    artist.name.trim().lowercase()
                },
            ) { artist ->
                val interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

                val isPressed by
                    interactionSource
                        .collectIsPressedAsState()

                Column(
                    modifier =
                        Modifier
                            .width(HomeArtworkSize)
                            .clickable(
                                interactionSource =
                                    interactionSource,
                                indication = null,
                            ) {
                                onArtistClick(
                                    artist.name,
                                )
                            },
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    ExpressiveMorphArtwork(
                        artworkUri =
                            artist.artworkUri,
                        fallbackText =
                            artist.name,
                        contentDescription =
                            stringResource(
                                R.string.action_open_artist,
                                artist.name,
                            ),
                        sizeDp =
                            HomeArtworkSize,
                        isPressed =
                            isPressed,
                        startShape =
                            MaterialShapes.Circle,
                        endShape =
                            MaterialShapes.Cookie4Sided,
                        label =
                            "recent_artist_morph",
                        modifier =
                            Modifier
                                .width(HomeArtworkSize)
                                .aspectRatio(1f),
                    )

                    Text(
                        text = artist.name,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumsSection(
    albums: List<RecentAlbum>,
    onAlbumClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(HomeArtworkSpacing),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recent_albums,
                ),
            style =
                MaterialTheme.typography.titleLarge,
        )

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HomeArtworkRowHeight),
            contentPadding =
                PaddingValues(
                    end = HomeArtworkSpacing,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(HomeArtworkSpacing),
        ) {
            items(
                items = albums,
                key = { album ->
                    "${album.title.trim().lowercase()}|" +
                        album.artist
                            ?.trim()
                            ?.lowercase()
                            .orEmpty()
                },
            ) { album ->
                val interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

                val isPressed by
                    interactionSource
                        .collectIsPressedAsState()

                Column(
                    modifier =
                        Modifier
                            .width(HomeArtworkSize)
                            .clickable(
                                interactionSource =
                                    interactionSource,
                                indication = null,
                            ) {
                                onAlbumClick(
                                    album.title,
                                    album.artist,
                                )
                            },
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    ExpressiveMorphArtwork(
                        artworkUri =
                            album.artworkUri,
                        fallbackText =
                            album.title,
                        contentDescription =
                            stringResource(
                                R.string.action_open_album,
                                album.title,
                            ),
                        sizeDp =
                            HomeArtworkSize,
                        isPressed =
                            isPressed,
                        startShape =
                            MaterialShapes.Square,
                        endShape =
                            MaterialShapes.Cookie4Sided,
                        label =
                            "recent_album_morph",
                        modifier =
                            Modifier
                                .width(HomeArtworkSize)
                                .aspectRatio(1f),
                    )

                    Text(
                        text = album.title,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    if (!album.artist.isNullOrBlank()) {
                        Text(
                            text = album.artist,
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGenresSection(
    genres: List<RecentGenre>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(HomeArtworkSpacing),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recent_genres,
                ),
            style =
                MaterialTheme.typography.titleLarge,
        )

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(HomeArtworkRowHeight),
            contentPadding =
                PaddingValues(
                    end = HomeArtworkSpacing,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(HomeArtworkSpacing),
        ) {
            items(
                items = genres,
                key = { genre ->
                    genre.name.trim().lowercase()
                },
            ) { genre ->
                val interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

                val isPressed by
                    interactionSource
                        .collectIsPressedAsState()

                Column(
                    modifier =
                        Modifier
                            .width(HomeArtworkSize)
                            .clickable(
                                interactionSource =
                                    interactionSource,
                                indication = null,
                            ) {
                                onGenreClick(
                                    genre.name,
                                )
                            },
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    ExpressiveMorphArtwork(
                        artworkUri =
                            genre.artworkUri,
                        fallbackText =
                            genre.name,
                        contentDescription =
                            stringResource(
                                R.string.action_open_genre,
                                genre.name,
                            ),
                        sizeDp =
                            HomeArtworkSize,
                        isPressed =
                            isPressed,
                        startShape =
                            MaterialShapes.Cookie9Sided,
                        endShape =
                            MaterialShapes.Cookie4Sided,
                        label =
                            "recent_genre_morph",
                        modifier =
                            Modifier
                                .width(HomeArtworkSize)
                                .aspectRatio(1f),
                    )

                    Text(
                        text = genre.name,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recently_added,
                ),
            style =
                MaterialTheme.typography.titleLarge,
            modifier =
                Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                ),
        )

        if (tracks.isEmpty()) {
            EmptySectionText(
                text =
                    stringResource(
                        R.string.recently_added_empty,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                    ),
            )
        } else {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                tracks.forEachIndexed { index, track ->
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
                        isCurrentTrack =
                            isCurrentTrack,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySectionText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.bodyLarge,
        color =
            MaterialTheme
                .colorScheme
                .onSurfaceVariant,
        modifier =
            modifier.padding(
                vertical = 8.dp,
            ),
    )
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Text(
            text =
                stringResource(
                    R.string.home_error,
                ),
        )
    }
}