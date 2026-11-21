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
import androidx.compose.material3.ContainedLoadingIndicator
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
import com.rovia.music.ui.theme.ArtworkContentTheme
import com.rovia.music.ui.theme.BackdropContentTheme
import com.rovia.music.ui.components.AddToPlaylistDialog
import com.rovia.music.ui.components.PlaylistArtwork
import com.rovia.music.ui.components.PlaylistNameDialog
import com.rovia.music.ui.components.TrackInfoSheetHost
import com.rovia.music.ui.components.TrackMenuItem
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.components.rememberTrackInfoState
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

private val PlaylistPageNavigationBarHeight = 80.dp
private val PlaylistPageMiniPlayerHeight = 80.dp

/**
 * Playlist detail page. The hero artwork is the playlist name itself
 * (see PlaylistArtwork); playlists carry no images.
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

    BackdropContentTheme {
        val (playlistSeedColor, _) = playlistArtworkColors(pageTitle)

        ArtworkContentTheme(
            seedColor = playlistSeedColor,
            darkTheme = true,
        ) {
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
                    PlaylistBackdrop(
                        name = pageTitle,
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
                                ContainedLoadingIndicator()
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
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.playlist_no_tracks),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f),
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
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(3.dp))

                                            Text(
                                                text = trackCountLabel,
                                                modifier = Modifier.fillMaxWidth(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.7f),
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

/**
 * Backdrop for the playlist page, built from the playlist artwork: the
 * palette color derived from the playlist name, rendered large with the
 * name typography and blurred over a surface color from the same palette.
 */
@Composable
private fun PlaylistBackdrop(
    name: String,
    modifier: Modifier = Modifier,
) {
    val materialSurface = MaterialTheme.colorScheme.surface
    val isDarkTheme = materialSurface.luminance() < 0.5f
    val (containerColor, contentColor) = playlistArtworkColors(name)
    val targetBackgroundColor = remember(containerColor, isDarkTheme) {
        playlistBackdropSurfaceColor(containerColor, isDarkTheme)
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 480),
        label = "playlist-backdrop-background-color",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(1f / 4f)
                .fillMaxHeight(1f / 4f)
                .graphicsLayer {
                    scaleX = 4f
                    scaleY = 4f
                }
                .blur(
                    blurRadiusSpec = BlurRadiusSpec.verticalGradient(
                        startRadius = 20.dp,
                        endRadius = 30.dp,
                    ),
                    edgeTreatment = BlurredEdgeTreatment.Rectangle,
                )
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim(),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.16f else 0.10f,
                            ),
                            0.36f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.22f else 0.18f,
                            ),
                            0.66f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.40f else 0.34f,
                            ),
                            1f to backgroundColor.copy(
                                alpha = if (isDarkTheme) 0.64f else 0.50f,
                            ),
                        ),
                    ),
                ),
        )

        val topScrim = Color.Black.copy(
            alpha = if (isDarkTheme) 0.28f else 0.10f,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to topScrim,
                        0.14f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * The palette entry derived from a playlist name. Kept in step with
 * PlaylistArtwork so the backdrop always matches the artwork.
 */
@Composable
private fun playlistArtworkColors(name: String): Pair<Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(colorScheme) {
        listOf(
            colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
            colorScheme.secondaryContainer to colorScheme.onSecondaryContainer,
            colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
            colorScheme.primary to colorScheme.onPrimary,
            colorScheme.secondary to colorScheme.onSecondary,
            colorScheme.tertiary to colorScheme.onTertiary,
            colorScheme.surfaceContainerHighest to colorScheme.onSurface,
        )
    }
    return remember(name, palette) {
        val index = (name.trim().lowercase().hashCode() and Int.MAX_VALUE) % palette.size
        palette[index]
    }
}

private fun playlistBackdropSurfaceColor(
    containerColor: Color,
    isDarkTheme: Boolean,
): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(
        AndroidColor.rgb(
            (containerColor.red * 255).roundToInt(),
            (containerColor.green * 255).roundToInt(),
            (containerColor.blue * 255).roundToInt(),
        ),
        hsv,
    )
    val saturation = hsv[1]
    val adjusted = if (isDarkTheme) {
        floatArrayOf(
            hsv[0],
            (0.28f + saturation * 0.45f).coerceIn(0.28f, 0.62f),
            (0.12f + saturation * 0.06f).coerceIn(0.12f, 0.18f),
        )
    } else {
        floatArrayOf(
            hsv[0],
            (saturation * 0.25f).coerceIn(0.08f, 0.24f),
            0.97f,
        )
    }
    return Color(AndroidColor.HSVToColor(adjusted))
}
