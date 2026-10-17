
package com.rovia.music.core.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AlbumArtwork(
    artworkUri: String?,
    fallbackText: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val sizePx =
        with(density) {
            size.roundToPx()
        }.coerceAtLeast(1)

    /*
     * The shared loader handles both Android thumbnails and fallback
     * decoding. Its bounded LRU cache avoids repeating expensive
     * artwork reads and bitmap decoding when scrolling.
     *
     * Artwork loading and decoding run on Dispatchers.IO inside
     * ArtworkBitmapLoader rather than on the main thread.
     */
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        artworkUri,
        sizePx,
        context,
    ) {
        value = ArtworkBitmapLoader.loadArtwork(
            context = context,
            artworkUri = artworkUri,
            maxDimension = sizePx,
        )
    }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(shape)
                .background(
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHighest,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val loadedBitmap = bitmap

        if (loadedBitmap != null && !loadedBitmap.isRecycled) {
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text =
                    fallbackText
                        .trim()
                        .take(1)
                        .uppercase()
                        .ifBlank {
                            "R"
                        },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
