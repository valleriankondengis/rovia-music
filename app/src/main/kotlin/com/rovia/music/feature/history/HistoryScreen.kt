package com.rovia.music.feature.history

import android.net.Uri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.rovia.music.R
import com.rovia.music.data.local.entity.PlaybackActivityEntity
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.repository.PlaybackHistoryRepository
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.TrackList
import com.rovia.music.ui.components.fadeScrimProgressiveBlur

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


private val HistoryNavigationBarHeight = 80.dp
private val HistoryMiniPlayerHeight = 80.dp
private const val HistoryLoadMoreThreshold = 4

enum class HistoryActivityType {
    TRACK,
    ARTIST,
    ALBUM,
    GENRE,
    PLAYLIST,
    LIKES,
}

data class HistoryActivityUiModel(
    val activityId: Long,
    val activityKey: String,
    val entityKey: String,
    val type: HistoryActivityType,
    val title: String,
    val subtitle: String?,
    val typeLabel: String,
    val timestampEpochMillis: Long,
    val timeZoneIdAtEvent: String,
    val artworkUri: String?,
    val trackId: Long? = null,
    val playCount: Long = 1L,
    val entityTypeName: String = type.name,
)

private data class HistoryDisplayGroupKey(
    val localDate: LocalDate,
    val timeZoneId: String,
    val entityIdentity: String,
)

