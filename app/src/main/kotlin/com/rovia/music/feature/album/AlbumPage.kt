package com.rovia.music.feature.album

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import java.util.Locale

private val AlbumPageNavigationBarHeight = 80.dp
private val AlbumPageMiniPlayerHeight = 80.dp

/**
 * Album/release detail page. Release type and year come from the same catalog parser and
 * MediaStore metadata used by ArtistPage; this screen only changes their presentation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumPage(
    releaseKey: String,
    catalogTracks: List<LocalAudioTrack>,
    playbackController: PlaybackController,
    onBack: () -> Unit
) {
    val release = remember(catalogTracks, releaseKey) {
        AlbumParser.parse(catalogTracks).firstOrNull { it.key == releaseKey }
    }
    val pageTitle = release?.title ?: stringResource(R.string.library_category_albums)
    val pageTracks = release?.tracks.orEmpty()
    val releaseArtist = release?.albumArtist
        ?.takeIf(String::isNotBlank)
        ?: release?.artists
            ?.joinToString(", ")
            ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_artist)

    val releaseTypeLabel = stringResource(
        when (release?.type) {
            AlbumReleaseType.SINGLE -> R.string.artist_release_type_single
            AlbumReleaseType.EP -> R.string.artist_release_type_ep
            AlbumReleaseType.ALBUM -> R.string.artist_release_type_album
            AlbumReleaseType.UNKNOWN,
            null -> R.string.artist_release_type_unknown
        }
    )
    // Match ArtistPage: use the most frequent non-null MediaStore release year among this release's tracks.
    val releaseYear = remember(release) {
        release?.tracks
            .orEmpty()
            .mapNotNull(LocalAudioTrack::releaseYear)
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }
    val trackCountLabel = pluralStringResource(
        R.plurals.library_track_count,
        pageTracks.size,
        pageTracks.size
    )
    val releaseMetadata = listOfNotNull(
        releaseTypeLabel,
        releaseYear?.toString(),
        trackCountLabel
    ).joinToString(" • ")

    val backContentDescription = stringResource(R.string.player_back)
    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()

    var currentMediaId by remember(mediaController) {
        mutableStateOf(mediaController?.currentMediaItem?.mediaId)
    }

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
            AlbumPageNavigationBarHeight +
            16.dp +
            if (currentMediaId != null) AlbumPageMiniPlayerHeight else 0.dp

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
                artworkUri = release?.artworkUri,
                foregroundArtworkHeight = 0.dp,
                modifier = Modifier.fillMaxSize()
            )

            if (pageTracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = contentTopPadding,
                            bottom = contentBottomPadding
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.library_no_tracks),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = contentTopPadding,
                        bottom = contentBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    item(key = "album-hero-$releaseKey") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AlbumArtwork(
                                artworkUri = release?.artworkUri,
                                fallbackText = pageTitle,
                                contentDescription = pageTitle,
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

                            Text(
                                text = releaseArtist,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = releaseMetadata,
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
                                            startIndex = 0
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

                    itemsIndexed(
                        items = pageTracks,
                        key = { _, track -> track.uri.toString() }
                    ) { index, track ->
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

                        SegmentedListItem(
                            selected = currentMediaId == track.id.toString(),
                            onClick = {
                                val controller = mediaController
                                if (controller != null) {
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
                                            tracks = pageTracks,
                                            startIndex = index
                                        )
                                    }
                                }
                            },
                            enabled = mediaController != null,
                            modifier = Modifier.padding(horizontal = 18.dp),
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = pageTracks.size
                            ),
                            colors = trackColors,
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
                                    text = formatAlbumTrackDuration(track.durationMs),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
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

            // Match ArtistPage's top bar spacing, back-button dimensions, and colors.
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
                            contentDescription = backContentDescription
                        )
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

private fun formatAlbumTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L
    )
}