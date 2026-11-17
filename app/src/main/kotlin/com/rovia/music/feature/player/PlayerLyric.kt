package com.rovia.music.feature.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.rovia.music.R
import com.rovia.music.data.lyrics.EmbeddedLyricsReader
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.ArtworkBitmapLoader
import com.rovia.music.ui.components.LyricMenuButton
import com.rovia.music.ui.components.LyricOffsetBottomSheet
import com.rovia.music.ui.components.LyricShareBottomSheet
import com.rovia.music.ui.components.LyricShareRequest
import com.rovia.music.ui.components.LyricViewer
import com.rovia.music.ui.components.SyncedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerLyricScreen(
    title: String?,
    artist: String?,
    mediaItem: MediaItem?,
    positionMs: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val artworkUri = mediaItem
        ?.localConfiguration
        ?.uri
        ?.toString()

    val artworkBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = artworkUri,
        key2 = context,
    ) {
        value = ArtworkBitmapLoader.loadArtwork(
            context = context,
            artworkUri = artworkUri,
            maxDimension = 160,
        )
    }

    var lyrics by remember(mediaItem) {
        mutableStateOf<SyncedLyrics?>(null)
    }

    var isLoading by remember(mediaItem) {
        mutableStateOf(mediaItem != null)
    }

    val displayTitle = title
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_track)

    val displayArtist = artist
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.player_unknown_artist)

    var lyricOffsetMs by remember(mediaItem) {
        mutableIntStateOf(0)
    }

    var showOffsetSheet by remember {
        mutableStateOf(false)
    }

    val sharePicker = rememberLyricSharePickerState()

    var shareRequest by remember {
        mutableStateOf<LyricShareRequest?>(null)
    }

    LaunchedEffect(mediaItem) {
        sharePicker.cancel()

        if (mediaItem == null) {
            lyrics = null
            isLoading = false
        } else {
            isLoading = true
            lyrics = withContext(Dispatchers.IO) {
                EmbeddedLyricsReader.read(context, mediaItem)
            }
            isLoading = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        ArtworkBackdrop(
            artworkUri = artworkUri,
            foregroundArtworkHeight = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(start = 18.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = artworkBitmap
                        ?.takeUnless(Bitmap::isRecycled)

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = displayTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )

                    Text(
                        text = displayArtist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }

                if (!lyrics?.lines.isNullOrEmpty()) {
                    LyricMenuButton(
                        onShareClick = { sharePicker.beginPicking() },
                        onOffsetClick = { showOffsetSheet = true },
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                lyrics == null || lyrics?.lines.isNullOrEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.player_lyrics_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                }

                else -> {
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme.copy(
                            primary = Color.White,
                            onSurface = Color.White,
                            onSurfaceVariant = Color.White,
                        ),
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes,
                    ) {
                        LyricViewer(
                            lyrics = requireNotNull(lyrics),
                            positionMs = (positionMs - lyricOffsetMs)
                                .coerceAtLeast(0L),
                            onSeek = { lineStartMs ->
                                onSeek(lineStartMs + lyricOffsetMs)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 18.dp),
                            isPlaying = isPlaying,
                            picking = sharePicker.picking,
                            pickedSourceIndices = sharePicker.picks,
                            onPickLine = { sourceIndex ->
                                sharePicker.pick(
                                    sourceIndex,
                                    lyrics?.lines.orEmpty(),
                                )
                            },
                            onToggleLine = { sourceIndex ->
                                sharePicker.toggle(
                                    sourceIndex,
                                    lyrics?.lines.orEmpty(),
                                )
                            },
                        )
                    }
                }
            }
        }

        if (sharePicker.picking) {
            LyricSharePickBar(
                state = sharePicker,
                onShareClick = {
                    sharePicker.buildRequest(
                        title = displayTitle,
                        artist = displayArtist,
                        artworkUri = artworkUri,
                        lines = lyrics?.lines.orEmpty(),
                    )?.let { request ->
                        shareRequest = request
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
            )
        }

        if (showOffsetSheet) {
            LyricOffsetBottomSheet(
                offsetMs = lyricOffsetMs,
                onOffsetChange = { lyricOffsetMs = it },
                onDismissRequest = { showOffsetSheet = false },
            )
        }

        shareRequest?.let { request ->
            LyricShareBottomSheet(
                request = request,
                onDismissRequest = { shareRequest = null },
            )
        }
    }
}