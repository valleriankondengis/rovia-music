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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import java.text.Normalizer
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
private val SearchBarHorizontalPadding = 10.dp
private val SearchBarTopPadding = 8.dp
private val SearchResultsSpacing = 12.dp
private const val SearchDebounceMillis = 150L
private const val SearchCancellationCheckInterval = 64

private val SearchWordSeparator = Regex("[^\\p{L}\\p{N}]+")
private val SearchWhitespace = Regex("\\s+")
private val SearchDiacritics = Regex("\\p{M}+")

private data class IndexedTrack(
    val track: LocalAudioTrack,
    val searchableText: String,
    val words: List<String>
)

/** Keeps expensive text normalization/indexing reusable when SearchScreen is recreated. */
private object SearchIndexCache {
    private data class Entry(
        val revision: Long,
        val index: List<IndexedTrack>,
    )

    private val entries = WeakHashMap<MediaStoreAudioRepository, Entry>()

    @Synchronized
    fun get(repository: MediaStoreAudioRepository, revision: Long): List<IndexedTrack>? =
        entries[repository]?.takeIf { it.revision == revision }?.index

    @Synchronized
    fun put(
        repository: MediaStoreAudioRepository,
        revision: Long,
        index: List<IndexedTrack>,
    ) {
        entries[repository] = Entry(revision, index)
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
    onOpenSettings: () -> Unit
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
        mutableStateOf<List<IndexedTrack>>(emptyList())
    }

    var matchingTracks by remember {
        mutableStateOf<List<LocalAudioTrack>>(emptyList())
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
        mutableStateOf(
            mediaController?.currentMediaItem?.mediaId
        )
    }

    val isSearchPending =
        hasQuery && (isIndexing || isFiltering)

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val systemNavigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            SearchResultsSpacing

    val contentBottomPadding =
        systemNavigationBarBottomInset +
            SearchNavigationBarHeight +
            if (currentMediaId != null) {
                SearchMiniPlayerHeight
            } else {
                0.dp
            } +
            16.dp

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
        if (!hasAudioPermission) {
            searchIndex = emptyList()
            matchingTracks = emptyList()
            isIndexing = false
            isFiltering = false
            return@LaunchedEffect
        }

        try {
            audioRepository.refresh(forceRefresh = reloadRequest > 0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // CatalogState preserves the last successful result if a refresh fails.
        }
    }

    LaunchedEffect(
        audioRepository,
        hasAudioPermission,
        catalog.isLoaded,
        catalog.revision,
    ) {
        if (!hasAudioPermission || !catalog.isLoaded) {
            searchIndex = emptyList()
            matchingTracks = emptyList()
            isIndexing = false
            return@LaunchedEffect
        }

        val cachedIndex = SearchIndexCache.get(audioRepository, catalog.revision)
        if (cachedIndex != null) {
            searchIndex = cachedIndex
            isIndexing = false
            return@LaunchedEffect
        }

        matchingTracks = emptyList()
        isIndexing = true
        try {
            val indexedTracks = withContext(Dispatchers.Default) {
                val activeContext = coroutineContext
                val result = ArrayList<IndexedTrack>(tracks.size)

                tracks.forEachIndexed { index, track ->
                    if (index % SearchCancellationCheckInterval == 0) {
                        activeContext.ensureActive()
                    }

                    val searchableText = listOfNotNull(
                        track.title,
                        track.artist,
                        track.album,
                    )
                        .map(::normalizeSearchText)
                        .filter(String::isNotBlank)
                        .joinToString(" ")

                    result.add(
                        IndexedTrack(
                            track = track,
                            searchableText = searchableText,
                            words = searchableText
                                .split(SearchWordSeparator)
                                .filter(String::isNotBlank)
                                .distinct(),
                        ),
                    )
                }

                result
            }

            coroutineContext.ensureActive()
            SearchIndexCache.put(audioRepository, catalog.revision, indexedTracks)
            searchIndex = indexedTracks
        } finally {
            isIndexing = false
        }
    }

