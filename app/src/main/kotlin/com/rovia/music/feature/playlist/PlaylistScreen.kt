package com.rovia.music.feature.playlist

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rovia.music.data.repository.PlaylistDetail
import com.rovia.music.data.repository.PlaylistSummary
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.queue.PlaybackOrigin
import com.rovia.music.playback.queue.PlaybackOriginType
import com.rovia.music.ui.components.AddToPlaylistDialog
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.PlaylistArtwork
import com.rovia.music.ui.components.PlaylistNameDialog
import com.rovia.music.ui.components.TrackInfoSheetHost
import com.rovia.music.ui.components.TrackMenuItem
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.components.rememberTrackInfoState

private val PlaylistPageNavigationBarHeight = 80.dp
private val PlaylistPageMiniPlayerHeight = 80.dp

/**
 * Playlist detail page, modeled on AlbumPage. The hero artwork is the
 * playlist name itself (see PlaylistArtwork); playlists carry no images.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistScreen(
    detail: PlaylistDetail?,
    playlists: List<PlaylistSummary>,
    likedTrackKeys: Set<String>,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onToggleLike: (LocalAudioTrack) -> Unit,
    onAddToPlaylist: (Long, LocalAudioTrack) -> Unit,
    onCreatePlaylistAndAdd: (String, LocalAudioTrack) -> Unit,
) {

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }
    val trackInfoState = rememberTrackInfoState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var headerMenuExpanded by remember { mutableStateOf(false) }
    var pickerTrack by remember { mutableStateOf<LocalAudioTrack?>(null) }

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
            PlaylistPageNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) PlaylistPageMiniPlayerHeight else 0.dp

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

    val pageTracks = detail?.playableTracks.orEmpty()
    val pageTitle = detail?.name ?: stringResource(R.string.search_section_playlists)
    val trackCountLabel = pluralStringResource(
        R.plurals.library_track_count,
        pageTracks.size,
        pageTracks.size
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkSize = minOf(
            maxWidth * 0.78f,
            340.dp,
            maxHeight * 0.36f
        ).coerceAtLeast(150.dp)
        val playButtonWidth = (maxWidth * 0.66f)
            .coerceAtMost(360.dp)
            .coerceAtLeast(220.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkBackdrop(
                artworkUri = null,
                foregroundArtworkHeight = 0.dp,
                modifier = Modifier.fillMaxSize()
            )

            when {
                detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = contentTopPadding, bottom = contentBottomPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                pageTracks.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = contentTopPadding, bottom = contentBottomPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PlaylistArtwork(
                            name = pageTitle,
                            modifier = Modifier.size(artworkSize),
                            size = artworkSize,
                            shape = MaterialTheme.shapes.large
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.playlist_no_tracks),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    TrackList(
                        tracks = pageTracks,
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
                                        tracks = pageTracks,
                                        startIndex = index,
                                        origin = PlaybackOrigin(
                                            type = PlaybackOriginType.PLAYLIST,
                                            entityKey = "playlist:${detail.id}",
                                            title = pageTitle,
                                            subtitle = trackCountLabel,
                                            artworkSourceUri = null,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        outerPadding = PaddingValues(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        ),
                        horizontalPadding = 18.dp,
                        verticalSpacing = 1.dp,
                        currentTrackId = currentMediaId,
                        itemsEnabled = mediaController != null,
                        onTrackInfoClick = trackInfoState::show,
                        likedTrackKeys = likedTrackKeys,
                        onToggleLike = onToggleLike,
                        onAddToPlaylistClick = { track -> pickerTrack = track },
                        trackMenuContent = { track, _ ->
                            TrackMenuItem(
                                text = stringResource(R.string.playlist_remove_from),
                                onClick = {
                                    detail.tracks
                                        .firstOrNull { item ->
                                            item.track.id == track.id && item.track.uri == track.uri
                                        }
                                        ?.let { item -> onRemoveTrack(item.entryId) }
                                },
                            )
                        },
                        headerContent = {
                            item(key = "playlist-hero-${detail.id}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 0.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    PlaylistArtwork(
                                        name = pageTitle,
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
                                        style = MaterialTheme.typography.bodyMedium,
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
                                                    tracks = pageTracks,
                                                    startIndex = 0,
                                                    origin = PlaybackOrigin(
                                                        type = PlaybackOriginType.PLAYLIST,
                                                        entityKey = "playlist:${detail.id}",
                                                        title = pageTitle,
                                                        subtitle = trackCountLabel,
                                                        artworkSourceUri = null,
                                                    ),
                                                )
                                            }
                                        },
                                        enabled = mediaController != null && pageTracks.isNotEmpty(),
                                        modifier = Modifier
                                            .width(playButtonWidth)
                                            .height(64.dp),
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
            }

            TopAppBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
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
                            contentDescription = stringResource(R.string.player_back)
                        )
                    }
                },
                actions = {
                    if (detail != null) {
                        Box {
                            FilledIconButton(
                                onClick = { headerMenuExpanded = true },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.track_menu_open)
                                )
                            }

                            DropdownMenu(
                                expanded = headerMenuExpanded,
                                onDismissRequest = { headerMenuExpanded = false },
                                shape = MaterialTheme.shapes.large,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.playlist_rename),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    onClick = {
                                        headerMenuExpanded = false
                                        showRenameDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.playlist_delete),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    onClick = {
                                        headerMenuExpanded = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
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

    if (showRenameDialog && detail != null) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_rename,
            confirmRes = R.string.playlist_save_action,
            initialName = detail.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRenamePlaylist(newName)
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(text = stringResource(R.string.playlist_delete))
            },
            text = {
                Text(text = stringResource(R.string.playlist_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePlaylist()
                    }
                ) {
                    Text(text = stringResource(R.string.playlist_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    pickerTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { pickerTrack = null },
            onSelectPlaylist = { targetPlaylistId ->
                pickerTrack = null
                onAddToPlaylist(targetPlaylistId, track)
            },
            onCreatePlaylist = { name ->
                pickerTrack = null
                onCreatePlaylistAndAdd(name, track)
            },
        )
    }

    TrackInfoSheetHost(trackInfoState)
}
