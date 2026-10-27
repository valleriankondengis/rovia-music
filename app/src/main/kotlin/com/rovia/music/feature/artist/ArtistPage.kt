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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rovia.music.R
import com.rovia.music.data.mediastore.AlbumParser
import com.rovia.music.data.mediastore.AlbumRelease
import com.rovia.music.data.mediastore.AlbumReleaseType
import com.rovia.music.data.mediastore.LocalAudioTrack
import com.rovia.music.playback.controller.PlaybackController
import com.rovia.music.ui.components.AlbumArtwork
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.ArtworkBitmapLoader
import java.text.Normalizer
import java.util.Locale

private val ArtistCarouselArtworkSize = 150.dp
private val ArtistCarouselItemWidth = ArtistCarouselArtworkSize
private val ArtistCarouselItemHeight = 220.dp

private data class ArtistGenreCollection(
    val key: String,
    val title: String,
    val tracks: List<LocalAudioTrack>
) {
    val artworkUri: String?
        get() = tracks.firstOrNull()?.uri?.toString()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistPage(
    artistName: String,
    tracks: List<LocalAudioTrack>,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onOpenSingle: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    @StringRes songsAndEpsTitleRes: Int
) {
    val context = LocalContext.current
    val artistKey = remember(artistName) { normalizeArtistKey(artistName) }

    val artistTracks = remember(tracks, artistKey) {
        tracks.filter { track ->
            track.artistNames.any { creditedArtist ->
                normalizeArtistKey(creditedArtist) == artistKey
            }
        }
    }

    // Parse the entire catalog so release classification uses every locally available track in
    // an album, rather than only tracks on which this particular artist is credited.
    val artistReleases = remember(tracks, artistKey) {
        AlbumParser.parse(tracks).filter { release ->
            release.isForArtist(artistName)
        }
    }
    val singlesAndEps = remember(artistReleases) {
        artistReleases.filter { release ->
            release.type == AlbumReleaseType.SINGLE ||
                release.type == AlbumReleaseType.EP
        }
    }
    val albums = remember(artistReleases) {
        artistReleases.filter { it.type == AlbumReleaseType.ALBUM }
    }
    val genres = remember(artistTracks) {
        buildArtistGenres(artistTracks)
    }

    val mediaController by playbackController.mediaController
        .collectAsStateWithLifecycle()
    val currentTrackId = mediaController?.currentMediaItem?.mediaId

    val heroArtworkUri = remember(artistTracks) {
        (
            artistTracks.firstOrNull { !it.album.isNullOrBlank() }
                ?: artistTracks.firstOrNull()
        )?.uri?.toString()
    }

    val navigationBarBottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val reservedBottomSpace = navigationBarBottomInset + 80.dp + 16.dp +
        if (currentTrackId != null) 80.dp else 0.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.72f)
            .coerceAtLeast(380.dp)
            .coerceAtMost(maxHeight)

        Box(modifier = Modifier.fillMaxSize()) {
            // Fixed back layer: only the blurred artwork remains when the sharp hero scrolls away.
            ArtworkBackdrop(
                artworkUri = heroArtworkUri,
                foregroundArtworkHeight = 0.dp,
                modifier = Modifier.fillMaxSize()
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = reservedBottomSpace),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item(key = "artist-hero") {
                    ArtistHeroHeader(
                        artistName = artistName,
                        artworkUri = heroArtworkUri,
                        heroHeight = heroHeight,
                        trackCount = artistTracks.size,
                        onPlay = {
                            if (artistTracks.isNotEmpty()) {
                                playbackController.playQueue(artistTracks, 0)
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
                                            release.tracks.any { it.id.toString() == currentTrackId },
                                        onClick = {
                                            onOpenSingle(release.key)
                                        }
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
                                            release.tracks.any { it.id.toString() == currentTrackId },
                                        onClick = {
                                            onOpenAlbum(release.key)
                                        }
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
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(
                                    items = genres,
                                    key = { "genre:${it.key}" }
                                ) { genre ->
                                    ArtistGenreCarouselItem(
                                        genre = genre,
                                        onClick = {
                                            onOpenGenre(genre.key)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Match HomeRecentlyAddedScreen's TopAppBar/FilledIconButton sizing and colors.
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
                contentPadding = PaddingValues(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun ArtistHeroHeader(
    artistName: String,
    artworkUri: String?,
    heroHeight: Dp,
    trackCount: Int,
    onPlay: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val heroBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = artworkUri,
        key2 = context
    ) {
        value = ArtworkBitmapLoader.load(
            context = context,
            artworkUri = artworkUri,
            maxDimension = 1280
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // The foreground artwork fades out at its lower edge into the fixed blurred backdrop.
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
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(
                    R.plurals.library_track_count,
                    trackCount,
                    trackCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
            Button(
                onClick = onPlay,
                enabled = trackCount > 0,
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.player_play))
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
            .padding(top = 22.dp, bottom = 10.dp),
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
        release.tracks
            .mapNotNull(LocalAudioTrack::releaseYear)
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
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

    ArtistCarouselItem(
        title = release.title,
        supportingText = supportingText,
        artworkUri = release.artworkUri,
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

private fun buildArtistGenres(
    tracks: List<LocalAudioTrack>
): List<ArtistGenreCollection> {
    val groups = linkedMapOf<String, Pair<String, MutableList<LocalAudioTrack>>>()

    tracks.forEach { track ->
        track.genres.forEach genreLoop@{ rawGenre ->
            val genre = rawGenre.trim()
            if (genre.isBlank()) return@genreLoop

            val key = normalizeArtistKey(genre)
            val group = groups.getOrPut(key) { genre to mutableListOf() }
            if (group.second.none { it.id == track.id }) {
                group.second += track
            }
        }
    }

    return groups.map { (key, value) ->
        ArtistGenreCollection(
            key = key,
            title = value.first,
            tracks = value.second.toList()
        )
    }.sortedBy { it.title.lowercase(Locale.ROOT) }
}

private fun normalizeArtistKey(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)