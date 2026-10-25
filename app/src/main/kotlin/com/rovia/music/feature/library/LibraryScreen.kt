package com.rovia.music.feature.library

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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

private val LibraryNavigationBarHeight = 80.dp
private val MiniPlayerReservedHeight = 80.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
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
    val tracks = catalog.tracks
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

    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            12.dp

    val miniPlayerBottomPadding = if (currentMediaId != null) {
        MiniPlayerReservedHeight
    } else {
        0.dp
    }

    val contentBottomPadding =
        systemNavigationBarBottomInset +
            LibraryNavigationBarHeight +
            16.dp +
            miniPlayerBottomPadding

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
            // CatalogState keeps the previous catalog available if refresh fails.
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !hasAudioPermission -> {
                LibraryMessage(
                    message = stringResource(
                        R.string.library_permission_message
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        ),
                    actionLabel = stringResource(
                        R.string.library_grant_permission
                    ),
                    onAction = {
                        permissionLauncher.launch(
                            Manifest.permission.READ_MEDIA_AUDIO
                        )
                    }
                )
            }

            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                LibraryMessage(
                    message = stringResource(
                        R.string.library_load_error
                    ),
                    modifier = Modifier
                        .fillMaxSize()
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
                LibraryMessage(
                    message = stringResource(
                        R.string.library_no_tracks
                    ),
                    modifier = Modifier
                        .fillMaxSize()
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
                            val isCurrentTrack =
                                currentMediaId == track.id.toString()

                            if (isCurrentTrack) {
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
                    modifier = Modifier.fillMaxSize(),
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
            title = {
                Text(
                    text = stringResource(R.string.library_title)
                )
            },
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
                .padding(horizontal = 10.dp),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun LibraryMessage(
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
        Text(text = message)

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}