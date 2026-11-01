package com.rovia.music.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.LibraryCollection
import com.rovia.music.data.repository.LibraryFolder
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.fadeScrimProgressiveBlur
import com.rovia.music.ui.components.TrackInfoSheetHost
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.components.rememberTrackInfoState
import com.rovia.music.ui.components.TrackListItem
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted

private val LibraryNavigationBarHeight = 80.dp
private val MiniPlayerReservedHeight = 80.dp
private val LibraryCategorySelectorHeight = 56.dp
private val LibraryFolderHeaderHeight = 48.dp
private val LibraryHeaderControlsTopSpacing = 4.dp
private val LibraryCategoryContentSpacing = 12.dp
private val LibraryCategoryBreadcrumbSpacing = 4.dp
private val LibraryBreadcrumbContentSpacing = 12.dp

private enum class LibraryCategory(
    @StringRes val labelResource: Int
) {
    ALL_SONGS(R.string.library_category_all_songs),
    ARTISTS(R.string.library_category_artists),
    ALBUMS(R.string.library_category_albums),
    GENRES(R.string.library_category_genres),
    FOLDERS(R.string.library_category_folders)
}

private enum class LibraryRowKind {
    ARTIST,
    ALBUM,
    GENRE
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    playbackController: PlaybackController,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val hasAudioPermission = rememberAudioPermissionGranted()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val catalog = libraryState.catalog
    val tracks = catalog.tracks
    val isLoading = hasAudioPermission && (libraryState.isLoading || !libraryState.isCatalogLoaded)
    val loadFailed = libraryState.loadFailed

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    LaunchedEffect(hasAudioPermission) {
        libraryViewModel.onAudioPermissionChanged(hasAudioPermission)
    }

    var selectedCategory by rememberSaveable {
        mutableStateOf(LibraryCategory.ALL_SONGS)
    }

