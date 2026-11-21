package com.rovia.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.CatalogArtworkSelector
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.data.repository.DatabaseSearchRepository
import com.rovia.music.data.repository.SearchAlbumResult
import com.rovia.music.data.repository.SearchArtistResult
import com.rovia.music.data.repository.SearchGenreResult
import com.rovia.music.data.repository.SearchPlaylistResult
import com.rovia.music.data.repository.DatabaseSearchResults
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.PlaylistArtwork
import com.rovia.music.data.repository.UserLibraryRepository
import com.rovia.music.ui.components.TrackInfoSheetHost
import com.rovia.music.ui.components.TrackListItem
import com.rovia.music.ui.components.UserLibraryMenuDialogHost
import com.rovia.music.ui.components.UserLibraryMenuState
import com.rovia.music.ui.components.rememberTrackInfoState
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private val SearchNavigationBarHeight = 80.dp
private val SearchMiniPlayerHeight = 80.dp
private val SearchBarHorizontalPadding = 18.dp
private val SearchBarTopPadding = 8.dp
private val SearchResultsSpacing = 12.dp
private const val SearchDebounceMillis = 150L

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SearchScreen(
    audioRepository: MediaStoreAudioRepository,
    databaseSearchRepository: DatabaseSearchRepository,
    playbackController: PlaybackController,
    onOpenSettings: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    userLibraryMenu: UserLibraryMenuState? = null
) {
    val textFieldState = rememberTextFieldState()
    val trackInfoState = rememberTrackInfoState()
    val searchBarState = rememberSearchBarState()
    val query = textFieldState.text.toString()
    val hasQuery = query.isNotBlank()
    val clearSearchDescription = stringResource(R.string.search_clear)
    val hasAudioPermission = rememberAudioPermissionGranted()

    val context = LocalContext.current
    val historyStore = remember(context) {
        SearchHistoryStore(context.applicationContext)
    }
    val historyEntries by historyStore.entries
        .collectAsStateWithLifecycle()

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()
    val catalog by audioRepository.catalogState.collectAsStateWithLifecycle()
    val isLoading = hasAudioPermission && !catalog.isLoaded && !catalog.loadFailed
    val loadFailed = catalog.loadFailed

    var searchResults by remember {
        mutableStateOf(DatabaseSearchResults())
    }
    var isSearching by remember {
        mutableStateOf(false)
    }
    var searchFailed by remember {
        mutableStateOf(false)
    }
    var reloadRequest by remember {
        mutableIntStateOf(0)
    }
    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }

    val isSearchPending = hasQuery && (isSearching || (isLoading && searchResults.isEmpty))
    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val contentTopPadding = statusBarTopInset +
        TopAppBarDefaults.TopAppBarExpandedHeight + SearchResultsSpacing
    val contentBottomPadding = systemNavigationBarBottomInset +
        SearchNavigationBarHeight +
        (if (currentMediaId != null) SearchMiniPlayerHeight else 0.dp) +
        16.dp

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
            onDispose {
                controller.removeListener(listener)
            }
        }
    }

    LaunchedEffect(hasAudioPermission, reloadRequest) {
        if (!hasAudioPermission) {
            searchResults = DatabaseSearchResults()
            isSearching = false
            searchFailed = false
            return@LaunchedEffect
        }

        try {
            audioRepository.refresh(forceRefresh = reloadRequest > 0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            if (!catalog.isLoaded) {
                searchFailed = true
            }
        }
    }

    LaunchedEffect(databaseSearchRepository, query, hasAudioPermission, reloadRequest) {
        if (!hasAudioPermission || query.isBlank()) {
            searchResults = DatabaseSearchResults()
            isSearching = false
            searchFailed = false
            return@LaunchedEffect
        }

        searchResults = DatabaseSearchResults()
        isSearching = true
        searchFailed = false

        try {
            delay(SearchDebounceMillis)
            databaseSearchRepository.observeSearch(query).collect { results ->
                searchResults = results
                isSearching = false
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            searchFailed = true
        } finally {
            isSearching = false
        }
    }

    fun playSearchedTrack(
        track: LocalAudioTrack,
        queueTracks: List<LocalAudioTrack>
    ) {
        val controller = mediaController ?: return
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
            val startIndex = queueTracks.indexOfFirst { it.id == track.id }
            playbackController.playQueue(
                tracks = if (startIndex >= 0) queueTracks else listOf(track),
                startIndex = startIndex.takeIf { it >= 0 } ?: 0
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchResultsContent(
            results = searchResults,
            hasQuery = hasQuery,
            isSearchPending = isSearchPending,
            hasAudioPermission = hasAudioPermission,
            isLoading = isLoading,
            loadFailed = loadFailed,
            searchFailed = searchFailed,
            currentMediaId = currentMediaId,
            itemsEnabled = mediaController != null,
            modifier = Modifier
                .fillMaxSize(),
            contentInsetPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            messageTopPadding = contentTopPadding,
            messageBottomPadding = contentBottomPadding,
            onRetry = { reloadRequest += 1 },
            onTrackClick = { track ->
                historyStore.record(searchHistoryTrackEntry(track))
                playSearchedTrack(
                    track = track,
                    queueTracks = searchResults.songs.map { it.track }
                )
            },
            onArtistClick = { result ->
                historyStore.record(searchHistoryArtistEntry(result))
                onOpenArtist(result.name)
            },
            onAlbumClick = { result ->
                historyStore.record(searchHistoryAlbumEntry(result))
                onOpenAlbum(result.release.key)
            },
            onGenreClick = { result ->
                historyStore.record(searchHistoryGenreEntry(result))
                onOpenGenre(result.genreKey)
            },
            onPlaylistClick = { result ->
                historyStore.record(searchHistoryPlaylistEntry(result))
                onOpenPlaylist(result.playlistId)
            },
            historyEntries = historyEntries,
            onHistoryEntryClick = { entry ->
                historyStore.record(entry)
                when (entry.type) {
                    SearchHistoryType.TRACK -> {
                        val track = catalog.tracks.firstOrNull { candidate ->
                            candidate.id == entry.trackId &&
                                candidate.volumeName == entry.volumeName
                        }
                        if (track != null) {
                            playSearchedTrack(
                                track = track,
                                queueTracks = listOf(track)
                            )
                        }
                    }
                    SearchHistoryType.ARTIST -> onOpenArtist(entry.key)
                    SearchHistoryType.ALBUM -> onOpenAlbum(entry.key)
                    SearchHistoryType.GENRE -> onOpenGenre(entry.key)
                    SearchHistoryType.PLAYLIST -> {
                        entry.key.toLongOrNull()?.let(onOpenPlaylist)
                    }
                }
            },
            onHistoryEntryRemove = { entry -> historyStore.remove(entry) },
            onClearHistory = { historyStore.clear() },
            onTrackInfoClick = trackInfoState::show,
            likedTrackKeys = userLibraryMenu?.likedTrackKeys ?: emptySet(),
            onToggleLike = userLibraryMenu?.onToggleLike,
            onAddToPlaylistClick = userLibraryMenu?.onShowPlaylistPicker
        )

        TrackInfoSheetHost(trackInfoState)

        if (userLibraryMenu != null) {
            UserLibraryMenuDialogHost(userLibraryMenu)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = SearchBarHorizontalPadding)
                .padding(top = SearchBarTopPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBarDefaults.InputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    onSearch = {},
                    modifier = Modifier.weight(1f),
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = SearchBarDefaults.inputFieldColors(
                        focusedContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    placeholder = {
                        Text(stringResource(R.string.search_hint))
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { textFieldState.clearText() },
                                modifier = Modifier.semantics {
                                    contentDescription = clearSearchDescription
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )

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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultsContent(
    results: DatabaseSearchResults,
    hasQuery: Boolean,
    isSearchPending: Boolean,
    hasAudioPermission: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    searchFailed: Boolean,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    modifier: Modifier,
    contentInsetPadding: PaddingValues,
    messageTopPadding: Dp,
    messageBottomPadding: Dp,
    onRetry: () -> Unit,
    onTrackClick: (LocalAudioTrack) -> Unit,
    onArtistClick: (SearchArtistResult) -> Unit,
    onAlbumClick: (SearchAlbumResult) -> Unit,
    onGenreClick: (SearchGenreResult) -> Unit,
    onPlaylistClick: (SearchPlaylistResult) -> Unit,
    historyEntries: List<SearchHistoryEntry>,
    onHistoryEntryClick: (SearchHistoryEntry) -> Unit,
    onHistoryEntryRemove: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onTrackInfoClick: (LocalAudioTrack) -> Unit,
    likedTrackKeys: Set<String>,
    onToggleLike: ((LocalAudioTrack) -> Unit)?,
    onAddToPlaylistClick: ((LocalAudioTrack) -> Unit)?
) {
    when {
        !hasAudioPermission -> SearchMessage(
            message = stringResource(R.string.library_no_tracks),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            )
        )

        !hasQuery -> {
            if (historyEntries.isEmpty()) {
                Box(modifier = modifier.fillMaxSize())
            } else {
                SearchHistoryContent(
                    entries = historyEntries,
                    currentMediaId = currentMediaId,
                    itemsEnabled = itemsEnabled,
                    modifier = modifier,
                    contentInsetPadding = contentInsetPadding,
                    onEntryClick = onHistoryEntryClick,
                    onEntryRemove = onHistoryEntryRemove,
                    onClearAll = onClearHistory
                )
            }
        }

        results.isEmpty && (isLoading || isSearchPending) -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }

        results.isEmpty && (searchFailed || loadFailed) -> SearchMessage(
            message = stringResource(R.string.library_load_error),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            ),
            actionLabel = stringResource(R.string.library_retry),
            onAction = onRetry
        )

        results.isEmpty -> SearchMessage(
            message = stringResource(R.string.search_no_results),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            )
        )

        else -> {
            val rowColors = ListItemDefaults.segmentedColors(
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
                modifier = modifier.padding(top = contentInsetPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = contentInsetPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (results.songs.isNotEmpty()) {
                    item(key = "search-section-songs") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_songs)
                        )
                    }
                    itemsIndexed(
                        items = results.songs,
                        key = { _, result -> "search-song-${result.track.id}" }
                    ) { index, result ->
                        val track = result.track
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
                            selected = currentMediaId == track.id.toString(),
                            enabled = itemsEnabled,
                            onClick = { onTrackClick(track) },
                            index = index,
                            count = results.songs.size,
                            onTrackInfoClick = { onTrackInfoClick(track) },
                            track = track,
                            isLiked = UserLibraryRepository.likedTrackKey(track) in likedTrackKeys,
                            onToggleLike = onToggleLike?.let { callback ->
                                { callback(track) }
                            },
                            onAddToPlaylist = onAddToPlaylistClick?.let { callback ->
                                { callback(track) }
                            },
                        )
                    }
                }

                if (results.artists.isNotEmpty()) {
                    item(key = "search-section-artists") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_artists)
                        )
                    }
                    itemsIndexed(
                        items = results.artists,
                        key = { _, result -> "search-artist-${result.name}" }
                    ) { index, result ->
                        val artworkFallback = result.name.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "♪"
                        val trackCountLabel = pluralStringResource(
                            R.plurals.library_track_count,
                            result.trackCount,
                            result.trackCount
                        )

                        SegmentedListItem(
                            selected = false,
                            onClick = { onArtistClick(result) },
                            modifier = Modifier.padding(horizontal = 18.dp),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = results.artists.size
                            ),
                            colors = rowColors,
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            leadingContent = {
                                AlbumArtwork(
                                    artworkUri = result.artworkUri,
                                    fallbackText = artworkFallback,
                                    contentDescription = result.name,
                                    modifier = Modifier.size(56.dp),
                                    size = 56.dp,
                                    shape = CircleShape
                                )
                            },
                            content = {
                                Text(
                                    text = result.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = trackCountLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item(key = "search-section-albums") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_albums)
                        )
                    }
                    itemsIndexed(
                        items = results.albums,
                        key = { _, result -> "search-album-${result.release.key}" }
                    ) { index, result ->
                        val release = result.release
                        val releaseArtist = release.albumArtist
                            ?.takeIf(String::isNotBlank)
                            ?: release.artists.joinToString(", ")
                                .takeIf(String::isNotBlank)
                            ?: stringResource(R.string.player_unknown_artist)
                        val releaseTypeLabel = stringResource(
                            when (release.type) {
                                AlbumReleaseType.SINGLE -> R.string.artist_release_type_single
                                AlbumReleaseType.EP -> R.string.artist_release_type_ep
                                AlbumReleaseType.ALBUM -> R.string.artist_release_type_album
                                AlbumReleaseType.UNKNOWN -> R.string.artist_release_type_unknown
                            }
                        )
                        val releaseYear = remember(release) {
                            CatalogArtworkSelector.releaseYearForDisplay(release.tracks)
                        }
                        val trackCountLabel = pluralStringResource(
                            R.plurals.library_track_count,
                            release.tracks.size,
                            release.tracks.size
                        )
                        val supportingText = buildList {
                            add(releaseArtist)
                            add(releaseTypeLabel)
                            releaseYear?.let { add(it.toString()) }
                            add(trackCountLabel)
                        }.joinToString(" • ")
                        val artworkFallback = release.title.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "♪"

                        SegmentedListItem(
                            selected = false,
                            onClick = { onAlbumClick(result) },
                            modifier = Modifier.padding(horizontal = 18.dp),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = results.albums.size
                            ),
                            colors = rowColors,
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            leadingContent = {
                                AlbumArtwork(
                                    artworkUri = release.artworkUri,
                                    fallbackText = artworkFallback,
                                    contentDescription = release.title,
                                    modifier = Modifier.size(56.dp),
                                    size = 56.dp,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            },
                            content = {
                                Text(
                                    text = release.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = supportingText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                if (results.genres.isNotEmpty()) {
                    item(key = "search-section-genres") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_genres)
                        )
                    }
                    itemsIndexed(
                        items = results.genres,
                        key = { _, result -> "search-genre-${result.genreKey}" }
                    ) { index, result ->
                        val artworkFallback = result.name.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "♪"
                        val trackCountLabel = pluralStringResource(
                            R.plurals.library_track_count,
                            result.trackCount,
                            result.trackCount
                        )

                        SegmentedListItem(
                            selected = false,
                            onClick = { onGenreClick(result) },
                            modifier = Modifier.padding(horizontal = 18.dp),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = results.genres.size
                            ),
                            colors = rowColors,
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            leadingContent = {
                                AlbumArtwork(
                                    artworkUri = result.artworkUri,
                                    fallbackText = artworkFallback,
                                    contentDescription = result.name,
                                    modifier = Modifier.size(56.dp),
                                    size = 56.dp,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            },
                            content = {
                                Text(
                                    text = result.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = trackCountLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                if (results.playlists.isNotEmpty()) {
                    item(key = "search-section-playlists") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_playlists)
                        )
                    }
                    itemsIndexed(
                        items = results.playlists,
                        key = { _, result -> "search-playlist-${result.playlistId}" }
                    ) { _, result ->
                        val matchingTrackTitle = result.matchingTrackTitle
                            ?.takeIf(String::isNotBlank)
                        val matchingTrackArtist = result.matchingTrackArtist
                            ?.takeIf(String::isNotBlank)
                        val trackLabel = when {
                            matchingTrackTitle != null && matchingTrackArtist != null ->
                                stringResource(
                                    R.string.search_playlist_matching_track,
                                    matchingTrackTitle,
                                    matchingTrackArtist
                                )
                            matchingTrackTitle != null ->
                                stringResource(
                                    R.string.search_playlist_matching_track_title,
                                    matchingTrackTitle
                                )
                            else -> null
                        }
                        val playlistTrackCount = result.trackCount
                            .coerceIn(0L, Int.MAX_VALUE.toLong())
                            .toInt()
                        val playlistCountLabel = pluralStringResource(
                            R.plurals.library_track_count,
                            playlistTrackCount,
                            playlistTrackCount
                        )
                        val supportingText = buildList {
                            trackLabel?.let(::add)
                            result.description?.takeIf(String::isNotBlank)?.let(::add)
                            if (isEmpty()) add(playlistCountLabel)
                        }.joinToString(" • ")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { onPlaylistClick(result) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistArtwork(
                                name = result.name,
                                modifier = Modifier.size(56.dp),
                                size = 56.dp,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = supportingText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (results.likedTracks.isNotEmpty()) {
                    item(key = "search-section-likes") {
                        SearchSectionTitle(
                            title = stringResource(R.string.search_section_likes)
                        )
                    }
                    itemsIndexed(
                        items = results.likedTracks,
                        key = { _, result -> "search-liked-${result.likedTrackId}" }
                    ) { index, result ->
                        val track = result.track
                        val title = result.title.trim()
                            .ifBlank { stringResource(R.string.player_unknown_track) }
                        val supportingText = result.artist?.trim()
                            ?.takeIf(String::isNotBlank)
                            ?: result.album?.trim()?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.player_unknown_artist)

                        TrackListItem(
                            title = title,
                            supportingText = supportingText,
                            artworkUri = result.artworkUri ?: track?.uri?.toString(),
                            durationMs = track?.durationMs,
                            selected = track != null && currentMediaId == track.id.toString(),
                            enabled = itemsEnabled && track != null,
                            onClick = { track?.let(onTrackClick) },
                            index = index,
                            count = results.likedTracks.size,
                            onTrackInfoClick = track?.let { resolved ->
                                { onTrackInfoClick(resolved) }
                            },
                            track = track,
                            isLiked = track != null &&
                                UserLibraryRepository.likedTrackKey(track) in likedTrackKeys,
                            onToggleLike = if (track != null) {
                                onToggleLike?.let { callback ->
                                    { callback(track) }
                                }
                            } else {
                                null
                            },
                            onAddToPlaylist = if (track != null) {
                                onAddToPlaylistClick?.let { callback ->
                                    { callback(track) }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchHistoryContent(
    entries: List<SearchHistoryEntry>,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    modifier: Modifier,
    contentInsetPadding: PaddingValues,
    onEntryClick: (SearchHistoryEntry) -> Unit,
    onEntryRemove: (SearchHistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    val rowColors = ListItemDefaults.segmentedColors(
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
        modifier = modifier.padding(top = contentInsetPadding.calculateTopPadding()),
        contentPadding = PaddingValues(bottom = contentInsetPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item(key = "search-history-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_history_title),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(text = stringResource(R.string.search_history_clear_all))
                }
            }
        }
        itemsIndexed(
            items = entries,
            key = { _, entry -> "search-history-${entry.type}-${entry.key}" }
        ) { index, entry ->
            val artworkFallback = entry.title.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "♪"
            val supportingText = when (entry.type) {
                SearchHistoryType.TRACK,
                SearchHistoryType.ALBUM -> entry.subtitle
                    ?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.player_unknown_artist)

                else -> if (entry.trackCount >= 0) {
                    pluralStringResource(
                        R.plurals.library_track_count,
                        entry.trackCount,
                        entry.trackCount
                    )
                } else {
                    null
                }
            }
            val title = entry.title.ifBlank {
                stringResource(R.string.player_unknown_track)
            }

            SegmentedListItem(
                selected = entry.type == SearchHistoryType.TRACK &&
                    currentMediaId == entry.trackId.toString(),
                onClick = { onEntryClick(entry) },
                enabled = entry.type != SearchHistoryType.TRACK || itemsEnabled,
                modifier = Modifier.padding(horizontal = 18.dp),
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = entries.size
                ),
                colors = rowColors,
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                leadingContent = {
                    when (entry.type) {
                        SearchHistoryType.PLAYLIST -> PlaylistArtwork(
                            name = entry.title,
                            modifier = Modifier.size(56.dp),
                            size = 56.dp,
                            shape = RoundedCornerShape(14.dp)
                        )

                        SearchHistoryType.ARTIST -> AlbumArtwork(
                            artworkUri = entry.artworkUri,
                            fallbackText = artworkFallback,
                            contentDescription = entry.title,
                            modifier = Modifier.size(56.dp),
                            size = 56.dp,
                            shape = CircleShape
                        )

                        else -> AlbumArtwork(
                            artworkUri = entry.artworkUri,
                            fallbackText = artworkFallback,
                            contentDescription = entry.title,
                            modifier = Modifier.size(56.dp),
                            size = 56.dp,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                },
                content = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    if (supportingText != null) {
                        Text(
                            text = supportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onEntryRemove(entry) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(
                                R.string.search_history_remove
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SearchMessage(
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
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
