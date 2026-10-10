
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.model.MusicFolder
import com.rovia.music.core.model.Track
import com.rovia.music.core.ui.R as CoreUiR
import com.rovia.music.core.ui.component.TrackRow

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
    onFolderClick: (String) -> Unit = {},
    onBackFromFolder: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onShowAllSongs: () -> Unit = {},
    onShowRootFolders: () -> Unit = {},
    onSortOptionChange: (LibrarySortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    hasMiniPlayer: Boolean = false,
    onBrowseModeChange: (LibraryBrowseMode) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String, String?) -> Unit = { _, _ -> },
    onOpenGenre: (String) -> Unit = {},
    onBackFromCollection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        LibraryUiState.Loading ->
            LoadingContent(
                modifier = modifier,
            )

        is LibraryUiState.Content ->
            LibraryContent(
                uiState = uiState,
                onTrackClick = onTrackClick,
                onRestartCurrentTrack = onRestartCurrentTrack,
                currentTrackId = currentTrackId,
                onFolderClick = onFolderClick,
                onBackFromFolder = onBackFromFolder,
                onOpenSettings = onOpenSettings,
                onShowAllSongs = onShowAllSongs,
                onShowRootFolders = onShowRootFolders,
                onSortOptionChange = onSortOptionChange,
                onToggleSortOrder = onToggleSortOrder,
                hasMiniPlayer = hasMiniPlayer,
                onBrowseModeChange = onBrowseModeChange,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onOpenGenre = onOpenGenre,
                onBackFromCollection = onBackFromCollection,
                modifier = modifier,
            )

        is LibraryUiState.Error ->
            ErrorContent(
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState.Content,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
    onFolderClick: (String) -> Unit,
    onBackFromFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowAllSongs: () -> Unit,
    onShowRootFolders: () -> Unit,
    onSortOptionChange: (LibrarySortOption) -> Unit,
    onToggleSortOrder: () -> Unit,
    hasMiniPlayer: Boolean,
    onBrowseModeChange: (LibraryBrowseMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenGenre: (String) -> Unit,
    onBackFromCollection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSortSheetVisible by remember {
        mutableStateOf(false)
    }

    val allSongsLabel =
        stringResource(
            R.string.library_all_songs,
        )

    val artistsLabel =
        stringResource(
            R.string.library_artist,
        )

    val albumsLabel =
        stringResource(
            R.string.library_album,
        )

    val genresLabel =
        stringResource(
            R.string.library_genre,
        )

    val foldersLabel =
        stringResource(
            R.string.library_folders,
        )

    val bottomContentPadding =
        if (hasMiniPlayer) {
            200.dp
        } else {
            100.dp
        }

    val libraryButtonGroupColorScheme =
        MaterialTheme
            .colorScheme
            .copy(
                surfaceContainer =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHigh,
            )

    val motionScheme =
        MaterialTheme.motionScheme

    val sortedTracks =
        uiState.tracks.sortedForLibrary(
            option = uiState.sortOption,
            order = uiState.sortOrder,
        )

    val sortedFolderTracks =
        uiState.folderTracks.sortedForLibrary(
            option = uiState.sortOption,
            order = uiState.sortOrder,
        )

    val artists =
        remember(uiState.tracks) {
            buildArtistCollections(
                tracks = uiState.tracks,
            )
        }

    val albums =
        remember(uiState.tracks) {
            buildAlbumCollections(
                tracks = uiState.tracks,
            )
        }

    val genres =
        remember(uiState.tracks) {
            buildGenreCollections(
                tracks = uiState.tracks,
            )
        }

    val selectedCollectionTracks =
        remember(
            uiState.tracks,
            uiState.selectedCollection,
            uiState.sortOption,
            uiState.sortOrder,
        ) {
            uiState.tracks
                .filter { track ->
                    matchesCollection(
                        track = track,
                        selection = uiState.selectedCollection,
                    )
                }
                .sortedForLibrary(
                    option = uiState.sortOption,
                    order = uiState.sortOrder,
                )
        }

    val screenTitle =
        when (val selection = uiState.selectedCollection) {
            is LibraryCollectionSelection.Artist ->
                selection.name

            is LibraryCollectionSelection.Album ->
                selection.title

            is LibraryCollectionSelection.Genre ->
                selection.name

            null ->
                stringResource(
                    R.string.library_title,
                )
        }

    val showSortControl =
        uiState.selectedCollection != null ||
            uiState.browseMode == LibraryBrowseMode.ALL_SONGS ||
            uiState.browseMode == LibraryBrowseMode.FOLDER

    fun selectBrowseMode(
        mode: LibraryBrowseMode,
    ) {
        when (mode) {
            LibraryBrowseMode.ALL_SONGS ->
                onShowAllSongs()

            LibraryBrowseMode.FOLDER ->
                onShowRootFolders()

            LibraryBrowseMode.ARTIST,
            LibraryBrowseMode.ALBUM,
            LibraryBrowseMode.GENRE,
            ->
                onBrowseModeChange(mode)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                ),
            title = {
                Text(
                    text = screenTitle,
                )
            },
            navigationIcon = {
                AnimatedVisibility(
                    visible =
                        uiState.currentFolderPath != null ||
                            uiState.selectedCollection != null,
                    enter =
                        expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec =
                                motionScheme
                                    .defaultSpatialSpec(),
                        ) +
                            fadeIn(
                                animationSpec =
                                    motionScheme
                                        .defaultEffectsSpec(),
                            ),
                    exit =
                        shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec =
                                motionScheme
                                    .defaultSpatialSpec(),
                        ) +
                            fadeOut(
                                animationSpec =
                                    motionScheme
                                        .defaultEffectsSpec(),
                            ),
                ) {
                    FilledIconButton(
                        onClick = {
                            if (
                                uiState.selectedCollection != null
                            ) {
                                onBackFromCollection()
                            } else {
                                onBackFromFolder()
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults
                                .filledIconButtonColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceContainerHigh,
                                    contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                ),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    CoreUiR.drawable.ic_arrow_back,
                                ),
                            contentDescription =
                                stringResource(
                                    R.string.library_back,
                                ),
                        )
                    }
                }
            },
            actions = {
                FilledIconButton(
                    onClick = onOpenSettings,
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceContainerHigh,
                                    contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface,
                                ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_settings,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.action_settings,
                            ),
                    )
                }
            },
        )

        /*
         * Library browsing modes:
         * All Songs, Artist, Album, Genre, and Folder.
         */
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        start = 12.dp,
                        top = 6.dp,
                        end = 12.dp,
                        bottom = 6.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialTheme(
                colorScheme =
                    libraryButtonGroupColorScheme,
            ) {
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults
                            .OverflowIndicator(
                                menuState = menuState,
                            )
                    },
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                ) {
                    toggleableItem(
                        checked =
                            uiState.browseMode ==
                                LibraryBrowseMode.ALL_SONGS,
                        label = allSongsLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectBrowseMode(
                                    LibraryBrowseMode.ALL_SONGS,
                                )
                            }
                        },
                    )

                    toggleableItem(
                        checked =
                            uiState.browseMode ==
                                LibraryBrowseMode.ARTIST,
                        label = artistsLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectBrowseMode(
                                    LibraryBrowseMode.ARTIST,
                                )
                            }
                        },
                    )

                    toggleableItem(
                        checked =
                            uiState.browseMode ==
                                LibraryBrowseMode.ALBUM,
                        label = albumsLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectBrowseMode(
                                    LibraryBrowseMode.ALBUM,
                                )
                            }
                        },
                    )

                    toggleableItem(
                        checked =
                            uiState.browseMode ==
                                LibraryBrowseMode.GENRE,
                        label = genresLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectBrowseMode(
                                    LibraryBrowseMode.GENRE,
                                )
                            }
                        },
                    )

                    toggleableItem(
                        checked =
                            uiState.browseMode ==
                                LibraryBrowseMode.FOLDER,
                        label = foldersLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectBrowseMode(
                                    LibraryBrowseMode.FOLDER,
                                )
                            }
                        },
                    )
                }
            }
        }

        /*
         * Folder breadcrumb and sorting controls.
         */
        if (
            uiState.currentFolderPath != null ||
                showSortControl
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                        )
                        .padding(
                            bottom = 8.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                if (uiState.currentFolderPath != null) {
                    FolderBreadcrumb(
                        modifier =
                            Modifier.weight(1f),
                        relativePath =
                            uiState.currentFolderPath,
                        onFolderClick =
                            onFolderClick,
                    )
                } else {
                    Spacer(
                        modifier =
                            Modifier.weight(1f),
                    )
                }

                if (showSortControl) {
                    SortControlRow(
                        onOpenSortSheet = {
                            isSortSheetVisible = true
                        },
                    )
                }
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    bottom = bottomContentPadding,
                ),
        ) {
            when {
                uiState.selectedCollection != null -> {
                    if (selectedCollectionTracks.isEmpty()) {
                        item {
                            EmptyContent()
                        }
                    } else {
                        allSongsContent(
                            tracks = selectedCollectionTracks,
                            onTrackClick = onTrackClick,
                            onRestartCurrentTrack =
                                onRestartCurrentTrack,
                            currentTrackId = currentTrackId,
                        )
                    }
                }

                uiState.browseMode ==
                    LibraryBrowseMode.ALL_SONGS -> {
                    if (sortedTracks.isEmpty()) {
                        item {
                            EmptyContent()
                        }
                    } else {
                        allSongsContent(
                            tracks = sortedTracks,
                            onTrackClick = onTrackClick,
                            onRestartCurrentTrack =
                                onRestartCurrentTrack,
                            currentTrackId = currentTrackId,
                        )
                    }
                }

                uiState.browseMode ==
                    LibraryBrowseMode.ARTIST -> {
                    artistCollectionContent(
                        artists = artists,
                        onArtistClick = onOpenArtist,
                    )
                }

                uiState.browseMode ==
                    LibraryBrowseMode.ALBUM -> {
                    albumCollectionContent(
                        albums = albums,
                        onAlbumClick = onOpenAlbum,
                    )
                }

                uiState.browseMode ==
                    LibraryBrowseMode.GENRE -> {
                    genreCollectionContent(
                        genres = genres,
                        onGenreClick = onOpenGenre,
                    )
                }

                uiState.browseMode ==
                    LibraryBrowseMode.FOLDER -> {
                    if (uiState.currentFolderPath == null) {
                        rootFolderContent(
                            folders = uiState.folders,
                            onFolderClick = onFolderClick,
                        )
                    } else {
                        folderContent(
                            folders = uiState.folders,
                            tracks = sortedFolderTracks,
                            onFolderClick = onFolderClick,
                            onTrackClick = onTrackClick,
                            onRestartCurrentTrack =
                                onRestartCurrentTrack,
                            currentTrackId = currentTrackId,
                        )
                    }
                }
            }
        }
    }

    if (isSortSheetVisible) {
        LibrarySortBottomSheet(
            selectedOption = uiState.sortOption,
            sortOrder = uiState.sortOrder,
            onDismissRequest = {
                isSortSheetVisible = false
            },
            onSortOptionChange = { option ->
                onSortOptionChange(option)
                isSortSheetVisible = false
            },
            onToggleSortOrder = onToggleSortOrder,
        )
    }
}

