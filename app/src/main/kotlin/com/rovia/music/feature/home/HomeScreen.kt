package com.rovia.music.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.TrackList
import kotlinx.coroutines.CancellationException

private val HomeNavigationBarHeight = 80.dp
private val HomeMiniPlayerHeight = 80.dp
private val HomePreviewTrackHeight = 74.dp
private val HomeCardHorizontalMargin = 16.dp
private val HomeTopBarHorizontalPadding = 10.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
    onOpenRecentlyAdded: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    var hasAudioPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    val catalog by audioRepository.catalogState.collectAsStateWithLifecycle()
    val recentlyAddedTracks = catalog.recentlyAddedTracks
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
        if (!hasAudioPermission) return@LaunchedEffect

        try {
            audioRepository.refresh(forceRefresh = reloadRequest > 0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // CatalogState retains a usable cache if a background refresh fails.
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RecentlyAddedCard(
                    tracks = recentlyAddedTracks,
                    currentMediaId = currentMediaId,
                    itemsEnabled = mediaController != null,
                    hasAudioPermission = hasAudioPermission,
                    isLoading = isLoading,
                    loadFailed = loadFailed,
                    onGrantPermission = {
                        permissionLauncher.launch(
                            Manifest.permission.READ_MEDIA_AUDIO
                        )
                    },
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
                    onOpenRecentlyAdded = onOpenRecentlyAdded,
                    modifier = Modifier.padding(
                        horizontal = HomeCardHorizontalMargin
                    )
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
                .statusBarsPadding()
                .padding(
                    horizontal = HomeTopBarHorizontalPadding
                ),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun RecentlyAddedCard(
    tracks: List<LocalAudioTrack>,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    hasAudioPermission: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
    onTrackClick: (LocalAudioTrack, Int) -> Unit,
    onOpenRecentlyAdded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewTracks = tracks.take(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    R.string.home_recently_added_title
                ),
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                ),
                style = MaterialTheme.typography.titleLarge
            )

            when {
                !hasAudioPermission -> {
                    CardMessage(
                        message = stringResource(
                            R.string.library_permission_message
                        ),
                        actionLabel = stringResource(
                            R.string.library_grant_permission
                        ),
                        onAction = onGrantPermission
                    )
                }

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
                        message = stringResource(
                            R.string.home_recently_added_load_error
                        ),
                        actionLabel = stringResource(
                            R.string.library_retry
                        ),
                        onAction = onRetry
                    )
                }

                previewTracks.isEmpty() -> {
                    Text(
                        text = stringResource(
                            R.string.home_recently_added_empty
                        ),
                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 24.dp
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    TrackList(
                        tracks = previewTracks,
                        onTrackClick = onTrackClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                HomePreviewTrackHeight *
                                    previewTracks.size
                            ),
                        outerPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = 1.dp
                        ),
                        contentInsetPadding = PaddingValues(0.dp),
                        horizontalPadding = 18.dp,
                        verticalSpacing = 1.dp,
                        currentTrackId = currentMediaId,
                        itemsEnabled = itemsEnabled,
                        userScrollEnabled = false
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    ),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onOpenRecentlyAdded,
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
                            R.drawable.ic_arrow_forward
                        ),
                        contentDescription = stringResource(
                            R.string.home_recently_added_view_all
                        )
                    )
                }
            }
        }
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