@Composable
fun HistoryScreen(
    items: List<HistoryActivityUiModel>,
    title: String,
    emptyMessage: String,
    errorMessage: String,
    retryLabel: String,
    backContentDescription: String,
    isLoading: Boolean,
    loadFailed: Boolean,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    miniPlayerVisible: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onActivityClick: (HistoryActivityUiModel) -> Unit,
    onTrackInfoClick: (HistoryActivityUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }

    var loadRequestedAtItemCount by remember {
        mutableIntStateOf(-1)
    }

    val displayItems = remember(items) {
        val groupedItems = LinkedHashMap<HistoryDisplayGroupKey, HistoryActivityUiModel>()

        items.forEach { item ->
            val zoneId = resolveHistoryZoneId(item.timeZoneIdAtEvent)
            val localDate = Instant.ofEpochMilli(item.timestampEpochMillis)
                .atZone(zoneId)
                .toLocalDate()
            val entityIdentity = if (item.type == HistoryActivityType.TRACK) {
                item.trackId?.let { "track-id:$it" } ?: "track-key:${item.entityKey}"
            } else {
                "activity:${item.entityTypeName}:${item.entityKey}"
            }
            val key = HistoryDisplayGroupKey(
                localDate = localDate,
                timeZoneId = item.timeZoneIdAtEvent,
                entityIdentity = entityIdentity,
            )

            val existingItem = groupedItems[key]
            if (existingItem == null) {
                groupedItems[key] = item
            } else if (item.type != HistoryActivityType.TRACK) {
                groupedItems[key] = existingItem.copy(
                    playCount = addPlayCounts(existingItem.playCount, item.playCount),
                )
            }
        }

        groupedItems.values.toList()
    }

    val historyTracks = remember(displayItems) {
        displayItems.map { item ->
            val supportingText = when (item.type) {
                HistoryActivityType.TRACK -> item.subtitle
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: item.typeLabel

                else -> listOfNotNull(
                    item.typeLabel.takeIf(String::isNotBlank),
                    item.subtitle?.trim()?.takeIf(String::isNotEmpty),
                ).joinToString(" · ")
            }

            LocalAudioTrack(
                id = item.activityId,
                uri = item.artworkUri
                    ?.takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                    ?: Uri.EMPTY,
                title = item.title,
                artist = supportingText,
                album = null,
                durationMs = 0L,
            )
        }
    }

    val displayItemsById = remember(displayItems) {
        displayItems.associateBy(HistoryActivityUiModel::activityId)
    }

    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    }
    val sectionLabels = remember(displayItems, formatter) {
        displayItems.associate { item ->
            val zoneId = resolveHistoryZoneId(item.timeZoneIdAtEvent)
            val date = Instant.ofEpochMilli(item.timestampEpochMillis)
                .atZone(zoneId)
                .toLocalDate()
            item.activityId to formatter.format(date)
        }
    }
    val sectionKey: (LocalAudioTrack) -> String = remember(sectionLabels) {
        { track -> sectionLabels[track.id].orEmpty() }
    }

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            12.dp

    val contentBottomPadding =
        navigationBarBottomInset +
            HistoryNavigationBarHeight +
            16.dp +
            if (miniPlayerVisible) HistoryMiniPlayerHeight else 0.dp

    LaunchedEffect(
        listState,
        historyTracks.size,
        canLoadMore,
        isLoading,
        isLoadingMore,
    ) {
        if (
            historyTracks.isEmpty() ||
            !canLoadMore ||
            isLoading ||
            isLoadingMore ||
            loadRequestedAtItemCount == historyTracks.size
        ) {
            return@LaunchedEffect
        }

        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val thresholdIndex =
                    (historyTracks.lastIndex - HistoryLoadMoreThreshold).coerceAtLeast(0)

                if (
                    lastVisibleIndex >= thresholdIndex &&
                    loadRequestedAtItemCount != historyTracks.size
                ) {
                    loadRequestedAtItemCount = historyTracks.size
                    latestOnLoadMore()
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = miniPlayerVisible)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            displayItems.isEmpty() && loadFailed -> {
                HistoryMessage(
                    message = errorMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = miniPlayerVisible)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding,
                        ),
                    actionLabel = retryLabel,
                    onAction = onRetry,
                )
            }

            displayItems.isEmpty() -> {
                HistoryMessage(
                    message = emptyMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = miniPlayerVisible)
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding,
                        ),
                )
            }

            else -> {
                TrackList(
                    tracks = historyTracks,
                    onTrackClick = { _, index ->
                        displayItems.getOrNull(index)?.let(onActivityClick)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .fadeScrimProgressiveBlur(withMiniPlayer = miniPlayerVisible),
                    outerPadding = PaddingValues(horizontal = 0.dp, vertical = 1.dp),
                    contentInsetPadding = PaddingValues(
                        top = contentTopPadding,
                        bottom = contentBottomPadding,
                    ),
                    horizontalPadding = 18.dp,
                    verticalSpacing = 1.dp,
                    currentTrackId = null,
                    itemsEnabled = true,
                    onTrackInfoClick = { track ->
                        displayItemsById[track.id]
                            ?.takeIf { it.type == HistoryActivityType.TRACK }
                            ?.let(onTrackInfoClick)
                    },
                    trackInfoEnabled = { track ->
                        displayItemsById[track.id]?.type == HistoryActivityType.TRACK
                    },
                    listState = listState,
                    sectionKey = sectionKey,
                    sectionHeaderContent = { dateLabel ->
                        Text(
                            text = dateLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 18.dp,
                                    vertical = 12.dp,
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    showDuration = false,
                    supportingContentOverride = { track ->
                        val historyItem = displayItemsById[track.id]
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = track.artist.orEmpty()
                                    .ifBlank { stringResource(R.string.player_unknown_artist) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (historyItem != null) {
                                val playCount = historyItem.playCount
                                    .coerceAtLeast(1L)
                                    .coerceAtMost(Int.MAX_VALUE.toLong())
                                    .toInt()
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.history_play_count,
                                        playCount,
                                        playCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    footerContent = {
                        if (isLoadingMore) {
                            item(key = "history-loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (loadFailed) {
                            item(key = "history-load-more-error") {
                                HistoryMessage(
                                    message = errorMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    actionLabel = retryLabel,
                                    onAction = onRetry,
                                )
                            }
                        }
                    },
                )
            }
        }

        FadeScrim(
            edge = FadeScrimEdge.Top,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FadeScrim(
            edge = FadeScrimEdge.Bottom,
            modifier = Modifier.align(Alignment.BottomCenter),
            withMiniPlayer = miniPlayerVisible,
        )

        TopAppBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = backContentDescription,
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }
}

@Composable
private fun HistoryMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun addPlayCounts(first: Long, second: Long): Long {
    val left = first.coerceAtLeast(0L)
    val right = second.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

private fun resolveHistoryZoneId(timeZoneId: String): ZoneId =
    runCatching { ZoneId.of(timeZoneId) }
        .getOrElse { ZoneId.systemDefault() }


private const val HISTORY_PAGE_SIZE = 50

data class HistoryUiState(
    val activities: List<PlaybackActivityEntity> = emptyList(),
    val dailyTrackPlayCounts: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasReachedEnd: Boolean = false,
)

class HistoryViewModel(
    private val repository: PlaybackHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()

    private val stateUpdateMutex = Mutex()
    private var observeJob: Job? = null

    init {
        observeLatest()
    }

    fun retry() {
        if (_state.value.activities.isEmpty()) {
            observeLatest()
        } else {
            loadMore()
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.activities.lastOrNull() ?: return
        if (!current.canLoadMore || current.isLoadingMore) return

        _state.update { it.copy(isLoadingMore = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val nextPage = repository.getPageBefore(
                    beforeTimestampEpochMillis = cursor.timestampEpochMillis,
                    beforeActivityId = cursor.activityId,
                    limit = HISTORY_PAGE_SIZE,
                )

                stateUpdateMutex.withLock {
                    val merged = mergeActivities(_state.value.activities + nextPage)
                    val playCounts = repository.getDailyTrackPlayCounts(merged)
                    _state.update { latest ->
                        latest.copy(
                            activities = mergeActivities(latest.activities + nextPage),
                            dailyTrackPlayCounts = playCounts,
                            isLoadingMore = false,
                            loadFailed = false,
                            canLoadMore = nextPage.size == HISTORY_PAGE_SIZE,
                            hasReachedEnd = nextPage.size < HISTORY_PAGE_SIZE,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(isLoadingMore = false, loadFailed = true)
                }
            }
        }
    }

    private fun observeLatest() {
        observeJob?.cancel()
        _state.update {
            it.copy(
                isLoading = it.activities.isEmpty(),
                loadFailed = false,
            )
        }
        observeJob = viewModelScope.launch {
            repository.observeLatest(HISTORY_PAGE_SIZE)
                .catch {
                    _state.update {
                        it.copy(isLoading = false, loadFailed = true)
                    }
                }
                .collect { latestPage ->
                    try {
                        stateUpdateMutex.withLock {
                            val previousLatestKeys = _state.value.activities
                                .take(HISTORY_PAGE_SIZE)
                                .map(PlaybackActivityEntity::activityKey)
                            val incomingLatestKeys = latestPage
                                .map(PlaybackActivityEntity::activityKey)
                            val latestPageChanged = previousLatestKeys != incomingLatestKeys
                            val merged = mergeActivities(
                                latestPage + _state.value.activities,
                            )
                            val playCounts = repository.getDailyTrackPlayCounts(merged)
                            _state.update { current ->
                                val latestMerged = mergeActivities(
                                    latestPage + current.activities,
                                )
                                val canContinueLoading = when {
                                    latestPageChanged ->
                                        latestPage.size == HISTORY_PAGE_SIZE || current.canLoadMore
                                    current.hasReachedEnd -> false
                                    current.activities.isEmpty() ->
                                        latestPage.size == HISTORY_PAGE_SIZE
                                    else -> current.canLoadMore
                                }
                                current.copy(
                                    activities = latestMerged,
                                    dailyTrackPlayCounts = playCounts,
                                    isLoading = false,
                                    loadFailed = false,
                                    canLoadMore = canContinueLoading,
                                    hasReachedEnd = if (latestPageChanged) {
                                        false
                                    } else {
                                        current.hasReachedEnd
                                    },
                                )
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _state.update {
                            it.copy(isLoading = false, loadFailed = true)
                        }
                    }
                }
        }
    }

    private fun mergeActivities(
        activities: List<PlaybackActivityEntity>,
    ): List<PlaybackActivityEntity> = activities
        .distinctBy(PlaybackActivityEntity::activityKey)
        .sortedWith(
            compareByDescending<PlaybackActivityEntity> {
                it.timestampEpochMillis
            }.thenByDescending { it.activityId },
        )
}

class HistoryViewModelFactory(
    private val repository: PlaybackHistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
        }
        return HistoryViewModel(repository) as T
    }
}