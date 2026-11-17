package com.rovia.music.feature.wrapped

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rovia.music.R
import com.rovia.music.ui.components.AlbumArtwork
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.foundation.layout.navigationBarsPadding
import com.rovia.music.ui.components.ArtworkBackdrop
import kotlinx.serialization.Serializable
import androidx.compose.foundation.Image

data class WrappedArtist(
    val name: String,
    val artworkUri: String?,
    val genre: String?,
    val minutes: Long,
    val plays: Long,
)

data class WrappedTrack(
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val minutes: Long,
    val plays: Long,
)

data class WrappedAlbum(
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val minutes: Long,
    val plays: Long,
)

@Serializable
enum class WrappedPeriod {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
}

data class WrappedData(
    val period: WrappedPeriod,
    val periodPhrase: String,
    val recapPhrase: String,
    val fileTag: String,
    val hasData: Boolean,
    val totalMinutes: Long,
    val totalPlays: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val topArtists: List<WrappedArtist>,
    val topTracks: List<WrappedTrack>,
    val topAlbums: List<WrappedAlbum>,
)

private const val SLIDE_INTRO = 0
private const val SLIDE_ARTISTS = 1
private const val SLIDE_TRACKS = 2
private const val SLIDE_ALBUMS = 3
private const val SLIDE_SUMMARY = 4

private const val STORY_WIDTH_PX = 1080
private const val STORY_HEIGHT_PX = 1920

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedScreen(
    data: WrappedData?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            data == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                )
                ContainedLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 23.dp, end = 16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.wrapped_close),
                    )
                }
            }

            !data.hasData -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 23.dp, end = 16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.wrapped_close),
                    )
                }
                Text(
                    text = stringResource(R.string.wrapped_empty, data.periodPhrase),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                )
            }

            else -> WrappedStory(data = data, onBack = onBack)
        }
    }
}

@Composable
private fun WrappedStory(
    data: WrappedData,
    onBack: () -> Unit,
) {
    val slideKinds = remember(data) {
        buildList {
            add(SLIDE_INTRO)
            if (data.topArtists.isNotEmpty()) add(SLIDE_ARTISTS)
            if (data.topTracks.isNotEmpty()) add(SLIDE_TRACKS)
            if (data.topAlbums.isNotEmpty()) add(SLIDE_ALBUMS)
            add(SLIDE_SUMMARY)
        }
    }
    var slideIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentKind = slideKinds.getOrElse(slideIndex) { SLIDE_INTRO }

    val backdropArtworkUri = when (currentKind) {
        SLIDE_ARTISTS -> data.topArtists.firstOrNull()?.artworkUri
        SLIDE_TRACKS -> data.topTracks.firstOrNull()?.artworkUri
        SLIDE_ALBUMS -> data.topAlbums.firstOrNull()?.artworkUri
        else -> data.topTracks.firstOrNull()?.artworkUri
            ?: data.topArtists.firstOrNull()?.artworkUri
            ?: data.topAlbums.firstOrNull()?.artworkUri
    }

    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saveCompleted by remember {
        mutableStateOf(false)
    }
    val shareTitle = stringResource(R.string.wrapped_share_title)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                },
        ) {
            ArtworkBackdrop(
                artworkUri = backdropArtworkUri,
                foregroundArtworkHeight = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val availableHeight = maxHeight - 176.dp
            val canvasModifier = if (maxWidth * 16f / 9f <= availableHeight) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            } else {
                Modifier
                    .height(availableHeight)
                    .aspectRatio(9f / 16f)
            }
            Box(modifier = canvasModifier) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                    when (currentKind) {
                        SLIDE_INTRO -> WrappedIntroSlide(data)
                        SLIDE_ARTISTS -> WrappedArtistsSlide(data)
                        SLIDE_TRACKS -> WrappedTracksSlide(data)
                        SLIDE_ALBUMS -> WrappedAlbumsSlide(data)
                        else -> WrappedSummarySlide(data)
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = null,
                            ) {
                                if (slideIndex > 0) slideIndex -= 1
                            },
                    )
                    Spacer(modifier = Modifier.weight(2f))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = null,
                            ) {
                                if (slideIndex < slideKinds.lastIndex) {
                                    slideIndex += 1
                                }
                            },
                    )
                }
            }
        }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slideKinds.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            when {
                                index < slideIndex ->
                                    MaterialTheme.colorScheme.onSurface
                                index == slideIndex ->
                                    MaterialTheme.colorScheme.primary
                                else ->
                                    MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = 0.2f)
                            }
                        ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wrapped_title, data.recapPhrase),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.wrapped_slide_of,
                        slideIndex + 1,
                        slideKinds.size,
                        stringResource(wrappedSlideTitleRes(currentKind)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.wrapped_close),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val saved = saveCurrentCard(
                            context = context,
                            graphicsLayer = graphicsLayer,
                            tag = data.fileTag,
                            slide = slideIndex + 1,
                        )

                        if (saved) {
                            saveCompleted = true
                            delay(1800)
                            saveCompleted = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        if (saveCompleted) {
                            R.string.wrapped_saved
                        } else {
                            R.string.wrapped_save
                        },
                    ),
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        shareCurrentCard(
                            context = context,
                            graphicsLayer = graphicsLayer,
                            tag = data.fileTag,
                            slide = slideIndex + 1,
                            chooserTitle = shareTitle,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.wrapped_share))
            }
        }
        }
    }
}

