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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
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
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember(uiState.currentFolderPath) {
        mutableIntStateOf(
            if (uiState.currentFolderPath != null) {
                1
            } else {
                0
            },
        )
    }

    var isSortSheetVisible by remember {
        mutableStateOf(false)
    }

    val allSongsLabel =
        stringResource(
            R.string.library_all_songs,
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
                    text =
                        stringResource(
                            R.string.library_title,
                        ),
                )
            },
            navigationIcon = {
                AnimatedVisibility(
                    visible =
                        uiState.currentFolderPath != null,
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
                        onClick = onBackFromFolder,
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
                        checked = selectedMode == 0,
                        label = allSongsLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedMode = 0
                                onShowAllSongs()
                            }
                        },
                    )

                    toggleableItem(
                        checked = selectedMode == 1,
                        label = foldersLabel,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedMode = 1
                                onShowRootFolders()
                            }
                        },
                    )
                }
            }
        }

        if (uiState.currentFolderPath != null) {
            FolderBreadcrumb(
                relativePath = uiState.currentFolderPath,
                onFolderClick = onFolderClick,
            )
        }

        SortControlRow(
            onOpenSortSheet = {
                isSortSheetVisible = true
            },
        )

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
            if (selectedMode == 0) {
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
            } else {
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

@Composable
private fun SortControlRow(
    onOpenSortSheet: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    top = 0.dp,
                    end = 12.dp,
                    bottom = 6.dp,
                ),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
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
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState(),
                )
                .padding(
                    start = 12.dp,
                    top = 0.dp,
                    end = 12.dp,
                    bottom = 8.dp,
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