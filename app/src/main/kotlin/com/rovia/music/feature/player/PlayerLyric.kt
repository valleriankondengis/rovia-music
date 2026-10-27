package com.rovia.music.feature.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.rovia.music.R
import com.rovia.music.data.lyrics.EmbeddedLyricsReader
import com.rovia.music.ui.components.ArtworkBackdrop
import com.rovia.music.ui.components.ArtworkBitmapLoader
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

    LaunchedEffect(mediaItem) {
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
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            } else {
                                Text(
                                    text = "♫",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = title
                                    ?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.player_unknown_track),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Text(
                                text = artist
                                    ?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.player_unknown_artist),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
                            contentDescription = stringResource(R.string.player_back),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
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
                            positionMs = positionMs,
                            onSeek = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 18.dp),
                            isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }
}