private fun wrappedSlideTitleRes(kind: Int): Int = when (kind) {
    SLIDE_INTRO -> R.string.wrapped_slide_intro
    SLIDE_ARTISTS -> R.string.wrapped_slide_artists
    SLIDE_TRACKS -> R.string.wrapped_slide_tracks
    SLIDE_ALBUMS -> R.string.wrapped_slide_albums
    else -> R.string.wrapped_slide_summary
}

@Composable
private fun SlideHeader(
    kicker: String,
    recapPhrase: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.wrapped_recap_label, recapPhrase).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SlideFooter(text: String) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrappedIntroSlide(data: WrappedData) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        SlideHeader(
            kicker = stringResource(R.string.wrapped_slide_intro),
            recapPhrase = data.recapPhrase,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.wrapped_you_listened).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = numberFormat.format(data.totalMinutes),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.wrapped_minutes_of_music, data.periodPhrase),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    8.dp,
                    Alignment.CenterHorizontally,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                R.string.wrapped_plays,
                                numberFormat.format(data.totalPlays),
                            )
                        )
                    },
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                R.string.wrapped_stat_tracks_value,
                                numberFormat.format(data.uniqueTracks),
                            )
                        )
                    },
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                R.string.wrapped_stat_artists_value,
                                numberFormat.format(data.uniqueArtists),
                            )
                        )
                    },
                )
            }
        }
        SlideFooter(
            text = stringResource(
                R.string.wrapped_unique_artists,
                numberFormat.format(data.uniqueArtists),
                data.periodPhrase,
            )
        )
    }
}

private data class WrappedRankEntry(
    val artworkUri: String?,
    val fallback: String,
    val title: String,
    val subtitle: String?,
    val plays: Long,
)

