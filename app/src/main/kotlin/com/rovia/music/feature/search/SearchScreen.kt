package com.rovia.music.feature.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.fadeScrimProgressiveBlur
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private val SearchNavigationBarHeight = 80.dp
private val SearchMiniPlayerHeight = 80.dp
private val SearchBarHorizontalPadding = 18.dp
private val SearchBarTopPadding = 8.dp
private val SearchResultsSpacing = 12.dp
private const val SearchDebounceMillis = 150L

private object SearchIndexCache {

    private data class Entry(
        val revision: Long,
        val index: SearchIndex
    )

    private val entries = WeakHashMap<MediaStoreAudioRepository, Entry>()

    @Synchronized
    fun get(
        repository: MediaStoreAudioRepository,
        revision: Long
    ): SearchIndex? = entries[repository]
        ?.takeIf { it.revision == revision }
        ?.index

    @Synchronized
    fun put(
        repository: MediaStoreAudioRepository,
        revision: Long,
        index: SearchIndex
    ) {
        entries[repository] = Entry(
            revision = revision,
            index = index
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SearchScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
    onOpenSettings: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val query = textFieldState.text.toString()
    val hasQuery = query.isNotBlank()
    val clearSearchDescription = stringResource(R.string.search_clear)
    val hasAudioPermission = rememberAudioPermissionGranted()

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()
    val catalog by audioRepository.catalogState.collectAsStateWithLifecycle()
    val tracks = catalog.tracks
    val isLoading = hasAudioPermission && !catalog.isLoaded && !catalog.loadFailed
    val loadFailed = catalog.loadFailed

    var searchIndex by remember {
        mutableStateOf<SearchIndex?>(null)
    }
    var searchResults by remember {
        mutableStateOf(SearchResults())
    }
    var isIndexing by remember {
        mutableStateOf(false)
    }
    var isFiltering by remember {
        mutableStateOf(false)
    }
    var reloadRequest by remember {
        mutableIntStateOf(0)
    }
    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }

    val isSearchPending = hasQuery && (isIndexing || isFiltering)
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
            searchIndex = null
            searchResults = SearchResults()
            isIndexing = false
            isFiltering = false
            return@LaunchedEffect
        }

        try {
            audioRepository.refresh(forceRefresh = reloadRequest > 0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            searchResults = SearchResults()
        }
    }

    LaunchedEffect(
        audioRepository,
        hasAudioPermission,
        catalog.isLoaded,
        catalog.revision
    ) {
        if (!hasAudioPermission || !catalog.isLoaded) {
            searchIndex = null
            searchResults = SearchResults()
            isIndexing = false
            return@LaunchedEffect
        }

        val cachedIndex = SearchIndexCache.get(
            repository = audioRepository,
            revision = catalog.revision
        )
        if (cachedIndex != null) {
            searchIndex = cachedIndex
            isIndexing = false
            return@LaunchedEffect
        }

        searchIndex = null
        searchResults = SearchResults()
        isIndexing = true

        try {
            val builtIndex = withContext(Dispatchers.Default) {
                SearchEngine.buildIndex(tracks)
            }
            coroutineContext.ensureActive()
            SearchIndexCache.put(
                repository = audioRepository,
                revision = catalog.revision,
                index = builtIndex
            )
            searchIndex = builtIndex
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            searchIndex = null
        } finally {
            isIndexing = false
        }
    }

