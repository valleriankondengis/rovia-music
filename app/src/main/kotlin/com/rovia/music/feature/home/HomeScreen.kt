package com.rovia.music.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.fadeScrimProgressiveBlur
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import kotlinx.coroutines.CancellationException

private val HomeNavigationBarHeight = 80.dp
private val HomeMiniPlayerHeight = 80.dp
private val HomeCarouselArtworkSize = 150.dp
private val HomeCarouselItemWidth = HomeCarouselArtworkSize
private val HomeCarouselItemHeight = 220.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
    onOpenRecentlyAdded: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val hasAudioPermission = rememberAudioPermissionGranted()

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var recentlyAddedTracks by remember {
        mutableStateOf<List<LocalAudioTrack>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var loadFailed by remember {
        mutableStateOf(false)
    }

    var reloadRequest by remember {
        mutableIntStateOf(0)
    }

    var currentMediaId by remember(mediaController) {
        mutableStateOf(
            mediaController?.currentMediaItem?.mediaId
        )
    }

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            16.dp

    val contentBottomPadding =
        navigationBarBottomInset +
            HomeNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) {
                HomeMiniPlayerHeight
            } else {
                0.dp
            }

    DisposableEffect(mediaController) {
        val controller = mediaController

        if (controller == null) {
            currentMediaId = null
            onDispose {}
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) {
                    currentMediaId =
                        player.currentMediaItem?.mediaId
                }
            }

            controller.addListener(listener)
            currentMediaId = controller.currentMediaItem?.mediaId

            onDispose {
                controller.removeListener(listener)
            }
        }
    }

    LaunchedEffect(hasAudioPermission, reloadRequest) {
        if (!hasAudioPermission) {
            recentlyAddedTracks = emptyList()
            isLoading = false
            loadFailed = false
            return@LaunchedEffect
        }

        isLoading = true
        loadFailed = false

        try {
            recentlyAddedTracks =
                audioRepository.loadRecentlyAddedTracks()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            recentlyAddedTracks = emptyList()
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
            contentPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RecentlyAddedCarouselSection(
                    tracks = recentlyAddedTracks,
                    currentMediaId = currentMediaId,
                    itemsEnabled = mediaController != null,
                    isLoading = isLoading,
                    loadFailed = loadFailed,
                    onRetry = {
                        reloadRequest += 1
                    },
                    onTrackClick = { track, index ->
                        val controller = mediaController

                        if (controller != null) {
                            if (currentMediaId == track.id.toString()) {
                                if (controller.isPlaying) {
                                    controller.pause()
                                } else {
                                    if (
                                        controller.playbackState ==
                                        Player.STATE_ENDED
                                    ) {
                                        controller.seekToDefaultPosition()
                                    }

                                    controller.play()
                                }
                            } else {
                                playbackController.playQueue(
                                    tracks = recentlyAddedTracks,
                                    startIndex = index
                                )
                            }
                        }
                    },
                    onOpenRecentlyAdded = onOpenRecentlyAdded
                )
            }
        }

        FadeScrim(
            edge = FadeScrimEdge.Top,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        FadeScrim(
            edge = FadeScrimEdge.Bottom,
            modifier = Modifier.align(Alignment.BottomCenter),
            withMiniPlayer = currentMediaId != null
        )

        TopAppBar(
            title = {},
            actions = {
                FilledIconButton(
                    onClick = onOpenSettings,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings_title)
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }
}

@Composable
private fun RecentlyAddedCarouselSection(
    tracks: List<LocalAudioTrack>,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onTrackClick: (LocalAudioTrack, Int) -> Unit,
    onOpenRecentlyAdded: () -> Unit
) {
    val previewTracks = tracks.take(10)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_recently_added_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FilledIconButton(
                onClick = onOpenRecentlyAdded,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(
                        R.string.home_recently_added_view_all
                    )
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            loadFailed -> {
                CardMessage(
                    message = stringResource(R.string.home_recently_added_load_error),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = onRetry
                )
            }

            previewTracks.isEmpty() -> {
                Text(
                    text = stringResource(R.string.home_recently_added_empty),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeCarouselItemHeight),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(
                        items = previewTracks,
                        key = { _, track -> "recently-added:${track.id}" }
                    ) { index, track ->
                        RecentlyAddedCarouselItem(
                            track = track,
                            isCurrent = track.id.toString() == currentMediaId,
                            itemsEnabled = itemsEnabled,
                            onClick = {
                                onTrackClick(track, index)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedCarouselItem(
    track: LocalAudioTrack,
    isCurrent: Boolean,
    itemsEnabled: Boolean,
    onClick: () -> Unit
) {
    val title = track.title
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_track)
    val artist = track.artist
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_artist)

    Column(
        modifier = Modifier
            .width(HomeCarouselItemWidth)
            .height(HomeCarouselItemHeight)
            .clickable(enabled = itemsEnabled, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArtwork(
            artworkUri = track.uri.toString(),
            fallbackText = title,
            contentDescription = title,
            modifier = Modifier.size(HomeCarouselArtworkSize),
            size = HomeCarouselArtworkSize,
            shape = MaterialTheme.shapes.large
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CardMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onAction
        ) {
            Text(actionLabel)
        }
    }
}