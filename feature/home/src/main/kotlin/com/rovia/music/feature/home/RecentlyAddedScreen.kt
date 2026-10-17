
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.core.library.MusicRepository
import com.rovia.music.core.model.Track
import com.rovia.music.core.playback.PlaybackController
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.ExpressiveScrollbar
import com.rovia.music.core.ui.component.TrackRow

private val RecentlyAddedListTopCornerRadius = 28.dp

@Composable
fun RecentlyAddedRoute(
    musicRepository: MusicRepository,
    playbackController: PlaybackController,
    onBack: () -> Unit,
) {
    /*
     * Home observes only its five latest tracks.
     * This route observes the complete Recently Added collection.
     */
    val recentlyAddedFlow =
        remember(musicRepository) {
            musicRepository.observeRecentlyAdded(
                limit = Int.MAX_VALUE,
            )
        }

    val tracks by
        recentlyAddedFlow.collectAsStateWithLifecycle(
            initialValue = emptyList(),
        )

    val playbackState by
        playbackController.playbackState
            .collectAsStateWithLifecycle()

    RecentlyAddedScreen(
        tracks = tracks,
        currentTrackId = playbackState.currentTrack?.id,
        hasMiniPlayer = playbackState.currentTrack != null,
        onBack = onBack,
        onTrackClick = { selectedTracks, startIndex ->
            playbackController.playQueue(
                tracks = selectedTracks,
                startIndex = startIndex,
            )
        },
        onRestartCurrentTrack = {
            playbackController.restartCurrentTrack()
        },
    )
}

@Composable
private fun RecentlyAddedScreen(
    tracks: List<Track>,
    currentTrackId: Long?,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
) {
    val listState = rememberLazyListState()

    val bottomContentPadding =
        if (hasMiniPlayer) 200.dp else 100.dp

    val panelShape =
        RoundedCornerShape(
            topStart = RecentlyAddedListTopCornerRadius,
            topEnd = RecentlyAddedListTopCornerRadius,
        )

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier = Modifier.padding(
                horizontal = 4.dp,
            ),
            title = {
                Text(
                    text = stringResource(
                        R.string.section_recently_added,
                    ),
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
                                    MaterialTheme.colorScheme
                                        .surfaceContainerHigh,
                                contentColor =
                                    MaterialTheme.colorScheme
                                        .onSurface,
                            ),
                ) {
                    Icon(
                        painter = painterResource(
                            CoreUiR.drawable.ic_arrow_back,
                        ),
                        contentDescription = stringResource(
                            R.string.recent_collection_back,
                        ),
                    )
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(panelShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            if (tracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(
                            R.string.recently_added_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = bottomContentPadding,
                    ),
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { index, track ->
                            "recently_added_${index}_${track.id}"
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

                ExpressiveScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}
