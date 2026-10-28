package com.rovia.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.fadeScrimProgressiveBlur
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import kotlinx.coroutines.CancellationException

private val RecentlyAddedNavigationBarHeight = 80.dp
private val RecentlyAddedMiniPlayerHeight = 80.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeRecentlyAddedScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
    onBack: () -> Unit
) {
    val backContentDescription = stringResource(R.string.player_back)
    val hasAudioPermission = rememberAudioPermissionGranted()

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    val catalog by audioRepository.catalogState.collectAsStateWithLifecycle()
    val tracks = catalog.recentlyAddedTracks
    val isLoading = hasAudioPermission && !catalog.isLoaded && !catalog.loadFailed
    val loadFailed = catalog.loadFailed

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
            12.dp

    val contentBottomPadding =
        navigationBarBottomInset +
            RecentlyAddedNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) {
                RecentlyAddedMiniPlayerHeight
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
        if (!hasAudioPermission) return@LaunchedEffect

        try {
            audioRepository.refresh(forceRefresh = reloadRequest > 0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Keep displaying the shared cache when a refresh fails.
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !hasAudioPermission -> {
                RecentlyAddedMessage(
                    message = stringResource(R.string.home_recently_added_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        )
                )
            }

            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            loadFailed -> {
                RecentlyAddedMessage(
                    message = stringResource(
                        R.string.home_recently_added_load_error
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        ),
                    actionLabel = stringResource(
                        R.string.library_retry
                    ),
                    onAction = {
                        reloadRequest += 1
                    }
                )
            }

            tracks.isEmpty() -> {
                RecentlyAddedMessage(
                    message = stringResource(
                        R.string.home_recently_added_empty
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        )
                )
            }

            else -> {
                TrackList(
                    tracks = tracks,
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
                                    tracks = tracks,
                                    startIndex = index
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                    outerPadding = PaddingValues(
                        horizontal = 0.dp,
                        vertical = 1.dp
                    ),
                    contentInsetPadding = PaddingValues(
                        top = contentTopPadding,
                        bottom = contentBottomPadding
                    ),
                    horizontalPadding = 18.dp,
                    verticalSpacing = 1.dp,
                    currentTrackId = currentMediaId,
                    itemsEnabled = mediaController != null
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            title = {
                Text(
                    text = stringResource(
                        R.string.home_recently_added_title
                    ),
                    maxLines = 1
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_arrow_back
                        ),
                        contentDescription = backContentDescription
                    )
                }
            },
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
private fun RecentlyAddedMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}
