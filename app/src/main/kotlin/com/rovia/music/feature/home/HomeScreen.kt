package com.rovia.music.feature.home

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rovia.music.R
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.data.mediastore.MediaStoreAudioRepository
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.TrackListCard
import com.rovia.music.ui.permissions.rememberAudioPermissionGranted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import com.rovia.music.feature.wrapped.WrappedPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.style.TextOverflow
import java.text.NumberFormat

private val HomeNavigationBarHeight = 80.dp
private val HomeMiniPlayerHeight = 80.dp
private val HomeCarouselArtworkSize = 150.dp
private val HomeCarouselItemWidth = HomeCarouselArtworkSize
private val HomeCarouselItemHeight = 220.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    audioRepository: MediaStoreAudioRepository,
    playbackController: PlaybackController,
    onOpenRecentlyAdded: () -> Unit,
    onOpenStreaming: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWrapped: (WrappedPeriod) -> Unit,
    wrappedCards: List<WrappedHomeCardData> = emptyList()
) {
    val hasAudioPermission = rememberAudioPermissionGranted()

    val recentlyAddedCarouselState = remember { LazyListState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val carouselScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                carouselScope.launch {
                    recentlyAddedCarouselState.scrollToItem(0)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var recentlyAddedTracks by remember {
        mutableStateOf<List<LocalAudioTrack>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var loadFailed by remember {
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

    val statusBarTopInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    val contentTopPadding =
        statusBarTopInset +
            TopAppBarDefaults.TopAppBarExpandedHeight +
            4.dp

    val contentBottomPadding =
        navigationBarBottomInset +
            HomeNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) {
                HomeMiniPlayerHeight
            } else {
                0.dp
            }

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
            recentlyAddedTracks = emptyList()
            isLoading = false
            loadFailed = false
            return@LaunchedEffect
        }

        isLoading = true
        loadFailed = false

        try {
            recentlyAddedTracks =
                audioRepository.loadRecentlyAddedTracks()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            recentlyAddedTracks = emptyList()
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentTopPadding),
            contentPadding = PaddingValues(
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wrappedCards.isNotEmpty()) {
                item {
                    WrappedCarousel(
                        cards = wrappedCards,
                        onOpenWrapped = onOpenWrapped
                    )
                }
            }

            item {
                RecentlyAddedCarouselSection(
                    tracks = recentlyAddedTracks,
                    currentMediaId = currentMediaId,
                    itemsEnabled = mediaController != null,
                    isLoading = isLoading,
                    loadFailed = loadFailed,
                    onRetry = {
                        reloadRequest += 1
                    },
                    carouselState = recentlyAddedCarouselState,
                    onTrackClick = { track, index ->
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
                                playbackController.playQueue(
                                    tracks = recentlyAddedTracks,
                                    startIndex = index
                                )
                            }
                        }
                    },
                    onOpenRecentlyAdded = onOpenRecentlyAdded
                )
            }
        }

        TopAppBar(
            title = {},
            actions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = onOpenStreaming,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stream),
                            contentDescription = stringResource(R.string.streaming_open)
                        )
                    }

                    FilledIconButton(
                        onClick = onOpenHistory,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = stringResource(R.string.history_open)
                        )
                    }

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
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentlyAddedCarouselSection(
    tracks: List<LocalAudioTrack>,
    carouselState: LazyListState,
    currentMediaId: String?,
    itemsEnabled: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onTrackClick: (LocalAudioTrack, Int) -> Unit,
    onOpenRecentlyAdded: () -> Unit
) {
    val previewTracks = tracks.take(10)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_recently_added_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FilledIconButton(
                onClick = onOpenRecentlyAdded,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(
                        R.string.home_recently_added_view_all
                    )
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }

            loadFailed -> {
                CardMessage(
                    message = stringResource(R.string.home_recently_added_load_error),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = onRetry
                )
            }

            previewTracks.isEmpty() -> {
                Text(
                    text = stringResource(R.string.home_recently_added_empty),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyRow(
                    state = carouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeCarouselItemHeight),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(
                        items = previewTracks,
                        key = { _, track -> "recently-added:${track.id}" }
                    ) { index, track ->
                        TrackListCard(
                            track = track,
                            isSelected = track.id.toString() == currentMediaId,
                            enabled = itemsEnabled,
                            onClick = {
                                onTrackClick(track, index)
                            },
                            width = HomeCarouselItemWidth,
                            height = HomeCarouselItemHeight,
                            artworkSize = HomeCarouselArtworkSize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onAction
        ) {
            Text(actionLabel)
        }
    }
}
data class WrappedHomeCardData(
    val period: WrappedPeriod,
    val minutes: Long,
    val topTrack: String?,
    val topArtist: String?,
    val topAlbum: String?
)

@Composable
private fun WrappedCarousel(
    cards: List<WrappedHomeCardData>,
    onOpenWrapped: (WrappedPeriod) -> Unit
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState)
    ) {
        items(
            items = cards,
            key = { card -> "wrapped-${card.period.name}" }
        ) { card ->
            WrappedHomeCard(
                data = card,
                onClick = { onOpenWrapped(card.period) },
                modifier = Modifier.width(300.dp)
            )
        }
    }
}

@Composable
private fun WrappedHomeCard(
    data: WrappedHomeCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val period = data.period
    val subtitle = remember(period) {
        val today = LocalDate.now()
        val locale = Locale.getDefault()
        when (period) {
            WrappedPeriod.TODAY -> today.format(
                DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
            )
            WrappedPeriod.WEEK -> {
                val weekStart = today.with(
                    WeekFields.of(locale).dayOfWeek(),
                    1
                )
                val weekEnd = weekStart.plusDays(6)
                weekStart.format(
                    DateTimeFormatter.ofPattern("d MMM", locale)
                ) + " \u2013 " + weekEnd.format(
                    DateTimeFormatter.ofPattern("d MMM yyyy", locale)
                )
            }
            WrappedPeriod.MONTH -> today.format(
                DateTimeFormatter.ofPattern("MMMM yyyy", locale)
            )
            WrappedPeriod.YEAR -> today.format(
                DateTimeFormatter.ofPattern("yyyy", locale)
            )
        }
    }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
            )
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(
                        when (period) {
                            WrappedPeriod.TODAY -> R.string.wrapped_card_today
                            WrappedPeriod.WEEK -> R.string.wrapped_card_week
                            WrappedPeriod.MONTH -> R.string.wrapped_card_month
                            WrappedPeriod.YEAR -> R.string.wrapped_card_year
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.wrapped_card_minutes,
                        numberFormat.format(data.minutes)
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                data.topTrack?.let { value ->
                    Text(
                        text = stringResource(R.string.wrapped_card_top_song, value),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                data.topArtist?.let { value ->
                    Text(
                        text = stringResource(R.string.wrapped_card_top_artist, value),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                data.topAlbum?.let { value ->
                    Text(
                        text = stringResource(R.string.wrapped_card_top_album, value),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