    LaunchedEffect(query, searchIndex) {
        if (query.isBlank()) {
            matchingTracks = emptyList()
            isFiltering = false
            return@LaunchedEffect
        }

        matchingTracks = emptyList()
        isFiltering = true

        try {
            delay(SearchDebounceMillis)

            val normalizedQuery = normalizeSearchText(query)

            if (normalizedQuery.isBlank()) {
                matchingTracks = emptyList()
                return@LaunchedEffect
            }

            val queryTerms = normalizedQuery
                .split(SearchWhitespace)
                .filter(String::isNotBlank)
                .distinct()

            val results = withContext(Dispatchers.Default) {
                val activeContext = coroutineContext
                val result = ArrayList<LocalAudioTrack>()

                searchIndex.forEachIndexed { index, indexedTrack ->
                    if (index % SearchCancellationCheckInterval == 0) {
                        activeContext.ensureActive()
                    }

                    if (
                        matchesIndexedTrack(
                            indexedTrack = indexedTrack,
                            normalizedQuery = normalizedQuery,
                            queryTerms = queryTerms
                        )
                    ) {
                        result.add(indexedTrack.track)
                    }
                }

                result
            }

            matchingTracks = results
        } finally {
            isFiltering = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SearchResultsContent(
            tracks = tracks,
            matchingTracks = matchingTracks,
            hasQuery = hasQuery,
            isSearchPending = isSearchPending,
            hasAudioPermission = hasAudioPermission,
            isLoading = isLoading,
            loadFailed = loadFailed,
            currentMediaId = currentMediaId,
            itemsEnabled = mediaController != null,
            modifier = Modifier.fillMaxSize(),
            contentInsetPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            ),
            messageTopPadding = contentTopPadding,
            messageBottomPadding = contentBottomPadding,
            onRetry = {
                reloadRequest += 1
            },
            onTrackClick = { track, _ ->
                val controller = mediaController

                if (controller != null) {
                    if (currentMediaId == track.id.toString()) {
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
                        val selectedIndex = matchingTracks.indexOf(track)

                        if (selectedIndex >= 0) {
                            playbackController.playQueue(
                                tracks = matchingTracks,
                                startIndex = selectedIndex
                            )
                        }
                    }
                }
            }
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
                                onClick = {
                                    textFieldState.clearText()
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = clearSearchDescription
                                }
                            ) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                )

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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultsContent(
    tracks: List<LocalAudioTrack>,
    matchingTracks: List<LocalAudioTrack>,
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
    onTrackClick: (LocalAudioTrack, Int) -> Unit
) {
    when {
        !hasAudioPermission -> {
            SearchMessage(
                message = stringResource(R.string.library_no_tracks),
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        top = messageTopPadding,
                        bottom = messageBottomPadding
                    )
            )
        }

        isLoading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        top = messageTopPadding,
                        bottom = messageBottomPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        loadFailed -> {
            SearchMessage(
                message = stringResource(
                    R.string.library_load_error
                ),
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        top = messageTopPadding,
                        bottom = messageBottomPadding
                    ),
                actionLabel = stringResource(
                    R.string.library_retry
                ),
                onAction = onRetry
            )
        }

        tracks.isEmpty() -> {
            SearchMessage(
                message = stringResource(
                    R.string.library_no_tracks
                ),
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        top = messageTopPadding,
                        bottom = messageBottomPadding
                    )
            )
        }

        !hasQuery -> {
            Box(
                modifier = modifier.fillMaxSize()
            )
        }

        isSearchPending && matchingTracks.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize()
            )
        }

        matchingTracks.isEmpty() -> {
            SearchMessage(
                message = stringResource(
                    R.string.search_no_results
                ),
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        top = messageTopPadding,
                        bottom = messageBottomPadding
                    )
            )
        }

        else -> {
            TrackList(
                tracks = matchingTracks,
                onTrackClick = onTrackClick,
                modifier = modifier.fillMaxSize(),
                outerPadding = PaddingValues(
                    horizontal = 0.dp,
                    vertical = 1.dp
                ),
                contentInsetPadding = contentInsetPadding,
                horizontalPadding = 18.dp,
                verticalSpacing = 1.dp,
                currentTrackId = currentMediaId,
                itemsEnabled = itemsEnabled
            )
        }
    }
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

private fun matchesIndexedTrack(
    indexedTrack: IndexedTrack,
    normalizedQuery: String,
    queryTerms: List<String>
): Boolean {
    if (indexedTrack.searchableText.contains(normalizedQuery)) {
        return true
    }

    return queryTerms.all { term ->
        val directMatch = indexedTrack.words.any { word ->
            word.contains(term)
        }

        if (directMatch) {
            true
        } else if (term.length >= 3) {
            val maxDistance = if (term.length <= 4) 1 else 2

            indexedTrack.words.any { word ->
                kotlin.math.abs(word.length - term.length) <= maxDistance &&
                    editDistanceWithin(
                        left = term,
                        right = word,
                        maxDistance = maxDistance
                    )
            }
        } else {
            false
        }
    }
}

private fun normalizeSearchText(value: String): String {
    return Normalizer.normalize(
        value,
        Normalizer.Form.NFD
    )
        .replace(SearchDiacritics, "")
        .lowercase(Locale.ROOT)
        .trim()
}

private fun editDistanceWithin(
    left: String,
    right: String,
    maxDistance: Int
): Boolean {
    if (kotlin.math.abs(left.length - right.length) > maxDistance) {
        return false
    }

    val outsideRange = maxDistance + 1

    var previousRow = IntArray(right.length + 1) { index ->
        if (index <= maxDistance) index else outsideRange
    }

    for (leftIndex in 1..left.length) {
        val currentRow = IntArray(right.length + 1) {
            outsideRange
        }

        if (leftIndex <= maxDistance) {
            currentRow[0] = leftIndex
        }

        val startColumn = maxOf(1, leftIndex - maxDistance)
        val endColumn = minOf(right.length, leftIndex + maxDistance)
        var rowMinimum = outsideRange

        for (rightIndex in startColumn..endColumn) {
            val substitutionCost = if (
                left[leftIndex - 1] == right[rightIndex - 1]
            ) {
                0
            } else {
                1
            }

            currentRow[rightIndex] = minOf(
                currentRow[rightIndex - 1] + 1,
                previousRow[rightIndex] + 1,
                previousRow[rightIndex - 1] + substitutionCost
            )

            rowMinimum = minOf(
                rowMinimum,
                currentRow[rightIndex]
            )
        }

        if (rowMinimum > maxDistance) {
            return false
        }

        previousRow = currentRow
    }

    return previousRow[right.length] <= maxDistance
}