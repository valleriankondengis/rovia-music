@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.AlbumArtwork
import com.rovia.music.core.ui.component.TrackRow

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTrackClick: (List<Track>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    hasMiniPlayer: Boolean = false,
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
                onTrackClick =
                    onTrackClick,
                onOpenSettings =
                    onOpenSettings,
                hasMiniPlayer =
                    hasMiniPlayer,
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
    onTrackClick: (List<Track>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    hasMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    /*
     * MainNavigation now keeps NavDisplay full-height.
     *
     * NavigationBar is an overlay layer, so the
     * LazyColumn needs its own bottom clearance.
     *
     * The larger values ensure the final TrackRow
     * can be scrolled completely above the NavigationBar.
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
                                MaterialTheme
                                    .shapes
                                    .medium,
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
                    bottom =
                        bottomContentPadding,
                ),
        ) {
            if (recentPlays.isNotEmpty()) {
                item {
                    RecentPlaySection(
                        tracks =
                            recentPlays,
                        onTrackClick =
                            onTrackClick,
                        modifier =
                            Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 28.dp,
                            ),
                    )
                }
            }

            item {
                RecentlyAddedSection(
                    tracks =
                        recentlyAdded,
                    onTrackClick =
                        onTrackClick,
                )
            }
        }
    }
}

@Composable
private fun RecentPlaySection(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowCount =
        if (tracks.size == 1) {
            1
        } else {
            2
        }

    val artworkSize =
        156.dp

    val itemHeight =
        204.dp

    val rowSpacing =
        12.dp

    val gridHeight =
        (itemHeight * rowCount) +
            (
                rowSpacing *
                    (rowCount - 1)
            )

    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recent_play,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
        )

        LazyHorizontalGrid(
            rows =
                GridCells.Fixed(
                    rowCount,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        gridHeight,
                    ),
            contentPadding =
                PaddingValues(
                    end = 12.dp,
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    rowSpacing,
                ),
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track ->
                    track.id
                },
            ) { index, track ->
                val artist =
                    track.artist

                val interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

                val isPressed by
                    interactionSource
                        .collectIsPressedAsState()

                val morphProgress by
                    animateFloatAsState(
                        targetValue =
                            if (isPressed) {
                                1f
                            } else {
                                0f
                            },
                        animationSpec =
                            spring(
                                dampingRatio =
                                    0.4f,
                                stiffness =
                                    Spring
                                        .StiffnessMedium,
                            ),
                        label =
                            "recent_play_shape_morph",
                    )

                val artworkMorph =
                    remember {
                        Morph(
                            start =
                                MaterialShapes.Circle,
                            end =
                                MaterialShapes.Cookie4Sided,
                        )
                    }

                Column(
                    modifier =
                        Modifier.width(
                            artworkSize,
                        ),
                ) {
                    AlbumArtwork(
                        artworkUri =
                            track.artworkUri,
                        fallbackText =
                            track.title,
                        contentDescription =
                            stringResource(
                                R.string.action_play_track,
                                track.title,
                            ),
                        modifier =
                            Modifier
                                .width(
                                    artworkSize,
                                )
                                .aspectRatio(
                                    1f,
                                )
                                .clickable(
                                    interactionSource =
                                        interactionSource,
                                    indication = null,
                                ) {
                                    onTrackClick(
                                        tracks,
                                        index,
                                    )
                                },
                        size =
                            artworkSize,
                        shape =
                            MorphPolygonShape(
                                morph =
                                    artworkMorph,
                                percentage =
                                    morphProgress,
                            ),
                    )

                    Text(
                        text =
                            track.title,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.padding(
                                top = 6.dp,
                            ),
                    )

                    if (
                        !artist.isNullOrBlank()
                    ) {
                        Text(
                            text =
                                artist,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
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
private fun RecentlyAddedSection(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Int) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.section_recently_added,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
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
                    TrackRow(
                        track = track,
                        onClick = {
                            onTrackClick(
                                tracks,
                                index,
                            )
                        },
                        showAlbum = true,
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
            MaterialTheme
                .typography
                .bodyLarge,
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
                progress =
                    percentage,
            )

        path.transform(matrix)

        val bounds =
            path.getBounds()

        val targetCenterX =
            size.width / 2f

        val targetCenterY =
            size.height / 2f

        val offsetX =
            targetCenterX -
                bounds.center.x

        val offsetY =
            targetCenterY -
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