    LaunchedEffect(query, searchIndex) {
        val activeIndex = searchIndex
        if (query.isBlank() || activeIndex == null) {
            searchResults = SearchResults()
            isFiltering = false
            return@LaunchedEffect
        }

        searchResults = SearchResults()
        isFiltering = true
        try {
            delay(SearchDebounceMillis)
            val results = withContext(Dispatchers.Default) {
                SearchEngine.search(
                    index = activeIndex,
                    query = query
                )
            }
            coroutineContext.ensureActive()
            searchResults = results
        } catch (exception: CancellationException) {
            throw exception
        } finally {
            isFiltering = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchResultsContent(
            tracks = tracks,
            results = searchResults,
            hasQuery = hasQuery,
            isSearchPending = isSearchPending,
            hasAudioPermission = hasAudioPermission,
            isLoading = isLoading,
            loadFailed = loadFailed,
            currentMediaId = currentMediaId,
            itemsEnabled = mediaController != null,
            modifier = Modifier
                .fillMaxSize()
                .fadeScrimProgressiveBlur(withMiniPlayer = currentMediaId != null),
            contentInsetPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            messageTopPadding = contentTopPadding,
            messageBottomPadding = contentBottomPadding,
            onRetry = { reloadRequest += 1 },
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
                        val queueTracks = searchResults.songs.map { it.track }
                        if (index in queueTracks.indices) {
                            playbackController.playQueue(
                                tracks = queueTracks,
                                startIndex = index
                            )
                        }
                    }
                }
            },
            onArtistClick = onOpenArtist,
            onAlbumClick = onOpenAlbum
        )

        FadeScrim(
            edge = FadeScrimEdge.Top,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        FadeScrim(
            edge = FadeScrimEdge.Bottom,
            modifier = Modifier.align(Alignment.BottomCenter),
            withMiniPlayer = currentMediaId != null
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
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
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.titleLarge
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
    tracks: List<LocalAudioTrack>,
    results: SearchResults,
    hasQuery: Boolean,
    isSearchPending: Boolean,
    hasAudioPermission: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    modifier: Modifier,
    contentInsetPadding: PaddingValues,
    messageTopPadding: Dp,
    messageBottomPadding: Dp,
    onRetry: () -> Unit,
    onTrackClick: (LocalAudioTrack, Int) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    when {
        !hasAudioPermission -> SearchMessage(
            message = stringResource(R.string.library_no_tracks),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            )
        )

        isLoading -> Box(
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        loadFailed -> SearchMessage(
            message = stringResource(R.string.library_load_error),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            ),
            actionLabel = stringResource(R.string.library_retry),
            onAction = onRetry
        )

        tracks.isEmpty() -> SearchMessage(
            message = stringResource(R.string.library_no_tracks),
            modifier = modifier.fillMaxSize().padding(
                top = messageTopPadding,
                bottom = messageBottomPadding
            )
        )

        !hasQuery -> Box(modifier = modifier.fillMaxSize())

        isSearchPending && results.songs.isEmpty() &&
            results.artists.isEmpty() && results.albums.isEmpty() -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        results.songs.isEmpty() && results.artists.isEmpty() &&
            results.albums.isEmpty() -> SearchMessage(
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
                modifier = modifier,
                contentPadding = contentInsetPadding,
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
                        val artworkFallback = title.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "♪"
                        val totalSeconds = track.durationMs.coerceAtLeast(0L) / 1000L

                        SegmentedListItem(
                            selected = currentMediaId == track.id.toString(),
                            onClick = { onTrackClick(track, index) },
                            enabled = itemsEnabled,
                            modifier = Modifier.padding(horizontal = 18.dp),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = results.songs.size
                            ),
                            colors = rowColors,
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            leadingContent = {
                                AlbumArtwork(
                                    artworkUri = track.uri.toString(),
                                    fallbackText = artworkFallback,
                                    contentDescription = title,
                                    modifier = Modifier.size(56.dp),
                                    size = 56.dp,
                                    shape = MaterialTheme.shapes.medium
                                )
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
                                Text(
                                    text = supportingText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = String.format(
                                        Locale.getDefault(),
                                        "%d:%02d",
                                        totalSeconds / 60L,
                                        totalSeconds % 60L
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
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
                            onClick = { onArtistClick(result.name) },
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
                            release.tracks.mapNotNull(LocalAudioTrack::releaseYear)
                                .groupingBy { it }
                                .eachCount()
                                .maxByOrNull { it.value }
                                ?.key
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
                            onClick = { onAlbumClick(release.key) },
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
            }
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