private data class AlbumCollection(
    val title: String,
    val artist: String?,
)

private data class AlbumCollectionKey(
    val normalizedTitle: String,
    val normalizedArtist: String?,
)

private fun buildArtistCollections(
    tracks: List<Track>,
): List<String> {
    return tracks
        .mapNotNull { track ->
            artistDisplayName(track)
        }
        .distinctBy { name ->
            normalizeMetadataName(name)
        }
        .sortedBy { name ->
            normalizeMetadataName(name)
        }
}

private fun buildAlbumCollections(
    tracks: List<Track>,
): List<AlbumCollection> {
    val albums =
        tracks.mapNotNull { track ->
            val title =
                cleanMetadata(
                    track.album,
                ) ?: return@mapNotNull null

            AlbumCollection(
                title = title,
                artist =
                    albumArtistDisplayName(
                        track,
                    ),
            )
        }

    return albums
        .groupBy { album ->
            AlbumCollectionKey(
                normalizedTitle =
                    normalizeMetadataName(
                        album.title,
                    ),
                normalizedArtist =
                    album.artist?.let(
                        ::normalizeMetadataName,
                    ),
            )
        }
        .values
        .map { albumEntries ->
            albumEntries.first()
        }
        .sortedWith(
            compareBy<AlbumCollection> {
                normalizeMetadataName(
                    it.title,
                )
            }.thenBy {
                it.artist?.let(
                    ::normalizeMetadataName,
                ).orEmpty()
            },
        )
}

