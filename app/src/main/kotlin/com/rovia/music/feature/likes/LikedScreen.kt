package com.rovia.music.feature.likes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.PlaylistSummary
import com.rovia.music.data.repository.UserLibraryRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.queue.PlaybackOrigin
import com.rovia.music.playback.queue.PlaybackOriginType
import com.rovia.music.ui.theme.BackdropContentTheme
import com.rovia.music.ui.components.AddToPlaylistDialog
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.TrackInfoSheetHost
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.components.rememberTrackInfoState

private val LikedPageNavigationBarHeight = 80.dp
private val LikedPageMiniPlayerHeight = 80.dp

/**
 * Liked songs as a collection page: the hero artwork is the cover of the
 * most recently liked track, and unliking a track removes it from the list.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LikedScreen(
    tracks: List<LocalAudioTrack>,
    playlists: List<PlaylistSummary>,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onToggleLike: (LocalAudioTrack) -> Unit,
    onAddToPlaylist: (Long, LocalAudioTrack) -> Unit,
    onCreatePlaylist: (String, LocalAudioTrack) -> Unit,
) {
    val pageTitle = stringResource(R.string.liked_songs_title)
    val heroTrack = remember(tracks) { tracks.firstOrNull() }
    val artworkUri = heroTrack?.uri?.toString()
    val trackCountLabel = pluralStringResource(
        R.plurals.library_track_count,
        tracks.size,
        tracks.size
    )
    val likedKeys = remember(tracks) {
        tracks.map { track -> UserLibraryRepository.likedTrackKey(track) }.toSet()
    }

    val backContentDescription = stringResource(R.string.player_back)
    val mediaController by playbackController.mediaController.collectAsStateWithLifecycle()
    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }
    val trackInfoState = rememberTrackInfoState()

    var pickerTrack by remember { mutableStateOf<LocalAudioTrack?>(null) }

    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentTopPadding = statusBarTopInset + TopAppBarDefaults.TopAppBarExpandedHeight + 12.dp
    val contentBottomPadding = navigationBarBottomInset + LikedPageNavigationBarHeight + 16.dp +
        if (currentMediaId != null) LikedPageMiniPlayerHeight else 0.dp

    DisposableEffect(mediaController) {
        val controller = mediaController
        if (controller == null) {
            currentMediaId = null
            onDispose {}
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    currentMediaId = player.currentMediaItem?.mediaId
                }
            }
            controller.addListener(listener)
            currentMediaId = controller.currentMediaItem?.mediaId
            onDispose { controller.removeListener(listener) }
        }
    }

    BackdropContentTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artworkSize = minOf(maxWidth * 0.78f, 340.dp, maxHeight * 0.36f).coerceAtLeast(150.dp)
            val playButtonWidth = (maxWidth * 0.66f).coerceAtMost(360.dp).coerceAtLeast(220.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                ArtworkBackdrop(
                    artworkUri = artworkUri,
                    foregroundArtworkHeight = 0.dp,
                    modifier = Modifier.fillMaxSize()
                )

                if (tracks.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(top = contentTopPadding, bottom = contentBottomPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.liked_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    TrackList(
                        tracks = tracks,
                        onTrackClick = { track, index ->
                            val controller = mediaController
                            if (controller != null) {
                                if (currentMediaId == track.id.toString()) {
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
                                        tracks = tracks,
                                        startIndex = index,
                                        origin = PlaybackOrigin(
                                            type = PlaybackOriginType.LIKES,
                                            entityKey = "likes",
                                            title = pageTitle,
                                            subtitle = trackCountLabel,
                                            artworkSourceUri = artworkUri,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize(),
                        outerPadding = PaddingValues(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        ),
                        horizontalPadding = 18.dp,
                        verticalSpacing = 1.dp,
                        currentTrackId = currentMediaId,
                        itemsEnabled = mediaController != null,
                        onTrackInfoClick = trackInfoState::show,
                        likedTrackKeys = likedKeys,
                        onToggleLike = onToggleLike,
                        onAddToPlaylistClick = { track -> pickerTrack = track },
                        headerContent = {
                            item(key = "liked-hero") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AlbumArtwork(
                                        artworkUri = artworkUri,
                                        fallbackText = pageTitle,
                                        contentDescription = pageTitle,
                                        modifier = Modifier.size(artworkSize),
                                        size = artworkSize,
                                        shape = MaterialTheme.shapes.large
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = pageTitle,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = trackCountLabel,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(22.dp))
                                    Button(
                                        onClick = {
                                            if (mediaController != null) {
                                                playbackController.playQueue(
                                                    tracks = tracks,
                                                    startIndex = 0,
                                                    origin = PlaybackOrigin(
                                                        type = PlaybackOriginType.LIKES,
                                                        entityKey = "likes",
                                                        title = pageTitle,
                                                        subtitle = trackCountLabel,
                                                        artworkSourceUri = artworkUri,
                                                    ),
                                                )
                                            }
                                        },
                                        enabled = mediaController != null && tracks.isNotEmpty(),
                                        modifier = Modifier.width(playButtonWidth).height(64.dp),
                                        shape = RoundedCornerShape(percent = 50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_play),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.player_play),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    )
                }

                TopAppBar(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding(),
                    title = {},
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
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                )
            }
        }
    }

    TrackInfoSheetHost(trackInfoState)

    pickerTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { pickerTrack = null },
            onSelectPlaylist = { playlistId ->
                pickerTrack = null
                onAddToPlaylist(playlistId, track)
            },
            onCreatePlaylist = { name ->
                pickerTrack = null
                onCreatePlaylist(name, track)
            },
        )
    }
}
