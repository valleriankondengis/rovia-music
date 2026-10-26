package com.rovia.music.feature.single

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
import androidx.compose.runtime.getValue
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
import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.TrackList

private val SinglePageNavigationBarHeight = 80.dp
private val SinglePageMiniPlayerHeight = 80.dp

/**
 * Independent single track page.
 * The layout and playback-list behavior are defined within this page, so its
 * design can evolve independently from the other library detail pages.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SinglePage(
    releaseKey: String,
    catalogTracks: List<LocalAudioTrack>,
    playbackController: PlaybackController,
    onBack: () -> Unit
) {
    val release = remember(catalogTracks, releaseKey) {
        AlbumParser.parse(catalogTracks).firstOrNull { candidate ->
            candidate.key == releaseKey &&
                (candidate.type == AlbumReleaseType.SINGLE ||
                    candidate.type == AlbumReleaseType.EP)
        }
    }
    val pageTitle = release?.title ?: stringResource(R.string.artist_songs_and_eps)
    val pageTracks = release?.tracks.orEmpty()

    val backContentDescription = stringResource(R.string.player_back)
    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset + TopAppBarDefaults.TopAppBarExpandedHeight + 12.dp

    val contentBottomPadding =
        navigationBarBottomInset +
            SinglePageNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) SinglePageMiniPlayerHeight else 0.dp

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
                    currentMediaId = player.currentMediaItem?.mediaId
                }
            }

            controller.addListener(listener)
            currentMediaId = controller.currentMediaItem?.mediaId

            onDispose {
                controller.removeListener(listener)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (pageTracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentTopPadding,
                        bottom = contentBottomPadding
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.library_no_tracks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            TrackList(
                tracks = pageTracks,
                onTrackClick = { track, index ->
                    val controller = mediaController
                    if (controller != null) {
                        val isCurrentTrack = currentMediaId == track.id.toString()

                        if (isCurrentTrack) {
                            if (controller.isPlaying) {
                                controller.pause()
                            } else {
                                if (controller.playbackState == Player.STATE_ENDED) {
                                    controller.seekToDefaultPosition()
                                }
                                controller.play()
                            }
                        } else {
                            playbackController.playQueue(
                                tracks = pageTracks,
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
                .statusBarsPadding()
                .padding(horizontal = 10.dp),
            title = {
                Text(
                    text = pageTitle,
                    maxLines = 1
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = backContentDescription
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}