private fun buildGenreCollections(
    tracks: List<Track>,
): List<String> {
    return tracks
        .mapNotNull { track ->
            cleanMetadata(
                track.genre,
            )
        }
        .distinctBy { genre ->
            normalizeMetadataName(genre)
        }
        .sortedBy { genre ->
            normalizeMetadataName(genre)
        }
}

private fun artistDisplayName(
    track: Track,
): String? {
    return cleanMetadata(track.artist)
        ?: cleanMetadata(track.albumArtist)
}

private fun albumArtistDisplayName(
    track: Track,
): String? {
    return cleanMetadata(track.albumArtist)
        ?: cleanMetadata(track.artist)
}

private fun cleanMetadata(
    value: String?,
): String? {
    return value
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
}

private fun normalizeMetadataName(
    value: String,
): String {
    return value.trim().lowercase()
}

private fun sameMetadataName(
    first: String?,
    second: String?,
): Boolean {
    val cleanFirst =
        cleanMetadata(first)

    val cleanSecond =
        cleanMetadata(second)

    return when {
        cleanFirst == null &&
            cleanSecond == null -> true

        cleanFirst == null ||
            cleanSecond == null -> false

        else ->
            cleanFirst.equals(
                other = cleanSecond,
                ignoreCase = true,
            )
    }
}

