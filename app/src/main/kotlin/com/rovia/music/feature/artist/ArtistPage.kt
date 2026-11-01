package com.rovia.music.feature.artist

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.R
import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.ArtistCreditParser
import com.rovia.music.data.mediastore.CatalogArtworkSelector
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.playback.queue.PlaybackOrigin
import com.rovia.music.playback.queue.PlaybackOriginType
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.ArtworkBitmapLoader
import com.rovia.music.ui.components.FadeScrim
import com.rovia.music.ui.components.FadeScrimEdge
import com.rovia.music.ui.components.fadeScrimProgressiveBlur
import kotlinx.coroutines.CancellationException

private val ArtistCarouselArtworkSize = 150.dp
private val ArtistCarouselItemWidth = ArtistCarouselArtworkSize
private val ArtistCarouselItemHeight = 220.dp

private data class ArtistGenreCollection(
    val key: String,
    val title: String,
    val tracks: List<LocalAudioTrack>
) {
    val artworkUri: String?
        get() = CatalogArtworkSelector.latestReleaseTrack(tracks)?.uri?.toString()
}

private class ArtistGenreCollectionBuilder(
    val title: String,
) {
    val tracks = mutableListOf<LocalAudioTrack>()
    val trackIds = hashSetOf<Long>()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistPage(
    artistName: String,
    tracks: List<LocalAudioTrack>,
    releases: List<AlbumRelease>,
    onLoadReleaseMetadata: suspend (LocalAudioTrack) -> LocalAudioTrack,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onOpenSingle: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    @StringRes songsAndEpsTitleRes: Int,
) {
    val artistKey = remember(artistName) {
        ArtistCreditParser.normalizeIdentity(artistName)
    }

    val artistTracks = tracks
    val artistReleases = releases
    val latestLoadReleaseMetadata = rememberUpdatedState(onLoadReleaseMetadata)
    val releaseKeys = remember(artistReleases) {
        artistReleases.map(AlbumRelease::key)
    }

    LaunchedEffect(artistKey, releaseKeys) {
        artistReleases.forEach { release ->
            val representativeTrack =
                CatalogArtworkSelector.albumArtworkTrack(release.tracks)
                    ?: return@forEach

            try {
                latestLoadReleaseMetadata.value(representativeTrack)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    val sortedArtistReleases = remember(artistReleases) {
        sortReleasesByRecency(artistReleases)
    }

    val resolvedArtistName = remember(artistName, artistTracks, sortedArtistReleases) {
        sortedArtistReleases
            .firstNotNullOfOrNull { release ->
                ArtistCreditParser.resolveDisplayName(
                    artistName = artistName,
                    primaryCredits = listOf(release.albumArtist),
                    fallbackCredits = emptyList()
                ) ?: ArtistCreditParser.resolvePrimaryDisplayName(
                    release.tracks.map(LocalAudioTrack::artist)
                )?.takeIf {
                    ArtistCreditParser.normalizeIdentity(it) == artistKey
                }
            }
            ?: ArtistCreditParser.resolveDisplayName(
                artistName = artistName,
                primaryCredits = artistTracks.map(LocalAudioTrack::albumArtist),
                fallbackCredits = artistTracks.map(LocalAudioTrack::artist)
            )
            ?: artistName.trim()
    }

    val heroArtworkUri = remember(artistName, artistTracks, artistReleases) {
        CatalogArtworkSelector.artistArtworkTrack(
            artistName = artistName,
            releases = artistReleases,
            ungroupedArtistTracks = artistTracks.filter { it.album.isNullOrBlank() }
        )?.uri?.toString()
    }

    val singlesAndEps = remember(sortedArtistReleases) {
        sortedArtistReleases.filter { release ->
            release.type == AlbumReleaseType.SINGLE || release.type == AlbumReleaseType.EP
        }
    }

    val albums = remember(sortedArtistReleases) {
        sortedArtistReleases.filter { it.type == AlbumReleaseType.ALBUM }
    }

    val genres = remember(artistTracks) {
        buildArtistGenres(artistTracks)
    }

    val singlesAndEpsListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val genresListState = rememberLazyListState()

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()
    val currentTrackId = mediaController?.currentMediaItem?.mediaId

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val reservedBottomSpace = navigationBarBottomInset + 80.dp + 16.dp +
        if (currentTrackId != null) 80.dp else 0.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heroHeightFraction = if (singlesAndEps.isNotEmpty() || albums.isNotEmpty()) {
            0.68f
        } else {
            0.72f
        }
        val heroHeight = (maxHeight * heroHeightFraction)
            .coerceAtLeast(380.dp)
            .coerceAtMost(maxHeight)
        val playButtonWidth = (maxWidth * 0.66f)
            .coerceAtMost(360.dp)
            .coerceAtLeast(220.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            key(heroArtworkUri) {
                ArtworkBackdrop(
                    artworkUri = heroArtworkUri,
                    foregroundArtworkHeight = 0.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fadeScrimProgressiveBlur(withMiniPlayer = currentTrackId != null),
                contentPadding = PaddingValues(bottom = reservedBottomSpace),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item(key = "artist-hero") {
                    ArtistHeroHeader(
                        artistName = resolvedArtistName,
                        artworkUri = heroArtworkUri,
                        heroHeight = heroHeight,
                        playButtonWidth = playButtonWidth,
                        trackCount = artistTracks.size,
                        onPlay = {
                            if (artistTracks.isNotEmpty()) {
                                playbackController.playQueue(
                                    tracks = artistTracks,
                                    startIndex = 0,
                                    origin = PlaybackOrigin(
                                        type = PlaybackOriginType.ARTIST,
                                        entityKey = resolvedArtistName,
                                        title = resolvedArtistName,
                                        artworkSourceUri = heroArtworkUri
                                    )
                                )
                            }
                        }
                    )
                }

                if (singlesAndEps.isNotEmpty()) {
                    item(key = "artist-singles-and-eps") {
                        ArtistCarouselSection(
                            title = stringResource(songsAndEpsTitleRes)
                        ) {
                            LazyRow(
                                state = singlesAndEpsListState,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(
                                    items = singlesAndEps,
                                    key = { "release:${it.key}" }
                                ) { release ->
                                    ArtistReleaseCarouselItem(
                                        release = release,
                                        isCurrent = currentTrackId != null &&
                                            release.tracks.any {
                                                it.id.toString() == currentTrackId
                                            },
                                        onClick = { onOpenSingle(release.key) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item(key = "artist-albums") {
                        ArtistCarouselSection(
                            title = stringResource(R.string.library_category_albums)
                        ) {
                            LazyRow(
                                state = albumsListState,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(
                                    items = albums,
                                    key = { "album:${it.key}" }
                                ) { release ->
                                    ArtistReleaseCarouselItem(
                                        release = release,
                                        isCurrent = currentTrackId != null &&
                                            release.tracks.any {
                                                it.id.toString() == currentTrackId
                                            },
                                        onClick = { onOpenAlbum(release.key) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (genres.isNotEmpty()) {
                    item(key = "artist-genres") {
                        ArtistCarouselSection(
                            title = stringResource(R.string.library_category_genres)
                        ) {
                            LazyRow(
                                state = genresListState,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(
                                    items = genres,
                                    key = { "genre:${it.key}" }
                                ) { genre ->
                                    ArtistGenreCarouselItem(
                                        genre = genre,
                                        onClick = { onOpenGenre(genre.key) }
                                    )
                                }
                            }
                        }
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
                withMiniPlayer = currentTrackId != null
            )

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
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun ArtistHeroHeader(
    artistName: String,
    artworkUri: String?,
    heroHeight: Dp,
    playButtonWidth: Dp,
    trackCount: Int,
    onPlay: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var heroBitmap by remember(artworkUri, context) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(artworkUri, context) {
        heroBitmap = null
        if (!artworkUri.isNullOrBlank()) {
            heroBitmap = ArtworkBitmapLoader.load(
                context = context,
                artworkUri = artworkUri,
                maxDimension = 1280
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                0.48f to Color.White,
                                0.76f to Color.White.copy(alpha = 0.56f),
                                1f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .background(Color.Transparent)
        ) {
            val bitmap = heroBitmap?.takeUnless(Bitmap::isRecycled)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = artistName,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(
                    R.plurals.library_track_count,
                    trackCount,
                    trackCount
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onPlay,
                enabled = trackCount > 0,
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
        }
    }
}

@Composable
private fun ArtistCarouselSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun ArtistReleaseCarouselItem(
    release: AlbumRelease,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
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
    val supportingText = if (releaseYear != null) {
        stringResource(
            R.string.artist_release_type_year,
            releaseTypeLabel,
            releaseYear
        )
    } else {
        releaseTypeLabel
    }

    val artworkUri = remember(release.tracks) {
        CatalogArtworkSelector.albumArtworkTrack(release.tracks)?.uri?.toString()
    }

    ArtistCarouselItem(
        title = release.title,
        supportingText = supportingText,
        artworkUri = artworkUri,
        fallbackText = release.title,
        contentDescription = release.title,
        isCurrent = isCurrent,
        onClick = onClick
    )
}

@Composable
private fun ArtistGenreCarouselItem(
    genre: ArtistGenreCollection,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val supportingText = context.resources.getQuantityString(
        R.plurals.library_track_count,
        genre.tracks.size,
        genre.tracks.size
    )

    ArtistCarouselItem(
        title = genre.title,
        supportingText = supportingText,
        artworkUri = genre.artworkUri,
        fallbackText = genre.title,
        contentDescription = genre.title,
        onClick = onClick
    )
}

@Composable
private fun ArtistCarouselItem(
    title: String,
    supportingText: String,
    artworkUri: String?,
    fallbackText: String,
    contentDescription: String?,
    onClick: () -> Unit,
    isCurrent: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(ArtistCarouselItemWidth)
            .height(ArtistCarouselItemHeight)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArtwork(
            artworkUri = artworkUri,
            fallbackText = fallbackText,
            contentDescription = contentDescription,
            modifier = Modifier.size(ArtistCarouselArtworkSize),
            size = ArtistCarouselArtworkSize,
            shape = MaterialTheme.shapes.large
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            minLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun bestReleaseMetadataTrack(
    tracks: List<LocalAudioTrack>
): LocalAudioTrack? = CatalogArtworkSelector.bestReleaseMetadataTrack(tracks)

private data class ReleaseRecencyKey(
    val release: AlbumRelease,
    val timestamp: Long?,
    val dateAddedSeconds: Long,
    val dateModifiedSeconds: Long,
)

private fun sortReleasesByRecency(releases: List<AlbumRelease>): List<AlbumRelease> {
    val keyedReleases = releases.map { release ->
        val bestTrack = bestReleaseMetadataTrack(release.tracks)
        ReleaseRecencyKey(
            release = release,
            timestamp = bestTrack?.releaseSortTimestamp(),
            dateAddedSeconds = bestTrack?.dateAddedSeconds ?: 0L,
            dateModifiedSeconds = bestTrack?.dateModifiedSeconds ?: 0L,
        )
    }

    return keyedReleases.sortedWith { left, right ->
        when {
            left.timestamp != null && right.timestamp != null && left.timestamp != right.timestamp ->
                right.timestamp.compareTo(left.timestamp)
            left.timestamp != null && right.timestamp == null -> -1
            left.timestamp == null && right.timestamp != null -> 1
            left.dateAddedSeconds != right.dateAddedSeconds ->
                right.dateAddedSeconds.compareTo(left.dateAddedSeconds)
            left.dateModifiedSeconds != right.dateModifiedSeconds ->
                right.dateModifiedSeconds.compareTo(left.dateModifiedSeconds)
            else -> String.CASE_INSENSITIVE_ORDER.compare(
                left.release.title,
                right.release.title,
            )
        }
    }.map(ReleaseRecencyKey::release)
}


private fun LocalAudioTrack.releaseSortTimestamp(): Long? =
    CatalogArtworkSelector.releaseSortTimestamp(this)

private fun buildArtistGenres(
    tracks: List<LocalAudioTrack>
): List<ArtistGenreCollection> {
    val groups = linkedMapOf<String, ArtistGenreCollectionBuilder>()

    tracks.forEach { track ->
        track.genres.forEach genreLoop@{ rawGenre ->
            val genre = rawGenre.trim()
            if (genre.isBlank()) return@genreLoop

            val key = normalizeArtistKey(genre)
            val group = groups.getOrPut(key) { ArtistGenreCollectionBuilder(genre) }
            if (group.trackIds.add(track.id)) {
                group.tracks += track
            }
        }
    }

    return groups.map { (key, value) ->
        ArtistGenreCollection(
            key = key,
            title = value.title,
            tracks = value.tracks,
        )
    }.sortedBy { it.title.lowercase() }
}

private fun normalizeArtistKey(value: String): String =
    ArtistCreditParser.normalizeIdentity(value)