@Composable
private fun WrappedRankedSlide(
    kicker: String,
    recapPhrase: String,
    footerText: String,
    moreLabel: String,
    heroArtworkUri: String?,
    heroFallback: String,
    heroTitle: String,
    heroSubtitle: String?,
    heroMinutes: Long,
    heroPlays: Long,
    entries: List<WrappedRankEntry>,
    circularArtwork: Boolean,
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        SlideHeader(kicker = kicker, recapPhrase = recapPhrase)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumArtwork(
                artworkUri = heroArtworkUri,
                fallbackText = heroFallback,
                contentDescription = heroTitle,
                modifier = Modifier.size(96.dp),
                size = 96.dp,
                shape = MaterialTheme.shapes.medium,
            )
            Text(
                text = heroTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (heroSubtitle != null) {
                Text(
                    text = heroSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.wrapped_minutes,
                    numberFormat.format(heroMinutes),
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(
                            R.string.wrapped_plays,
                            numberFormat.format(heroPlays),
                        )
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (entries.isNotEmpty()) {
            Text(
                text = moreLabel.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            HorizontalDivider()
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 2).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    AlbumArtwork(
                        artworkUri = entry.artworkUri,
                        fallbackText = entry.fallback,
                        contentDescription = entry.title,
                        modifier = Modifier.size(32.dp),
                        size = 32.dp,
                        shape = if (circularArtwork) {
                            CircleShape
                        } else {
                            MaterialTheme.shapes.small
                        },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.subtitle != null) {
                            Text(
                                text = entry.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.wrapped_plays,
                            numberFormat.format(entry.plays),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        SlideFooter(text = footerText)
    }
}

@Composable
private fun WrappedArtistsSlide(data: WrappedData) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val hero = data.topArtists.firstOrNull() ?: return
    WrappedRankedSlide(
        kicker = stringResource(R.string.wrapped_top_artist_kicker),
        recapPhrase = data.recapPhrase,
        footerText = stringResource(
            R.string.wrapped_unique_artists,
            numberFormat.format(data.uniqueArtists),
            data.periodPhrase,
        ),
        moreLabel = stringResource(R.string.wrapped_more_artists),
        heroArtworkUri = hero.artworkUri,
        heroFallback = hero.name,
        heroTitle = hero.name,
        heroSubtitle = null,
        heroMinutes = hero.minutes,
        heroPlays = hero.plays,
        entries = data.topArtists.drop(1).map { artist ->
            WrappedRankEntry(
                artworkUri = artist.artworkUri,
                fallback = artist.name,
                title = artist.name,
                subtitle = listOfNotNull(
                    artist.genre,
                    stringResource(
                        R.string.wrapped_minutes_short,
                        numberFormat.format(artist.minutes),
                    ),
                ).joinToString(" • "),
                plays = artist.plays,
            )
        },
        circularArtwork = true,
    )
}

@Composable
private fun WrappedTracksSlide(data: WrappedData) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val hero = data.topTracks.firstOrNull() ?: return
    WrappedRankedSlide(
        kicker = stringResource(R.string.wrapped_top_track_kicker),
        recapPhrase = data.recapPhrase,
        footerText = stringResource(
            R.string.wrapped_unique_tracks,
            numberFormat.format(data.uniqueTracks),
            data.periodPhrase,
        ),
        moreLabel = stringResource(R.string.wrapped_more_tracks),
        heroArtworkUri = hero.artworkUri,
        heroFallback = hero.title,
        heroTitle = hero.title,
        heroSubtitle = hero.artist,
        heroMinutes = hero.minutes,
        heroPlays = hero.plays,
        entries = data.topTracks.drop(1).map { track ->
            WrappedRankEntry(
                artworkUri = track.artworkUri,
                fallback = track.title,
                title = track.title,
                subtitle = track.artist,
                plays = track.plays,
            )
        },
        circularArtwork = false,
    )
}

@Composable
private fun WrappedAlbumsSlide(data: WrappedData) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val hero = data.topAlbums.firstOrNull() ?: return
    WrappedRankedSlide(
        kicker = stringResource(R.string.wrapped_top_album_kicker),
        recapPhrase = data.recapPhrase,
        footerText = stringResource(
            R.string.wrapped_unique_albums,
            numberFormat.format(data.uniqueAlbums),
            data.periodPhrase,
        ),
        moreLabel = stringResource(R.string.wrapped_more_albums),
        heroArtworkUri = hero.artworkUri,
        heroFallback = hero.title,
        heroTitle = hero.title,
        heroSubtitle = hero.artist,
        heroMinutes = hero.minutes,
        heroPlays = hero.plays,
        entries = data.topAlbums.drop(1).map { album ->
            WrappedRankEntry(
                artworkUri = album.artworkUri,
                fallback = album.title,
                title = album.title,
                subtitle = album.artist,
                plays = album.plays,
            )
        },
        circularArtwork = false,
    )
}

@Composable
private fun WrappedSummarySlide(data: WrappedData) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        SlideHeader(
            kicker = stringResource(R.string.wrapped_slide_summary),
            recapPhrase = data.recapPhrase,
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            WrappedStatRow(
                label = stringResource(R.string.wrapped_stat_minutes),
                value = stringResource(
                    R.string.wrapped_minutes,
                    numberFormat.format(data.totalMinutes),
                ),
            )
            WrappedStatRow(
                label = stringResource(R.string.wrapped_stat_plays),
                value = numberFormat.format(data.totalPlays),
            )
            WrappedStatRow(
                label = stringResource(R.string.wrapped_stat_tracks),
                value = numberFormat.format(data.uniqueTracks),
            )
            WrappedStatRow(
                label = stringResource(R.string.wrapped_stat_artists),
                value = numberFormat.format(data.uniqueArtists),
            )
            WrappedStatRow(
                label = stringResource(R.string.wrapped_stat_albums),
                value = numberFormat.format(data.uniqueAlbums),
                showDivider = false,
            )
        }

        val topArtist = data.topArtists.firstOrNull()
        val topTrack = data.topTracks.firstOrNull()
        val topAlbum = data.topAlbums.firstOrNull()
        if (topArtist != null || topTrack != null || topAlbum != null) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                if (topArtist != null) {
                    WrappedTopRow(
                        label = stringResource(R.string.wrapped_top_artist_label),
                        artworkUri = topArtist.artworkUri,
                        fallback = topArtist.name,
                        title = topArtist.name,
                    )
                }
                if (topTrack != null) {
                    WrappedTopRow(
                        label = stringResource(R.string.wrapped_top_track_label),
                        artworkUri = topTrack.artworkUri,
                        fallback = topTrack.title,
                        title = topTrack.title,
                    )
                }
                if (topAlbum != null) {
                    WrappedTopRow(
                        label = stringResource(R.string.wrapped_top_album_label),
                        artworkUri = topAlbum.artworkUri,
                        fallback = topAlbum.title,
                        title = topAlbum.title,
                        showDivider = false,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        SlideFooter(text = stringResource(R.string.wrapped_thanks))
    }
}

@Composable
private fun WrappedStatRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    if (showDivider) {
        HorizontalDivider()
    }
}

@Composable
private fun WrappedTopRow(
    label: String,
    artworkUri: String?,
    fallback: String,
    title: String,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtwork(
            artworkUri = artworkUri,
            fallbackText = fallback,
            contentDescription = title,
            modifier = Modifier.size(40.dp),
            size = 40.dp,
            shape = MaterialTheme.shapes.small,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showDivider) {
        HorizontalDivider()
    }
}

private suspend fun captureCardBitmap(graphicsLayer: GraphicsLayer): Bitmap {
    val image = graphicsLayer.toImageBitmap().asAndroidBitmap()
    val targetRatio = 9f / 16f
    val sourceRatio = image.width.toFloat() / image.height
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
        cropHeight = image.height
        cropWidth = (image.height * targetRatio).toInt()
    } else {
        cropWidth = image.width
        cropHeight = (image.width / targetRatio).toInt()
    }
    val cropped = Bitmap.createBitmap(
        image,
        (image.width - cropWidth) / 2,
        (image.height - cropHeight) / 2,
        cropWidth,
        cropHeight,
    )
    return Bitmap.createScaledBitmap(cropped, STORY_WIDTH_PX, STORY_HEIGHT_PX, true)
}

private suspend fun saveCurrentCard(
    context: Context,
    graphicsLayer: GraphicsLayer,
    tag: String,
    slide: Int,
): Boolean {
    val bitmap = captureCardBitmap(graphicsLayer)
    return withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "rovia-wrapped-$tag-$slide.png",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rovia")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return@withContext false
        val written = resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        written
    }
}

private suspend fun shareCurrentCard(
    context: Context,
    graphicsLayer: GraphicsLayer,
    tag: String,
    slide: Int,
    chooserTitle: String,
) {
    val bitmap = captureCardBitmap(graphicsLayer)
    val uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "wrapped").apply { mkdirs() }
        val file = File(directory, "rovia-wrapped-$tag-$slide.png")
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