private fun matchesCollection(
    track: Track,
    selection: LibraryCollectionSelection?,
): Boolean {
    return when (selection) {
        is LibraryCollectionSelection.Artist ->
            sameMetadataName(
                first =
                    artistDisplayName(track),
                second = selection.name,
            )

        is LibraryCollectionSelection.Album ->
            sameMetadataName(
                first = track.album,
                second = selection.title,
            ) &&
                sameMetadataName(
                    first =
                        albumArtistDisplayName(track),
                    second = selection.artist,
                )

        is LibraryCollectionSelection.Genre ->
            sameMetadataName(
                first = track.genre,
                second = selection.name,
            )

        null ->
            false
    }
}

private fun LazyListScope.artistCollectionContent(
    artists: List<String>,
    onArtistClick: (String) -> Unit,
) {
    if (artists.isEmpty()) {
        item {
            EmptyCollectionContent(
                text =
                    stringResource(
                        R.string.library_empty_artists,
                    ),
            )
        }
        return
    }

    items(
        items = artists,
        key = { artist ->
            "artist:${normalizeMetadataName(artist)}"
        },
    ) { artist ->
        ListItem(
            onClick = {
                onArtistClick(artist)
            },
            modifier =
                Modifier.fillMaxWidth(),
            shapes = ListItemDefaults.shapes(),
        ) {
            Text(
                text = artist,
            )
        }
    }
}