    var selectedFolderPath by rememberSaveable {
        mutableStateOf("")
    }

    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }
    val trackInfoState = rememberTrackInfoState()

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            LibraryHeaderControlsTopSpacing

    val categoryContentTopPadding =
        contentTopPadding +
            LibraryCategorySelectorHeight +
            LibraryCategoryContentSpacing

    val folderBreadcrumbTopPadding =
        contentTopPadding +
            LibraryCategorySelectorHeight +
            LibraryCategoryBreadcrumbSpacing

    val folderContentTopPadding =
        folderBreadcrumbTopPadding +
            LibraryFolderHeaderHeight +
            LibraryBreadcrumbContentSpacing

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

    val artistItems = catalog.artists
    val albumItems = catalog.albums
    val genreItems = catalog.genres
    val folderItems = catalog.folderChildren[selectedFolderPath].orEmpty()
    val selectedFolderTracks = catalog.folderTracks[selectedFolderPath].orEmpty()

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

    fun handleTrackClick(
        queueTracks: List<LocalAudioTrack>,
        track: LocalAudioTrack,
        index: Int
    ) {
        val controller = mediaController ?: return

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
                tracks = queueTracks,
                startIndex = index
            )
        }
    }

    val allSongsCategoryLabel = stringResource(LibraryCategory.ALL_SONGS.labelResource)
    val artistsCategoryLabel = stringResource(LibraryCategory.ARTISTS.labelResource)
    val albumsCategoryLabel = stringResource(LibraryCategory.ALBUMS.labelResource)
    val genresCategoryLabel = stringResource(LibraryCategory.GENRES.labelResource)
    val foldersCategoryLabel = stringResource(LibraryCategory.FOLDERS.labelResource)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasAudioPermission -> {
                LibraryMessage(
                    message = stringResource(R.string.library_no_tracks),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = categoryContentTopPadding,
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
                            top = categoryContentTopPadding,
                            bottom = contentBottomPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            loadFailed -> {
                LibraryMessage(
                    message = stringResource(R.string.library_load_error),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = categoryContentTopPadding,
                            bottom = contentBottomPadding
                        ),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = libraryViewModel::retry
                )
            }

            tracks.isEmpty() -> {
                LibraryMessage(
                    message = stringResource(R.string.library_no_tracks),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                        .padding(
                            top = categoryContentTopPadding,
                            bottom = contentBottomPadding
                        )
                )
            }

            selectedCategory == LibraryCategory.ALL_SONGS -> {
                TrackList(
                    tracks = tracks,
                    onTrackClick = { track, index ->
                        handleTrackClick(
                            queueTracks = tracks,
                            track = track,
                            index = index
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                    outerPadding = PaddingValues(
                        horizontal = 0.dp,
                        vertical = 1.dp
                    ),
                    contentInsetPadding = PaddingValues(
                        top = categoryContentTopPadding,
                        bottom = contentBottomPadding
                    ),
                    horizontalPadding = 18.dp,
                    verticalSpacing = 1.dp,
                    currentTrackId = currentMediaId,
                    itemsEnabled = mediaController != null,
                    onTrackInfoClick = trackInfoState::show,
                )
            }

            selectedCategory == LibraryCategory.ARTISTS -> {
                if (artistItems.isEmpty()) {
                    LibraryMessage(
                        message = stringResource(R.string.library_artists_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                            .padding(
                                top = categoryContentTopPadding,
                                bottom = contentBottomPadding
                            )
                    )
                } else {
                    LibraryEntityList(
                        items = artistItems,
                        kind = LibraryRowKind.ARTIST,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                        contentTopPadding = categoryContentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        onItemClick = onOpenArtist
                    )
                }
            }

            selectedCategory == LibraryCategory.ALBUMS -> {
                if (albumItems.isEmpty()) {
                    LibraryMessage(
                        message = stringResource(R.string.library_albums_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                            .padding(
                                top = categoryContentTopPadding,
                                bottom = contentBottomPadding
                            )
                    )
                } else {
                    LibraryEntityList(
                        items = albumItems,
                        kind = LibraryRowKind.ALBUM,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                        contentTopPadding = categoryContentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        onItemClick = onOpenAlbum
                    )
                }
            }

            selectedCategory == LibraryCategory.GENRES -> {
                if (genreItems.isEmpty()) {
                    LibraryMessage(
                        message = stringResource(R.string.library_genres_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                            .padding(
                                top = categoryContentTopPadding,
                                bottom = contentBottomPadding
                            )
                    )
                } else {
                    LibraryEntityList(
                        items = genreItems,
                        kind = LibraryRowKind.GENRE,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                        contentTopPadding = categoryContentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        onItemClick = onOpenGenre
                    )
                }
            }

            selectedCategory == LibraryCategory.FOLDERS -> {
                if (folderItems.isEmpty() && selectedFolderTracks.isEmpty()) {
                    LibraryMessage(
                        message = stringResource(R.string.library_folders_empty),
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null)
                            .padding(
                                top = folderContentTopPadding,
                                bottom = contentBottomPadding
                            )
                    )
                } else {
                    LibraryFolderContentList(
                        folderItems = folderItems,
                        tracks = selectedFolderTracks,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
                        contentTopPadding = folderContentTopPadding,
                        contentBottomPadding = contentBottomPadding,
                        currentTrackId = currentMediaId,
                        itemsEnabled = mediaController != null,
                        onFolderClick = { path -> selectedFolderPath = path },
                        onTrackInfoClick = trackInfoState::show,
                        onTrackClick = { track, index ->
                            handleTrackClick(
                                queueTracks = selectedFolderTracks,
                                track = track,
                                index = index
                            )
                        }
                    )
                }
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

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentTopPadding)
                .fillMaxWidth()
                .height(LibraryCategorySelectorHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val buttonGroupColorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            MaterialTheme(colorScheme = buttonGroupColorScheme) {
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(
                            menuState = menuState,
                        )
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryCategory.values().forEach { category ->
                        val categoryLabel = when (category) {
                            LibraryCategory.ALL_SONGS -> allSongsCategoryLabel
                            LibraryCategory.ARTISTS -> artistsCategoryLabel
                            LibraryCategory.ALBUMS -> albumsCategoryLabel
                            LibraryCategory.GENRES -> genresCategoryLabel
                            LibraryCategory.FOLDERS -> foldersCategoryLabel
                        }

                        toggleableItem(
                            checked = selectedCategory == category,
                            label = categoryLabel,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedCategory = category
                                    selectedFolderPath = ""
                                }
                            },
                        )
                    }
                }
            }
        }

        if (hasAudioPermission && libraryState.isCatalogLoaded && !loadFailed) {
            val headerTitle = if (
                selectedCategory == LibraryCategory.FOLDERS
            ) {
                selectedFolderPath
                    .substringAfterLast('/')
                    .takeIf(String::isNotBlank)
                    ?: stringResource(LibraryCategory.FOLDERS.labelResource)
            } else {
                null
            }

            val showCollectionHeader =
                selectedCategory == LibraryCategory.FOLDERS

            if (showCollectionHeader && headerTitle != null) {
                FolderBreadcrumb(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = folderBreadcrumbTopPadding)
                        .fillMaxWidth()
                        .height(LibraryFolderHeaderHeight),
                    selectedFolderPath = selectedFolderPath,
                    onNavigateToFolder = { path ->
                        selectedFolderPath = path
                    },
                )
            }
        }

        TopAppBar(
            title = {
                Text(stringResource(R.string.library_title))
            },
            actions = {
                FilledIconButton(
                    onClick = onOpenSettings,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

    TrackInfoSheetHost(trackInfoState)
}

@Composable
private fun FolderBreadcrumb(
    modifier: Modifier = Modifier,
    selectedFolderPath: String,
    onNavigateToFolder: (String) -> Unit,
) {
    val foldersLabel = stringResource(LibraryCategory.FOLDERS.labelResource)
    val segments = selectedFolderPath
        .trim('/')
        .split('/')
        .filter(String::isNotBlank)

    val breadcrumbColorScheme = MaterialTheme.colorScheme.copy(
        surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val breadcrumbScrollState = rememberScrollState()

    LaunchedEffect(selectedFolderPath, breadcrumbScrollState.maxValue) {
        breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
    }

    Row(
        modifier = modifier
            .horizontalScroll(breadcrumbScrollState)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialTheme(colorScheme = breadcrumbColorScheme) {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(
                        menuState = menuState,
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                toggleableItem(
                    checked = selectedFolderPath.isBlank(),
                    label = foldersLabel,
                    onCheckedChange = { checked ->
                        if (checked && selectedFolderPath.isNotBlank()) {
                            onNavigateToFolder("")
                        }
                    },
                )

                segments.forEachIndexed { index, segment ->
                    val folderPath = segments
                        .take(index + 1)
                        .joinToString("/")
                    val isCurrentFolder = index == segments.lastIndex

                    toggleableItem(
                        checked = isCurrentFolder,
                        label = segment,
                        onCheckedChange = { checked ->
                            if (checked && !isCurrentFolder) {
                                onNavigateToFolder(folderPath)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryEntityList(
    items: List<LibraryCollection>,
    kind: LibraryRowKind,
    modifier: Modifier = Modifier,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    onItemClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentTopPadding,
            end = 18.dp,
            bottom = contentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.key },
        ) { index, item ->
            val countLabel = pluralStringResource(
                R.plurals.library_track_count,
                item.trackCount,
                item.trackCount,
            )
            val supportingText = when (kind) {
                LibraryRowKind.ARTIST,
                LibraryRowKind.GENRE -> countLabel

                LibraryRowKind.ALBUM -> stringResource(
                    R.string.library_album_supporting,
                    countLabel,
                    item.detail ?: stringResource(R.string.player_unknown_artist),
                )
            }

            SegmentedListItem(
                selected = false,
                onClick = { onItemClick(item.destinationKey) },
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = items.size,
                ),
                leadingContent = {
                    AlbumArtwork(
                        artworkUri = item.artworkUri,
                        fallbackText = item.title,
                        contentDescription = item.title,
                        modifier = Modifier.size(52.dp),
                        size = 52.dp,
                        shape = if (kind == LibraryRowKind.ARTIST) {
                            CircleShape
                        } else {
                            RoundedCornerShape(14.dp)
                        },
                    )
                },
                supportingContent = {
                    Text(
                        text = supportingText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                content = {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryFolderContentList(
    folderItems: List<LibraryFolder>,
    tracks: List<LocalAudioTrack>,
    modifier: Modifier = Modifier,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    currentTrackId: String?,
    itemsEnabled: Boolean,
    onFolderClick: (String) -> Unit,
    onTrackInfoClick: (LocalAudioTrack) -> Unit,
    onTrackClick: (LocalAudioTrack, Int) -> Unit
) {
    val totalItemCount = folderItems.size + tracks.size
    val trackColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurface,
        trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedLeadingContentColor = MaterialTheme.colorScheme.onSurface,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentTopPadding,
            end = 18.dp,
            bottom = contentBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(
            items = folderItems,
            key = { _, item -> "folder:${item.path}" }
        ) { index, item ->
            SegmentedListItem(
                selected = false,
                onClick = { onFolderClick(item.path) },
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = totalItemCount
                ),
                colors = trackColors,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                leadingContent = {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                content = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(
                            R.string.library_folder_supporting,
                            pluralStringResource(
                                R.plurals.library_track_count,
                                item.trackCount,
                                item.trackCount,
                            ),
                            item.path,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        itemsIndexed(
            items = tracks,
            key = { _, track -> "track:${track.uri}" }
        ) { index, track ->
            val title = track.title.orEmpty()
                .trim()
                .ifBlank { stringResource(R.string.player_unknown_track) }
            val supportingText = track.artist.orEmpty()
                .trim()
                .ifBlank { track.album.orEmpty().trim() }
                .ifBlank { stringResource(R.string.player_unknown_artist) }

            TrackListItem(
                title = title,
                supportingText = supportingText,
                artworkUri = track.uri.toString(),
                durationMs = track.durationMs,
                selected = currentTrackId == track.id.toString(),
                enabled = itemsEnabled,
                onClick = { onTrackClick(track, index) },
                index = folderItems.size + index,
                count = totalItemCount,
                horizontalPadding = 0.dp,
                onTrackInfoClick = { onTrackInfoClick(track) },
            )
        }
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