private fun LazyListScope.albumCollectionContent(
    albums: List<AlbumCollection>,
    onAlbumClick: (String, String?) -> Unit,
) {
    if (albums.isEmpty()) {
        item {
            EmptyCollectionContent(
                text =
                    stringResource(
                        R.string.library_empty_albums,
                    ),
            )
        }
        return
    }

    items(
        items = albums,
        key = { album ->
            "album:${normalizeMetadataName(album.title)}:" +
                album.artist
                    ?.let(::normalizeMetadataName)
                    .orEmpty()
        },
    ) { album ->
        ListItem(
            onClick = {
                onAlbumClick(
                    album.title,
                    album.artist,
                )
            },
            modifier =
                Modifier.fillMaxWidth(),
            shapes = ListItemDefaults.shapes(),
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = album.title,
                )

                album.artist?.let { artist ->
                    Text(
                        text = artist,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.genreCollectionContent(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
) {
    if (genres.isEmpty()) {
        item {
            EmptyCollectionContent(
                text =
                    stringResource(
                        R.string.library_empty_genres,
                    ),
            )
        }
        return
    }

    items(
        items = genres,
        key = { genre ->
            "genre:${normalizeMetadataName(genre)}"
        },
    ) { genre ->
        ListItem(
            onClick = {
                onGenreClick(genre)
            },
            modifier =
                Modifier.fillMaxWidth(),
            shapes = ListItemDefaults.shapes(),
        ) {
            Text(
                text = genre,
            )
        }
    }
}

@Composable
private fun SortControlRow(
    onOpenSortSheet: () -> Unit,
) {
    Row(
        modifier =
            Modifier.padding(
                start = 8.dp,
            ),
        horizontalArrangement =
            Arrangement.End,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onOpenSortSheet,
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults
                    .filledIconButtonColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceContainerHigh,
                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                    ),
        ) {
            Icon(
                painter =
                    painterResource(
                        CoreUiR.drawable.ic_sort,
                    ),
                contentDescription =
                    stringResource(
                        R.string.library_sort,
                    ),
            )
        }
    }
}

@Composable
private fun FolderBreadcrumb(
    modifier: Modifier = Modifier,
    relativePath: String,
    onFolderClick: (String) -> Unit,
) {
    val normalizedPath =
        relativePath.trimEnd('/')

    val segments =
        normalizedPath
            .split('/')
            .filter {
                it.isNotBlank()
            }

    if (segments.isEmpty()) {
        return
    }

    val breadcrumbColorScheme =
        MaterialTheme
            .colorScheme
            .copy(
                surfaceContainer =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHigh,
            )

    Row(
        modifier =
            modifier
                .horizontalScroll(
                    rememberScrollState(),
                )
                .padding(
                    end = 8.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        MaterialTheme(
            colorScheme =
                breadcrumbColorScheme,
        ) {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults
                        .OverflowIndicator(
                            menuState = menuState,
                        )
                },
                horizontalArrangement =
                    Arrangement.spacedBy(
                        4.dp,
                    ),
            ) {
                segments.forEachIndexed { index, segment ->
                    var currentPath = ""

                    segments
                        .take(index + 1)
                        .forEach {
                            currentPath +=
                                "$it/"
                        }

                    val isCurrentFolder =
                        index ==
                            segments.lastIndex

                    toggleableItem(
                        checked = isCurrentFolder,
                        label = segment,
                        onCheckedChange = { checked ->
                            if (
                                checked &&
                                    !isCurrentFolder
                            ) {
                                onFolderClick(
                                    currentPath,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.allSongsContent(
    tracks: List<Track>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
) {
    itemsIndexed(
        items = tracks,
        key = { _, track ->
            track.id
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

private fun LazyListScope.rootFolderContent(
    folders: List<MusicFolder>,
    onFolderClick: (String) -> Unit,
) {
    if (folders.isEmpty()) {
        item {
            EmptyFolderContent()
        }
        return
    }

    items(
        items = folders,
        key = { folder ->
            folder.relativePath
        },
    ) { folder ->
        FolderRow(
            folder = folder,
            onClick = {
                onFolderClick(
                    folder.relativePath,
                )
            },
        )
    }
}

private fun LazyListScope.folderContent(
    folders: List<MusicFolder>,
    tracks: List<Track>,
    onFolderClick: (String) -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onRestartCurrentTrack: () -> Unit,
    currentTrackId: Long?,
) {
    if (folders.isNotEmpty()) {
        items(
            items = folders,
            key = { folder ->
                folder.relativePath
            },
        ) { folder ->
            FolderRow(
                folder = folder,
                onClick = {
                    onFolderClick(
                        folder.relativePath,
                    )
                },
            )
        }
    }

    if (tracks.isNotEmpty()) {
        itemsIndexed(
            items = tracks,
            key = { _, track ->
                track.id
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

    if (
        folders.isEmpty() &&
            tracks.isEmpty()
    ) {
        item {
            EmptyFolderContent()
        }
    }
}

@Composable
private fun FolderRow(
    folder: MusicFolder,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth(),
        shapes = ListItemDefaults.shapes(),
        leadingContent = {
            Icon(
                painter =
                    painterResource(
                        CoreUiR.drawable.ic_folder,
                    ),
                contentDescription = null,
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )
        },
    ) {
        Text(
            text =
                folderDisplayName(
                    relativePath =
                        folder.relativePath,
                ),
        )
    }
}

private fun folderDisplayName(
    relativePath: String,
): String {
    return relativePath
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank {
            relativePath.trimEnd('/')
        }
}

@Composable
private fun EmptyContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 32.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    R.string.library_empty,
                ),
        )
    }
}

@Composable
private fun EmptyCollectionContent(
    text: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 32.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
        )
    }
}

@Composable
private fun EmptyFolderContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 32.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    R.string.library_empty,
                ),
        )
    }
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Text(
            text =
                stringResource(
                    R.string.library_error,
                ),
        )
    }
}
